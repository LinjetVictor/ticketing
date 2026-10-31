package com.example.ticketing.service;

import com.example.ticketing.config.TicketingProperties;
import com.example.ticketing.domain.*;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.NotFoundException;
import com.example.ticketing.exception.SeatUnavailableException;
import com.example.ticketing.redis.SeatHoldStore;
import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.repository.SeatHoldRepository;
import com.example.ticketing.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Temporary seat holds.
 *
 * createHold() is deliberately NOT @Transactional: the Redis calls must not run while a DB connection is
 * held open, and every repository call below is its own short transaction.
 *
 * Race-free ordering:  1) cheap DB check  ->  2) atomic Redis acquire (the gate)  ->  3) DB re-check  ->  4) persist.
 * Step 3 closes the window where a seat was booked + its hold released between steps 1 and 2.
 */
@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldRepository holdRepository;
    private final SeatHoldStore store;
    private final TicketingProperties props;
    private final Clock clock;

    public HoldService(EventRepository eventRepository, SeatRepository seatRepository,
                       SeatHoldRepository holdRepository, SeatHoldStore store,
                       TicketingProperties props, Clock clock) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
        this.store = store;
        this.props = props;
        this.clock = clock;
    }

    public SeatHold createHold(long eventId, Collection<Long> requestedSeatIds) {
        Set<Long> seatIds = new TreeSet<>(requestedSeatIds == null ? List.of() : requestedSeatIds);
        if (seatIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one seat.");
        }
        if (seatIds.size() > props.hold().maxSeats()) {
            throw new BusinessRuleException("You can hold at most " + props.hold().maxSeats() + " seats at a time.");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isOnSale(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Tickets for this event are not on sale.");
        }

        assertSeatsBookable(eventId, seatIds);                                   // 1

        String holdId = UUID.randomUUID().toString();
        Duration ttl = props.hold().ttl();
        if (!store.tryAcquire(eventId, seatIds, holdId, ttl)) {                  // 2
            throw new SeatUnavailableException("Some of those seats were just taken. Pick different seats.");
        }
        try {
            assertSeatsBookable(eventId, seatIds);                               // 3
            LocalDateTime now = LocalDateTime.now(clock);
            return holdRepository.save(new SeatHold(holdId, eventId, seatIds, now, now.plus(ttl)));   // 4
        } catch (RuntimeException e) {
            safeRelease(eventId, seatIds, holdId);                               // compensate
            throw e;
        }
    }

    @Transactional
    public void release(String holdId) {
        SeatHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new NotFoundException("Hold not found"));
        if (hold.getStatus() == HoldStatus.ACTIVE) {
            hold.setStatus(HoldStatus.RELEASED);
            store.release(hold.getEventId(), hold.getSeatIds(), hold.getId());
        }
    }

    @Transactional(readOnly = true)
    public HoldDetails describe(String holdId) {
        SeatHold hold = holdRepository.findById(holdId).orElseThrow(() -> new NotFoundException("Hold not found"));
        Event event = eventRepository.findById(hold.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found"));
        List<Seat> seats = new ArrayList<>(seatRepository.findAllById(hold.getSeatIds()));
        seats.sort(Comparator.comparing(Seat::getSection).thenComparing(Seat::getRowLabel)
                .thenComparingInt(Seat::getSeatNumber));
        LocalDateTime now = LocalDateTime.now(clock);
        boolean live = hold.getStatus() == HoldStatus.ACTIVE
                && hold.getExpiresAt().isAfter(now)
                && store.ownsAll(hold.getEventId(), hold.getSeatIds(), hold.getId());
        long remaining = live ? Math.max(0, Duration.between(now, hold.getExpiresAt()).toSeconds()) : 0;
        long total = seats.stream().mapToLong(Seat::getPriceCents).sum();
        return new HoldDetails(hold.getId(), hold.getStatus(), event, seats, total, remaining, live);
    }

    /**
     * Scheduled-job body: marks ACTIVE holds past expiry (+grace) as EXPIRED. Redis already freed the seats via
     * TTL; this keeps the DB state honest and defensively deletes any keys that somehow still belong to the hold.
     * Uses SKIP LOCKED so it never blocks on (or fights with) a confirm in flight.
     */
    @Transactional
    public int expireStaleHolds() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(props.hold().expiryGrace());
        List<SeatHold> stale = holdRepository.lockExpired(HoldStatus.ACTIVE, cutoff, Pageable.ofSize(200));
        for (SeatHold h : stale) {
            h.setStatus(HoldStatus.EXPIRED);
            safeRelease(h.getEventId(), h.getSeatIds(), h.getId());
        }
        return stale.size();
    }

    private void assertSeatsBookable(long eventId, Set<Long> seatIds) {
        List<Seat> seats = seatRepository.findByIdInAndEventId(seatIds, eventId);
        if (seats.size() != seatIds.size()) {
            throw new BusinessRuleException("One or more selected seats do not exist for this event.");
        }
        for (Seat s : seats) {
            if (s.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException("Seat " + s.getLabel() + " is already booked.");
            }
        }
    }

    private void safeRelease(long eventId, Collection<Long> seatIds, String holdId) {
        try {
            store.release(eventId, seatIds, holdId);
        } catch (RuntimeException e) {
            log.warn("Could not release hold {} in Redis (TTL will clean it up): {}", holdId, e.toString());
        }
    }
}
