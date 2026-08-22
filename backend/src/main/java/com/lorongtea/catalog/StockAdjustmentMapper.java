package com.lorongtea.catalog;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface StockAdjustmentMapper {
    @Select("""
            SELECT product_id, operator_id, delta, reason, available_qty_after
            FROM stock_adjustment
            WHERE store_id = #{storeId} AND operation_id = #{operationId}
            """)
    Adjustment findAdjustment(@Param("storeId") long storeId, @Param("operationId") String operationId);

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
