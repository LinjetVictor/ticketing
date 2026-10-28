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
