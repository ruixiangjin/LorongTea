SELECT id, store_id, customer_id, source, status, total_cents, request_hash,
       pickup_token, paid_at, paid_by, created_at, updated_at
FROM orders
WHERE store_id = {store_id} AND status = 'CREATED'
ORDER BY created_at ASC, id ASC
LIMIT 20 OFFSET 0;
