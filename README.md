# Box Office: event ticketing with expiring seat holds

Spring Boot 3.3 / Java 21, Spring Data JPA + MySQL 8, Redis 7, Thymeleaf, Flyway. No Lombok.

## Run it

```bash
docker compose up -d                 # MySQL + Redis
mvn spring-boot:run                  # http://localhost:8080  (two demo events are seeded)
mvn verify                           # integration tests (Testcontainers; needs Docker)
```

Open the same event in two browsers, pick the same seat in both, and press "Hold seats": only one wins.
The other browser's seat map turns amber within 5 seconds. Let the countdown run out and the seats free themselves.

## How a booking flows

```
 Browser                     HoldService                 Redis                     MySQL
   | select seats --------->  1. validate + DB check ---------------------------->  seat.status = AVAILABLE?
   |                          2. tryAcquire (Lua, all-or-nothing, PX ttl) ------->  SET NX-style keys
   |                          3. DB re-check ------------------------------------>  (closes the book-then-release race)
   |                          4. save SeatHold ----------------------------------->  seat_hold (ACTIVE)
   | confirm (name, email)   BookingService.confirm  @Transactional
   |                          a. SELECT hold FOR UPDATE ------------------------->  serializes double-clicks
   |                          b. ownsAll (Lua) ---------------------------------->  hold alive and still mine?
   |                          c. SELECT seats FOR UPDATE ORDER BY id ------------->  pessimistic, deadlock-free order
   |                          d. seat.status=BOOKED (@Version), insert booking ---> UNIQUE(active_seat_id)
   |                          e. afterCommit: release Redis keys
```
