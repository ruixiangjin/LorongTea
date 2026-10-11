-- Local demo fixture opening stock is 120 at Bukit Bintang and 95 at Petaling Jaya.
WITH claimed AS (
    SELECT campaign_id, store_id,
           SUM(status = 'ACCEPTED') AS accepted,
           SUM(status = 'ORDER_CREATED') AS created,
           SUM(status = 'FAILED_RELEASED') AS released
    FROM promotion_claim GROUP BY campaign_id, store_id
), adjusted AS (
    SELECT store_id, product_id, SUM(delta) AS quantity
    FROM stock_adjustment GROUP BY store_id, product_id
), sold AS (
    SELECT o.store_id, i.product_id, SUM(i.quantity) AS quantity
    FROM orders o JOIN order_item i ON i.order_id = o.id
    WHERE o.status <> 'CANCELLED'
    GROUP BY o.store_id, i.product_id
), reserved AS (
    SELECT store_id, product_id, COUNT(*) AS quantity
    FROM promotion_claim WHERE status = 'ACCEPTED'
    GROUP BY store_id, product_id
), pending_outbox AS (
    SELECT pc.campaign_id, pc.store_id, COUNT(*) AS pending
    FROM promotion_claim pc JOIN outbox_event e ON e.claim_id = pc.claim_id
    WHERE e.status = 'PENDING' GROUP BY pc.campaign_id, pc.store_id
)
SELECT s.code AS store,
       COALESCE(claimed.accepted, 0) AS accepted,
       COALESCE(claimed.created, 0) AS created,
       COALESCE(claimed.released, 0) AS released,
       COALESCE(pending_outbox.pending, 0) AS pending_outbox,
       cs.quota_remaining AS quota_actual,
       cs.quota_total - COALESCE(claimed.accepted, 0) - COALESCE(claimed.created, 0) AS quota_expected,
       m.available_qty AS stock_actual,
       CASE s.code WHEN 'bukit-bintang' THEN 120 WHEN 'petaling-jaya' THEN 95 END
           + COALESCE(adjusted.quantity, 0) - COALESCE(sold.quantity, 0) - COALESCE(reserved.quantity, 0)
           AS stock_expected
FROM campaign c
JOIN campaign_store cs ON cs.campaign_id = c.id
JOIN store s ON s.id = cs.store_id
JOIN store_menu_item m ON m.store_id = cs.store_id AND m.product_id = cs.product_id
LEFT JOIN claimed ON claimed.campaign_id = c.id AND claimed.store_id = s.id
LEFT JOIN adjusted ON adjusted.store_id = s.id AND adjusted.product_id = cs.product_id
LEFT JOIN sold ON sold.store_id = s.id AND sold.product_id = cs.product_id
LEFT JOIN reserved ON reserved.store_id = s.id AND reserved.product_id = cs.product_id
LEFT JOIN pending_outbox ON pending_outbox.campaign_id = c.id AND pending_outbox.store_id = s.id
WHERE c.code = 'demo-oolong'
ORDER BY s.code;
