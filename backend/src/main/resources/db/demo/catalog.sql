INSERT INTO store (code, name)
VALUES ('bukit-bintang', '武吉免登店'), ('petaling-jaya', '八打灵再也店')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO product (sku_code, name)
VALUES ('roasted-oolong', '炭焙乌龙鲜奶'),
       ('brown-sugar-boba', '黑糖珍珠奶茶'),
       ('pandan-coconut', '斑斓椰奶茶')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO store_menu_item (store_id, product_id, price_cents, listed, available_qty)
SELECT s.id, p.id, demo.price_cents, demo.listed, demo.available_qty
FROM (
    SELECT 'bukit-bintang' AS store_code, 'roasted-oolong' AS sku_code,
           1090 AS price_cents, TRUE AS listed, 120 AS available_qty
    UNION ALL SELECT 'bukit-bintang', 'brown-sugar-boba', 990, TRUE, 80
    UNION ALL SELECT 'bukit-bintang', 'pandan-coconut', 1190, TRUE, 65
    UNION ALL SELECT 'petaling-jaya', 'roasted-oolong', 1150, TRUE, 95
    UNION ALL SELECT 'petaling-jaya', 'brown-sugar-boba', 1050, FALSE, 50
    UNION ALL SELECT 'petaling-jaya', 'pandan-coconut', 1090, TRUE, 75
) AS demo
JOIN store s ON s.code = demo.store_code
JOIN product p ON p.sku_code = demo.sku_code
ON DUPLICATE KEY UPDATE available_qty = store_menu_item.available_qty;
