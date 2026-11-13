# Box Office: event ticketing with expiring seat holds

Spring Boot 3.3 / Java 21, Spring Data JPA + PostgreSQL 16, Redis 7, Thymeleaf, Flyway.

**Live demo:** https://ticketing-1-fi12.onrender.com/

## Run it

```
docker compose up -d                 # PostgreSQL + Redis
mvn spring-boot:run                  # http://localhost:8080  (two demo events are seeded)
mvn verify                           # integration tests (Testcontainers; needs Docker)
```

Open the same event in two browsers (or on the [live demo](https://ticketing-1-fi12.onrender.com/)), pick the same seat in both, and press "Hold seats": only one wins. The other browser's seat map turns amber within 5 seconds. Let the countdown run out and the seats free themselves.

### Configuration

The app reads its connection settings from environment variables, so the same build runs locally and on a host such as Render:

| Variable | Example | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ticketing` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `ticketing` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `ticketing` | Database password |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `localhost` / `6379` | Redis connection |

## How a booking flows

```
 Browser                     HoldService                 Redis                     PostgreSQL
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

## Where each requirement lives

| Concern                         | Mechanism                                                                                             | Code                                          |
| ------------------------------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| Temporary holds that expire     | One Redis key per held seat with a TTL; Redis enforces expiry, no job needed for correctness          | `redis/SeatHoldStore`                         |
| Atomic multi-seat hold          | Lua script: all seats or none, no interleaving                                                        | `SeatHoldStore.ACQUIRE`                       |
| No double-booking, layer 1      | Redis hold ownership checked at confirm                                                               | `BookingService.confirm` (b)                  |
| Layer 2, pessimistic locking    | `SELECT ... FOR UPDATE ORDER BY id` on seats; status re-checked under lock                            | `SeatRepository.lockAllForBooking`            |
| Layer 3, optimistic locking     | `@Version` on `Seat`, `Booking`, `SeatHold`, `Event`                                                  | domain entities                               |
| Layer 4, database guarantee     | `UNIQUE(active_seat_id)`, NULL after cancel so seats can be resold (PostgreSQL allows multiple NULLs in a unique index) | `V1__init.sql`, `BookingSeat` |
| Optimistic locking with retry   | Cancellation retries a fresh transaction on a version conflict                                        | `CancellationService`                         |
| Optimistic locking in the UI    | Edit form carries the version; stale save returns a 409 page                                          | `EventService.update`, `AdminEventController` |
| Idempotent confirm              | Hold row lock plus `UNIQUE(hold_id)` on booking                                                       | `BookingService`, `V1__init.sql`              |
| Transactions                    | Service-level `@Transactional`; Redis calls kept outside transactions; Redis release in `afterCommit` | services                                      |
| Scheduled jobs                  | Hold reaper (`FOR UPDATE SKIP LOCKED`) every 10s; close sales for started events every minute         | `job/ScheduledJobs`                           |
| One job runner across instances | Redis `SET NX PX` lock with token-checked unlock                                                      | `redis/DistributedJobLock`                    |
| Live seat map                   | `/api/events/{id}/seats` polled every 5s                                                              | `SeatStatusApi`, `seatmap.js`                 |

## Design decisions worth knowing

- **Redis is the gate, PostgreSQL is the truth.** A hold is only a Redis key. If Redis loses data (failover), the worst case is two people briefly holding one seat. Confirm then fails for one of them because of the ownership check, the seat lock check and the unique index. Nobody is oversold.
- **Fail closed.** If Redis is down, creating a hold returns a 503 page. The seat map still renders (without holds).
- **`createHold` is not `@Transactional`** on purpose, so no DB connection is held while talking to Redis.
- **Payment** is not implemented. When you add it, charge after the hold and before `confirm`, and never call a payment provider while holding the seat row locks.
- Times are `LocalDateTime` in `ticketing.zone`. Seat/event/hold ids in URLs: the hold id is a random UUID used as a capability token.

## Before production

- Put Spring Security (and CSRF protection) in front of the app; `/admin/**` is currently open, including on the live demo.
- Add a payment step, email confirmation (send from an outbox or `afterCommit`) and per-IP rate limiting on hold creation.
- For very large venues, replace per-seat `multiGet` with a per-event Redis set or bitmap.
- Redis Cluster is supported by design: keys use the `{eventId}` hash tag.
- `ddl-auto` is `none` because Flyway owns the schema. Try `validate` in your environment once the app runs.

## Layout

```
src/main/java/com/example/ticketing
  config/      properties, Clock, demo data
  domain/      JPA entities and enums
  repository/  Spring Data repositories (locking queries live here)
  redis/       SeatHoldStore (Lua), DistributedJobLock
  service/     HoldService, BookingService, CancellationService, EventService, SeatMapService
  job/         ScheduledJobs
  web/         Thymeleaf controllers, forms, REST seat-status endpoint, error handling
src/main/resources/templates, static, db/migration
src/test/.../SeatBookingConcurrencyIT.java
```
