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
