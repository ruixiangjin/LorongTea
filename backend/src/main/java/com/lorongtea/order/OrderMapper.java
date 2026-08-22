package com.lorongtea.order;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface OrderMapper {
    String ORDER_COLUMNS = """
            SELECT id, store_id, customer_id, source, status, total_cents, request_hash,
                   pickup_token, paid_at, paid_by, created_at, updated_at
            FROM orders
            """;

    @Select(ORDER_COLUMNS + "WHERE customer_id = #{customerId} AND idempotency_key = #{key}")
    OrderRow findByCustomerAndKey(@Param("customerId") long customerId, @Param("key") String key);

    @Insert("""
            INSERT INTO orders (customer_id, store_id, idempotency_key, request_hash, total_cents)
            VALUES (#{customerId}, #{storeId}, #{key}, #{requestHash}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertDraft(OrderDraft draft);

    @Update("UPDATE orders SET total_cents = #{totalCents} WHERE id = #{orderId}")
    void setTotal(@Param("orderId") long orderId, @Param("totalCents") long totalCents);

    @Insert("""
            INSERT INTO order_item (order_id, product_id, product_name, unit_price_cents, quantity)
            VALUES (#{orderId}, #{item.productId}, #{item.productName}, #{item.unitPriceCents}, #{item.quantity})
            """)
    void insertItem(@Param("orderId") long orderId, @Param("item") OrderItem item);

    @Select("""
            SELECT product_id, product_name, unit_price_cents, quantity
            FROM order_item
            WHERE order_id = #{orderId}
            ORDER BY product_id
            """)
    List<OrderItem> findItems(@Param("orderId") long orderId);
}
