package com.lorongtea.catalog;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface StockAdjustmentMapper {
    @Select("""
            SELECT available_qty FROM store_menu_item
            WHERE store_id = #{storeId} AND product_id = #{productId}
            FOR UPDATE
            """)
    Integer lockItemQuantity(@Param("storeId") long storeId, @Param("productId") long productId);

    @Select("""
            SELECT product_id, operator_id, delta, reason, available_qty_after
            FROM stock_adjustment
            WHERE store_id = #{storeId} AND operation_id = #{operationId}
            """)
    Adjustment findAdjustment(@Param("storeId") long storeId, @Param("operationId") String operationId);

    @Update("""
            UPDATE store_menu_item
            SET available_qty = available_qty + #{delta}
            WHERE store_id = #{storeId} AND product_id = #{productId}
              AND available_qty + #{delta} BETWEEN 0 AND 2147483647
            """)
    int adjustQuantity(@Param("storeId") long storeId, @Param("productId") long productId, @Param("delta") int delta);

    @Insert("""
            INSERT INTO stock_adjustment (store_id, operation_id, product_id, operator_id, delta, reason,
                                          available_qty_after)
            VALUES (#{storeId}, #{operationId}, #{adjustment.productId}, #{adjustment.operatorId},
                    #{adjustment.delta}, #{adjustment.reason}, #{adjustment.availableQtyAfter})
            """)
    void insertAdjustment(@Param("storeId") long storeId, @Param("operationId") String operationId,
            @Param("adjustment") Adjustment adjustment);

    record Adjustment(long productId, long operatorId, int delta, String reason, int availableQtyAfter) {
    }
}
