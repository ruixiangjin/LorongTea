CREATE TABLE stock_adjustment (
    store_id BIGINT NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    available_qty_after INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (store_id, operation_id),
    CONSTRAINT fk_stock_adjustment_menu FOREIGN KEY (store_id, product_id)
        REFERENCES store_menu_item (store_id, product_id),
    CONSTRAINT fk_stock_adjustment_operator FOREIGN KEY (operator_id) REFERENCES app_user (id),
    CONSTRAINT ck_stock_adjustment_delta CHECK (delta <> 0),
    CONSTRAINT ck_stock_adjustment_quantity CHECK (available_qty_after >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
