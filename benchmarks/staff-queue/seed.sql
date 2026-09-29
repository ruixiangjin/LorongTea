INSERT INTO store (id, code, name) VALUES (1, 'bench-bb', 'Bukit Bintang'), (2, 'bench-pj', 'Petaling Jaya');
INSERT INTO app_user (id, username, password_hash, role, store_id)
VALUES (1, 'bench-staff', 'unused', 'STAFF', 1), (2, 'bench-staff-pj', 'unused', 'STAFF', 2);

SET @seed = 'lorong-tea-staff-queue-v1';
SET SESSION cte_max_recursion_depth = 100001;
INSERT INTO app_user (id, username, password_hash, role)
WITH RECURSIVE seq (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT n + 2, CONCAT('bench-customer-', n), 'unused', 'CUSTOMER' FROM seq;

INSERT INTO orders (id, store_id, customer_id, status, total_cents, idempotency_key, request_hash,
                    pickup_token, paid_at, paid_by, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100000
), distribution AS (
    SELECT n, IF(CRC32(CONCAT(@seed, ':store:', n)) % 100 < 30, 2, 1) AS store_id,
           CRC32(CONCAT(@seed, ':status:', n)) % 100 AS status_draw,
           TIMESTAMP('2026-08-15 00:00:00') + INTERVAL FLOOR(n / 20) MINUTE AS created_at
    FROM seq
), fixture_rows AS (
    SELECT n, store_id, created_at,
           CASE
               WHEN status_draw < IF(store_id = 1, 5, 35) THEN 'CREATED'
               WHEN status_draw < 45 THEN 'PREPARING'
               WHEN status_draw < 55 THEN 'READY'
               WHEN status_draw < 95 THEN 'COMPLETED'
               ELSE 'CANCELLED'
           END AS status
    FROM distribution
)
SELECT n, store_id, 3 + CRC32(CONCAT(@seed, ':customer:', n)) % 1000, status,
       850 + 50 * (n % 16), CONCAT('bench-order-', n), SHA2(CONCAT(@seed, ':order:', n), 256),
       IF(status IN ('READY', 'COMPLETED'), LPAD(n, 22, '0'), NULL),
       IF(status = 'COMPLETED', created_at, NULL), IF(status = 'COMPLETED', store_id, NULL),
       created_at, created_at
FROM fixture_rows ORDER BY n;

ANALYZE TABLE orders;
