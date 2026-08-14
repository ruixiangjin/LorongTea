CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku_code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_sku UNIQUE (sku_code)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE store_menu_item (
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    price_cents BIGINT NOT NULL,
    listed BOOLEAN NOT NULL DEFAULT TRUE,
    available_qty INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (store_id, product_id),
    CONSTRAINT fk_menu_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_menu_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_menu_price CHECK (price_cents >= 0),
    CONSTRAINT ck_menu_quantity CHECK (available_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
