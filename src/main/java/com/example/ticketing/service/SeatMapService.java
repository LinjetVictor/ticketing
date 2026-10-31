package com.example.ticketing.service;

import com.example.ticketing.domain.DisplayStatus;
import com.example.ticketing.domain.Seat;
import com.example.ticketing.domain.SeatStatus;
import com.example.ticketing.redis.SeatHoldStore;
import com.example.ticketing.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Builds the seat map: DB is the truth for BOOKED, Redis is the truth for HELD.
 * If Redis is unreachable the read path degrades (holds are not shown) rather than failing; the write path
 * (creating a hold) still fails closed, so nothing can be oversold.
 */
@Service
public class SeatMapService {

    private static final Logger log = LoggerFactory.getLogger(SeatMapService.class);

    public record SeatView(Long id, int number, String label, long priceCents, DisplayStatus status) {
        public boolean available() { return status == DisplayStatus.AVAILABLE; }
    }

    public record RowView(String label, List<SeatView> seats) { }

    public record SectionView(String name, List<RowView> rows) { }

    public record SeatMap(List<SectionView> sections, int total, int available) { }

    private final SeatRepository seatRepository;
    private final SeatHoldStore holdStore;

    public SeatMapService(SeatRepository seatRepository, SeatHoldStore holdStore) {
        this.seatRepository = seatRepository;
        this.holdStore = holdStore;
    }

    @Transactional(readOnly = true)
    public SeatMap build(long eventId) {
        List<Seat> seats = seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(eventId);
        Map<Long, DisplayStatus> statuses = resolve(eventId, seats);

        Map<String, Map<String, List<SeatView>>> grouped = new LinkedHashMap<>();
        int available = 0;
        for (Seat s : seats) {
            DisplayStatus st = statuses.get(s.getId());
            if (st == DisplayStatus.AVAILABLE) {
                available++;
            }
            grouped.computeIfAbsent(s.getSection(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(s.getRowLabel(), k -> new ArrayList<>())
                    .add(new SeatView(s.getId(), s.getSeatNumber(), s.getLabel(), s.getPriceCents(), st));
        }
        List<SectionView> sections = new ArrayList<>();
        grouped.forEach((section, rows) -> {
            List<RowView> rowViews = new ArrayList<>();
            rows.forEach((row, rowSeats) -> rowViews.add(new RowView(row, rowSeats)));
            sections.add(new SectionView(section, rowViews));
        });
        return new SeatMap(sections, seats.size(), available);
    }

    /** seatId -> AVAILABLE | HELD | BOOKED, for the live-refresh endpoint. */
    @Transactional(readOnly = true)
    public Map<Long, String> statuses(long eventId) {
        List<Seat> seats = seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(eventId);
        Map<Long, String> out = new LinkedHashMap<>();
        resolve(eventId, seats).forEach((id, st) -> out.put(id, st.name()));
        return out;
    }

    private Map<Long, DisplayStatus> resolve(long eventId, List<Seat> seats) {
        List<Long> candidateIds = new ArrayList<>();
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.AVAILABLE) {
                candidateIds.add(s.getId());
            }
        }
        Set<Long> held = Set.of();
        try {
            held = holdStore.heldSeatIds(eventId, candidateIds);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable while reading holds for event {}: {}", eventId, e.toString());
        }
        Map<Long, DisplayStatus> out = new LinkedHashMap<>();
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                out.put(s.getId(), DisplayStatus.BOOKED);
            } else if (held.contains(s.getId())) {
                out.put(s.getId(), DisplayStatus.HELD);
            } else {
                out.put(s.getId(), DisplayStatus.AVAILABLE);
            }
        }
        return out;
    }
}
