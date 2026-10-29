package com.example.ticketing.repository;

import com.example.ticketing.domain.Seat;
import com.example.ticketing.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(Long eventId);

    List<Seat> findByIdInAndEventId(Collection<Long> ids, Long eventId);

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    /**
     * Pessimistic lock: SELECT ... FOR UPDATE. ORDER BY id gives every transaction the same lock
     * acquisition order, so two bookings that overlap on several seats cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.eventId = :eventId and s.id in :ids order by s.id")
    List<Seat> lockAllForBooking(@Param("eventId") Long eventId, @Param("ids") Collection<Long> ids);
}
