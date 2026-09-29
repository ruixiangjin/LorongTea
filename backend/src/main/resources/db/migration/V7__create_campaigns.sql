CREATE TABLE campaign (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campaign_code UNIQUE (code),
    CONSTRAINT ck_campaign_window CHECK (starts_at < ends_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE campaign_store (
    campaign_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    price_cents BIGINT NOT NULL,
    quota_total INT NOT NULL,
    quota_remaining INT NOT NULL,
    PRIMARY KEY (campaign_id, store_id),
    CONSTRAINT fk_campaign_store_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_campaign_store_menu FOREIGN KEY (store_id, product_id)
        REFERENCES store_menu_item (store_id, product_id),
    CONSTRAINT ck_campaign_store_price CHECK (price_cents >= 0),
    CONSTRAINT ck_campaign_store_quota CHECK (quota_remaining BETWEEN 0 AND quota_total)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE promotion_claim (
    claim_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    customer_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    price_cents BIGINT NOT NULL,
    status VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ORDER_CREATED',
    active_customer_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ORDER_CREATED' THEN customer_id ELSE NULL END
    ) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (claim_id),
    CONSTRAINT uk_claim_active_customer UNIQUE (campaign_id, store_id, active_customer_id),
    CONSTRAINT fk_claim_campaign_store FOREIGN KEY (campaign_id, store_id)
        REFERENCES campaign_store (campaign_id, store_id),
    CONSTRAINT fk_claim_customer FOREIGN KEY (customer_id) REFERENCES app_user (id),
    CONSTRAINT fk_claim_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_claim_price CHECK (price_cents >= 0),
    CONSTRAINT ck_claim_status CHECK (status = 'ORDER_CREATED')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_promotion_claim FOREIGN KEY (promotion_claim_id)
        REFERENCES promotion_claim (claim_id);
