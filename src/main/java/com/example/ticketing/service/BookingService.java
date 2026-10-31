package com.example.ticketing.service;

import com.example.ticketing.domain.*;
import com.example.ticketing.exception.HoldExpiredException;
import com.example.ticketing.exception.NotFoundException;
import com.example.ticketing.exception.SeatUnavailableException;
import com.example.ticketing.redis.SeatHoldStore;
import com.example.ticketing.repository.BookingRepository;
import com.example.ticketing.repository.SeatHoldRepository;
import com.example.ticketing.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SeatHoldRepository holdRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatHoldStore store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public BookingService(SeatHoldRepository holdRepository, SeatRepository seatRepository,
                          BookingRepository bookingRepository, SeatHoldStore store, Clock clock) {
        this.holdRepository = holdRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Turns a live hold into a booking. Layers of protection, outermost first:
     *  1. row lock on the hold      - concurrent/double-click confirms of one hold run one at a time; repeats are idempotent
     *  2. Redis ownership check     - the hold must still be alive and still ours
     *  3. pessimistic seat locks    - SELECT ... FOR UPDATE in id order; status re-checked under the lock
     *  4. @Version on Seat          - a stale write would still fail
     *  5. UNIQUE(active_seat_id)    - the database refuses a second active booking of a seat, full stop
     * Redis keys are released only after the transaction commits.
     */
    @Transactional
    public Booking confirm(String holdId, String customerName, String customerEmail) {
        SeatHold hold = holdRepository.findByIdForUpdate(holdId)                         // 1
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        Optional<Booking> existing = bookingRepository.findByHoldId(holdId);
        if (existing.isPresent()) {
            return existing.get();                                                       // idempotent
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (hold.getStatus() != HoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(now)
                || !store.ownsAll(hold.getEventId(), hold.getSeatIds(), holdId)) {        // 2
            throw new HoldExpiredException("Your hold expired. Please pick your seats again.");
        }

        List<Seat> seats = seatRepository.lockAllForBooking(hold.getEventId(), hold.getSeatIds());   // 3
        if (seats.size() != hold.getSeatIds().size()) {
            throw new NotFoundException("One or more held seats no longer exist.");
        }
        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException("Seat " + seat.getLabel() + " is no longer available.");
            }
        }

        Booking booking = new Booking(newReference(), hold.getEventId(), holdId,
                customerName.trim(), customerEmail.trim(), now);
        long total = 0;
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            booking.addSeat(new BookingSeat(seat.getId(), seat.getSection(), seat.getLabel(), seat.getPriceCents()));
            total += seat.getPriceCents();
        }
        booking.setTotalCents(total);
        hold.setStatus(HoldStatus.CONFIRMED);

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);                             // 4 + 5 surface here
        } catch (DataIntegrityViolationException e) {
            throw new SeatUnavailableException("A seat in your selection was booked by someone else.");
        }

        releaseHoldKeysAfterCommit(hold);
        return saved;
    }

    @Transactional(readOnly = true)
    public Booking getByReference(String reference) {
        return bookingRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private void releaseHoldKeysAfterCommit(SeatHold hold) {
        Long eventId = hold.getEventId();
        List<Long> seatIds = List.copyOf(hold.getSeatIds());
        String holdId = hold.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    store.release(eventId, seatIds, holdId);
                } catch (RuntimeException e) {
                    log.warn("Could not release Redis keys for hold {} (TTL will clean up): {}", holdId, e.toString());
                }
            }
        });
    }

    private String newReference() {
        StringBuilder sb = new StringBuilder("TKT-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
