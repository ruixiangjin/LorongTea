ALTER TABLE promotion_claim
    DROP CHECK ck_claim_status,
    DROP INDEX uk_claim_active_customer,
    ADD COLUMN product_name VARCHAR(100),
    ADD COLUMN expires_at DATETIME(6),
    ADD COLUMN failure_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin,
    MODIFY COLUMN active_customer_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('ACCEPTED', 'ORDER_CREATED') THEN customer_id ELSE NULL END
    ) STORED,
    ADD CONSTRAINT uk_claim_active_customer UNIQUE (campaign_id, store_id, active_customer_id),
    ADD CONSTRAINT ck_claim_status CHECK (
        (status = 'ACCEPTED' AND expires_at IS NOT NULL AND product_name IS NOT NULL AND failure_code IS NULL)
        OR (status = 'ORDER_CREATED' AND failure_code IS NULL)
        OR (status = 'FAILED_RELEASED' AND expires_at IS NOT NULL AND failure_code IS NOT NULL)
    ),
    ADD INDEX ix_claim_expiry (status, expires_at, claim_id);

CREATE TABLE outbox_event (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CREATE_ORDER',
    status VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(255),
    PRIMARY KEY (event_id),
    CONSTRAINT uk_outbox_claim UNIQUE (claim_id),
    CONSTRAINT fk_outbox_claim FOREIGN KEY (claim_id) REFERENCES promotion_claim (claim_id),
    CONSTRAINT ck_outbox_type CHECK (event_type = 'CREATE_ORDER'),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'CLOSED')),
    INDEX ix_outbox_available (status, available_at, event_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
