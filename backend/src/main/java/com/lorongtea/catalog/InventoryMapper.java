package com.lorongtea.catalog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper {
    @Select("""
            SELECT available_qty FROM store_menu_item
            WHERE store_id = #{storeId} AND product_id = #{productId}
            FOR UPDATE
            """)
    Integer lockItemQuantity(@Param("storeId") long storeId, @Param("productId") long productId);

    @Select("""
            SELECT (SELECT p.name FROM product p WHERE p.id = m.product_id) AS product_name,
                   m.price_cents, m.available_qty, m.listed
            FROM store_menu_item m
            WHERE m.store_id = #{storeId} AND m.product_id = #{productId}
            FOR UPDATE
            """)
    CheckoutItem lockCheckoutItem(@Param("storeId") long storeId, @Param("productId") long productId);

    @Update("""
            UPDATE store_menu_item SET available_qty = available_qty - #{quantity}
            WHERE store_id = #{storeId} AND product_id = #{productId}
              AND listed = TRUE AND available_qty >= #{quantity}
            """)
    int deductAvailable(@Param("storeId") long storeId, @Param("productId") long productId,
            @Param("quantity") int quantity);

    @Update("""
            UPDATE store_menu_item
            SET available_qty = available_qty + #{delta}
            WHERE store_id = #{storeId} AND product_id = #{productId}
              AND available_qty + #{delta} BETWEEN 0 AND 2147483647
            """)
    int adjustQuantity(@Param("storeId") long storeId, @Param("productId") long productId, @Param("delta") int delta);

    record CheckoutItem(String productName, long priceCents, int availableQty, boolean listed) {
    }
}
