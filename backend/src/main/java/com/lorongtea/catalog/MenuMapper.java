package com.lorongtea.catalog;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MenuMapper {
    String STORE_MENU = """
            SELECT p.id AS product_id, p.sku_code, p.name, m.price_cents, m.listed, m.available_qty
            FROM store_menu_item m
            JOIN product p ON p.id = m.product_id
            WHERE m.store_id = #{storeId}
            """;

    @Select(STORE_MENU + "ORDER BY p.id")
    List<MenuRow> findByStoreId(@Param("storeId") long storeId);

    @Select(STORE_MENU + "AND m.product_id = #{productId}")
    MenuRow findItem(@Param("storeId") long storeId, @Param("productId") long productId);

    @Update("""
            UPDATE store_menu_item SET listed = #{listed}
            WHERE store_id = #{storeId} AND product_id = #{productId}
            """)
    void updateListing(@Param("storeId") long storeId, @Param("productId") long productId,
            @Param("listed") boolean listed);

    record MenuRow(long productId, String skuCode, String name, long priceCents, boolean listed, int availableQty) {
    }
}
