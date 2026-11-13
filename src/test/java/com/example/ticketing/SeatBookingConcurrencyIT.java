package com.example.ticketing;

import com.example.ticketing.domain.*;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.HoldExpiredException;
import com.example.ticketing.exception.SeatUnavailableException;
import com.example.ticketing.repository.BookingRepository;
import com.example.ticketing.repository.SeatRepository;
import com.example.ticketing.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against real MySQL and Redis (Testcontainers). Requires Docker. Run with: mvn verify
 */
@SpringBootTest(properties = {"ticketing.hold.ttl=3s", "ticketing.seed=false"})
@Testcontainers
class SeatBookingConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired EventService eventService;
    @Autowired HoldService holdService;
    @Autowired BookingService bookingService;
    @Autowired SeatRepository seatRepository;
    @Autowired BookingRepository bookingRepository;

    private Event newEvent(int rows, int seatsPerRow) {
        return eventService.create(new CreateEventCommand("Test event", "Test venue", "",
                LocalDateTime.now().plusDays(7), "Main", rows, seatsPerRow, 5000));
    }

    private List<Long> seatIds(Event event) {
        return seatRepository.findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(event.getId())
                .stream().map(Seat::getId).toList();
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                return task.call();
            }));
        }
        startGun.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }

    @Test
    void fiftyCustomersRaceForOneSeat_exactlyOneGetsTheHold() throws Exception {
        Event event = newEvent(1, 1);
        Long seatId = seatIds(event).get(0);

        List<String> holdIds = runConcurrently(50, () -> {
            try {
                return holdService.createHold(event.getId(), List.of(seatId)).getId();
            } catch (SeatUnavailableException e) {
                return null;
            }
        });

        List<String> winners = holdIds.stream().filter(Objects::nonNull).toList();
        assertThat(winners).hasSize(1);

        Booking booking = bookingService.confirm(winners.get(0), "Winner", "winner@example.com");
        assertThat(booking.getSeats()).hasSize(1);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(1);
    }

    @Test
    void overlappingMultiSeatRequests_neverProduceOverlappingBookings() throws Exception {
        Event event = newEvent(1, 10);
        List<Long> all = seatIds(event);

        List<List<Long>> bookedSeatGroups = runConcurrently(30, () -> {
            List<Long> pool = new ArrayList<>(all);
            Collections.shuffle(pool);
            List<Long> pick = pool.subList(0, 3);
            try {
                SeatHold hold = holdService.createHold(event.getId(), pick);
                bookingService.confirm(hold.getId(), "Buyer", "buyer@example.com");
                return new ArrayList<>(pick);
            } catch (SeatUnavailableException | BusinessRuleException | HoldExpiredException e) {
                return List.<Long>of();
            }
        });

        List<Long> allBooked = bookedSeatGroups.stream().flatMap(List::stream).toList();
        assertThat(allBooked).doesNotHaveDuplicates();                       // no seat sold twice
        long bookedInDb = seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.BOOKED);
        assertThat(bookedInDb).isEqualTo(allBooked.size());                  // DB agrees with the winners
    }

    @Test
    void doubleClickingConfirm_isIdempotent() throws Exception {
        Event event = newEvent(1, 2);
        SeatHold hold = holdService.createHold(event.getId(), seatIds(event));

        List<String> refs = runConcurrently(10,
                () -> bookingService.confirm(hold.getId(), "Pat", "pat@example.com").getReference());

        assertThat(new HashSet<>(refs)).hasSize(1);
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(1);
    }

    @Test
    void expiredHold_cannotBeConfirmed_andSeatsBecomeHoldableAgain() throws Exception {
        Event event = newEvent(1, 1);
        List<Long> seat = seatIds(event);
        SeatHold hold = holdService.createHold(event.getId(), seat);

        assertThatThrownBy(() -> holdService.createHold(event.getId(), seat))
                .isInstanceOf(SeatUnavailableException.class);

        Thread.sleep(3_500);   // hold TTL is 3s in this test

        assertThatThrownBy(() -> bookingService.confirm(hold.getId(), "Late", "late@example.com"))
                .isInstanceOf(HoldExpiredException.class);
        SeatHold second = holdService.createHold(event.getId(), seat);   // seat is free again
        assertThat(second.getId()).isNotEqualTo(hold.getId());
    }

    @Test
    void databaseConstraintIsTheLastLineOfDefence() {
        Event event = newEvent(1, 1);
        Seat seat = seatRepository.findAll().stream()
                .filter(s -> s.getEventId().equals(event.getId())).findFirst().orElseThrow();

        bookingRepository.saveAndFlush(manualBooking("TKT-AAAAAAAA", event, seat));
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(manualBooking("TKT-BBBBBBBB", event, seat)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Booking manualBooking(String reference, Event event, Seat seat) {
        Booking b = new Booking(reference, event.getId(), UUID.randomUUID().toString(), "X", "x@example.com",
                LocalDateTime.now());
        b.addSeat(new BookingSeat(seat.getId(), seat.getSection(), seat.getLabel(), seat.getPriceCents()));
        b.setTotalCents(seat.getPriceCents());
        return b;
    }
}
