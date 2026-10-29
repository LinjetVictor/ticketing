package com.example.ticketing.repository;

import com.example.ticketing.domain.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = "seats")
    Optional<Booking> findByReference(String reference);

    Optional<Booking> findByHoldId(String holdId);

    long countByEventId(Long eventId);
}
