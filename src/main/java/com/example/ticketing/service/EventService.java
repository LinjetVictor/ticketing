package com.example.ticketing.service;

import com.example.ticketing.domain.Event;
import com.example.ticketing.domain.EventStatus;
import com.example.ticketing.domain.Seat;
import com.example.ticketing.domain.SeatStatus;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.NotFoundException;
import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.repository.SeatRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Event get(long id) {
        return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
    }

    @Transactional(readOnly = true)
    public List<EventSummary> upcomingSummaries() {
        return summarize(eventRepository.findByStatusAndStartsAtAfterOrderByStartsAtAsc(
                EventStatus.ON_SALE, LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<EventSummary> allSummaries() {
        return summarize(eventRepository.findAllByOrderByStartsAtAsc());
    }

    private List<EventSummary> summarize(List<Event> events) {
        List<EventSummary> out = new ArrayList<>();
        for (Event e : events) {
            out.add(new EventSummary(e,
                    seatRepository.countByEventId(e.getId()),
                    seatRepository.countByEventIdAndStatus(e.getId(), SeatStatus.AVAILABLE)));
        }
        return out;
    }

    /** Creates the event and generates its seat grid (rows A, B, C... x seatsPerRow) in one transaction. */
    @Transactional
    public Event create(CreateEventCommand c) {
        if (c.rows() < 1 || c.rows() > 26) {
            throw new BusinessRuleException("Rows must be between 1 and 26");
        }
        if (c.seatsPerRow() < 1 || c.seatsPerRow() > 60) {
            throw new BusinessRuleException("Seats per row must be between 1 and 60");
        }
        if (c.priceCents() < 0) {
            throw new BusinessRuleException("Price cannot be negative");
        }
        String section = (c.section() == null || c.section().isBlank()) ? "Main" : c.section().trim();
        Event event = eventRepository.save(
                new Event(c.name(), c.venue(), c.description(), c.startsAt(), LocalDateTime.now(clock)));
        List<Seat> seats = new ArrayList<>(c.rows() * c.seatsPerRow());
        for (int r = 0; r < c.rows(); r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            for (int n = 1; n <= c.seatsPerRow(); n++) {
                seats.add(new Seat(event.getId(), section, rowLabel, n, c.priceCents()));
            }
        }
        seatRepository.saveAll(seats);
        return event;
    }

    /**
     * Optimistic locking in the open: the edit form carries the version it was loaded with. If somebody
     * saved in the meantime the versions differ and we fail instead of overwriting their change
     * ("lost update"). The same check is repeated by Hibernate (UPDATE ... WHERE version = ?) at flush.
     */
    @Transactional
    public Event update(long id, long expectedVersion, String name, String venue, String description,
                        LocalDateTime startsAt, EventStatus status) {
        Event event = get(id);
        if (!event.getVersion().equals(expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Event.class, id);
        }
        event.setName(name);
        event.setVenue(venue);
        event.setDescription(description);
        event.setStartsAt(startsAt);
        event.setStatus(status);
        return eventRepository.saveAndFlush(event);
    }

    @Transactional
    public int closeStartedEvents() {
        return eventRepository.closeStarted(LocalDateTime.now(clock), EventStatus.ON_SALE, EventStatus.CLOSED);
    }
}
