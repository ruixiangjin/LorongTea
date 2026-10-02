START TRANSACTION;

INSERT INTO product (sku_code, name) VALUES ('bench-sync-tea', 'Benchmark Tea');
INSERT INTO store_menu_item (store_id, product_id, price_cents, listed, available_qty)
SELECT s.id, p.id, 1090, TRUE, 320
FROM store s CROSS JOIN product p
WHERE s.code IN ('bukit-bintang', 'petaling-jaya') AND p.sku_code = 'bench-sync-tea';

INSERT INTO campaign (code, starts_at, ends_at)
VALUES ('bench-sync', UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE, UTC_TIMESTAMP(6) + INTERVAL 1 HOUR);

INSERT INTO campaign_store (campaign_id, store_id, product_id, price_cents, quota_total, quota_remaining)
SELECT c.id, m.store_id, m.product_id, 690, 320, 320
FROM campaign c
CROSS JOIN product p
JOIN store_menu_item m ON m.product_id = p.id
WHERE c.code = 'bench-sync' AND p.sku_code = 'bench-sync-tea';

INSERT INTO app_user (username, password_hash, role)
WITH RECURSIVE numbers (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM numbers WHERE n < 320
)
SELECT CONCAT('bench-sync-', n), demo.password_hash, 'CUSTOMER'
FROM numbers CROSS JOIN app_user demo
WHERE demo.username = 'demo-customer';

COMMIT;

SELECT id AS campaign_id FROM campaign WHERE code = 'bench-sync';
