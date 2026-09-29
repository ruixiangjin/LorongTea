ALTER TABLE orders
    ADD INDEX ix_orders_store_status_created_id (store_id, status, created_at, id),
    DROP INDEX ix_orders_store_status;
