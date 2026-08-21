CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    source VARCHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CREATED',
    total_cents BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,
    promotion_claim_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    pickup_token VARCHAR(22) CHARACTER SET ascii COLLATE ascii_bin,
    paid_at DATETIME(6),
    paid_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_customer_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT uk_orders_promotion_claim UNIQUE (promotion_claim_id),
    CONSTRAINT uk_orders_pickup_token UNIQUE (pickup_token),
    CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES app_user (id),
    CONSTRAINT fk_orders_paid_by FOREIGN KEY (paid_by) REFERENCES app_user (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_orders_source CHECK (
        (source = 'NORMAL' AND idempotency_key IS NOT NULL AND request_hash IS NOT NULL AND promotion_claim_id IS NULL)
        OR (source = 'PROMOTION' AND idempotency_key IS NULL AND request_hash IS NULL AND promotion_claim_id IS NOT NULL)
    ),
    CONSTRAINT ck_orders_total CHECK (total_cents >= 0),
    CONSTRAINT ck_orders_payment CHECK (
        (paid_at IS NULL AND paid_by IS NULL AND status <> 'COMPLETED')
        OR (paid_at IS NOT NULL AND paid_by IS NOT NULL AND status IN ('READY', 'COMPLETED'))
    ),
    CONSTRAINT ck_orders_pickup CHECK (
        (pickup_token IS NOT NULL AND CHAR_LENGTH(pickup_token) = 22 AND status IN ('READY', 'COMPLETED'))
        OR (pickup_token IS NULL AND status IN ('CREATED', 'PREPARING', 'CANCELLED'))
    ),
    INDEX ix_orders_customer_history (customer_id, created_at, id),
    INDEX ix_orders_store_status (store_id, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE order_item (
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_order_item_price CHECK (unit_price_cents >= 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
