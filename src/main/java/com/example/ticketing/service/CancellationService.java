package com.example.ticketing.service;

import com.example.ticketing.domain.*;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.NotFoundException;
import com.example.ticketing.repository.BookingRepository;
import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancellation uses OPTIMISTIC locking (contrast with BookingService's pessimistic path): conflicts are rare and
 * cheap to retry, so we read without locks and let @Version detect a concurrent change at flush time, then retry
 * the whole transaction. A programmatic TransactionTemplate makes each attempt a fresh transaction.
 */
@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate tx;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public CancellationService(PlatformTransactionManager txManager, BookingRepository bookingRepository,
                               SeatRepository seatRepository, EventRepository eventRepository, Clock clock) {
        this.tx = new TransactionTemplate(txManager);
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    public Booking cancel(String reference) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> doCancel(reference));
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("Optimistic lock conflict cancelling {} (attempt {}/{}), retrying", reference, attempt, MAX_ATTEMPTS);
            }
        }
    }

    private Booking doCancel(String reference) {
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;                                   // idempotent
        }
        Event event = eventRepository.findById(booking.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("This event has already started; the booking can no longer be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        List<Long> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        for (Seat seat : seatRepository.findAllById(seatIds)) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }
        booking.getSeats().forEach(BookingSeat::deactivate);  // frees UNIQUE(active_seat_id) for re-sale
        return bookingRepository.saveAndFlush(booking);       // version checks happen here
    }
}
