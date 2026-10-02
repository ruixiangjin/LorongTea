SELECT s.code AS store, cs.quota_total - cs.quota_remaining AS quota_used,
       320 - m.available_qty AS stock_used, COUNT(pc.claim_id) AS claims, COUNT(o.id) AS orders
FROM campaign_store cs
JOIN campaign c ON c.id = cs.campaign_id
JOIN store s ON s.id = cs.store_id
JOIN store_menu_item m ON m.store_id = cs.store_id AND m.product_id = cs.product_id
LEFT JOIN promotion_claim pc ON pc.campaign_id = cs.campaign_id AND pc.store_id = cs.store_id
LEFT JOIN orders o ON o.promotion_claim_id = pc.claim_id
WHERE c.code = 'bench-sync'
GROUP BY s.code, cs.quota_total, cs.quota_remaining, m.available_qty
ORDER BY s.code;
