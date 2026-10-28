CREATE TABLE event (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(200)  NOT NULL,
  venue       VARCHAR(200)  NOT NULL,
  description VARCHAR(2000) NULL,
  starts_at   DATETIME(6)   NOT NULL,
  status      VARCHAR(20)   NOT NULL,
  created_at  DATETIME(6)   NOT NULL,
  version     BIGINT        NOT NULL DEFAULT 0,
  INDEX idx_event_status_start (status, starts_at)
) ENGINE=InnoDB;

CREATE TABLE seat (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id    BIGINT       NOT NULL,
  section     VARCHAR(50)  NOT NULL,
  row_label   VARCHAR(10)  NOT NULL,
  seat_number INT          NOT NULL,
  price_cents BIGINT       NOT NULL,
  status      VARCHAR(20)  NOT NULL,
  version     BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT fk_seat_event FOREIGN KEY (event_id) REFERENCES event (id),
  CONSTRAINT uq_seat_position UNIQUE (event_id, section, row_label, seat_number),
  INDEX idx_seat_event_status (event_id, status)
) ENGINE=InnoDB;

-- State record for a hold. The *enforcement* of a hold lives in Redis (TTL keys); this row lets us
-- confirm idempotently, serialize confirms (row lock), and report on holds.
CREATE TABLE seat_hold (
  id         VARCHAR(36)  PRIMARY KEY,
  event_id   BIGINT       NOT NULL,
  status     VARCHAR(20)  NOT NULL,
  expires_at DATETIME(6)  NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  version    BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT fk_hold_event FOREIGN KEY (event_id) REFERENCES event (id),
  INDEX idx_hold_status_expiry (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE seat_hold_seat (
  hold_id VARCHAR(36) NOT NULL,
  seat_id BIGINT      NOT NULL,
  PRIMARY KEY (hold_id, seat_id),
  CONSTRAINT fk_hs_hold FOREIGN KEY (hold_id) REFERENCES seat_hold (id) ON DELETE CASCADE,
  CONSTRAINT fk_hs_seat FOREIGN KEY (seat_id) REFERENCES seat (id)
) ENGINE=InnoDB;

CREATE TABLE booking (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  reference      VARCHAR(20)  NOT NULL,
  event_id       BIGINT       NOT NULL,
  hold_id        VARCHAR(36)  NOT NULL,
  customer_name  VARCHAR(200) NOT NULL,
  customer_email VARCHAR(320) NOT NULL,
  status         VARCHAR(20)  NOT NULL,
  total_cents    BIGINT       NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  version        BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT uq_booking_reference UNIQUE (reference),
  CONSTRAINT uq_booking_hold UNIQUE (hold_id),
  CONSTRAINT fk_booking_event FOREIGN KEY (event_id) REFERENCES event (id)
) ENGINE=InnoDB;

CREATE TABLE booking_seat (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id     BIGINT       NOT NULL,
  seat_id        BIGINT       NOT NULL,
  -- Equals seat_id while the booking is active, NULL once cancelled. The UNIQUE index makes the
  -- database itself refuse a second active booking of the same seat (NULLs never collide),
  -- even if every application-level safeguard were bypassed.
  active_seat_id BIGINT       NULL,
  section        VARCHAR(50)  NOT NULL,
  label          VARCHAR(20)  NOT NULL,
  price_cents    BIGINT       NOT NULL,
  CONSTRAINT uq_booking_seat_active UNIQUE (active_seat_id),
  CONSTRAINT fk_bs_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
  CONSTRAINT fk_bs_seat FOREIGN KEY (seat_id) REFERENCES seat (id)
) ENGINE=InnoDB;
