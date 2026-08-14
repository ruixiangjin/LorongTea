package com.lorongtea.catalog;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface MenuMapper {
    @Select("""
            SELECT p.id AS product_id, p.sku_code, p.name, m.price_cents, m.listed
            FROM store_menu_item m
            JOIN product p ON p.id = m.product_id
            WHERE m.store_id = #{storeId}
            ORDER BY p.id
            """)
    List<MenuRow> findByStoreId(@Param("storeId") long storeId);

    record MenuRow(long productId, String skuCode, String name, long priceCents, boolean listed) {
    }
}
