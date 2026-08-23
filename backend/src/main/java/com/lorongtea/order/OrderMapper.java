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

    @Select(ORDER_COLUMNS + "WHERE customer_id = #{customerId} AND id = #{orderId}")
    OrderRow findForCustomer(@Param("customerId") long customerId, @Param("orderId") long orderId);

    @Select(ORDER_COLUMNS + "WHERE store_id = #{storeId} AND id = #{orderId}")
    OrderRow findForStore(@Param("storeId") long storeId, @Param("orderId") long orderId);

    @Select(ORDER_COLUMNS + """
            WHERE customer_id = #{customerId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<OrderRow> listForCustomer(@Param("customerId") long customerId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>" + ORDER_COLUMNS + """
            WHERE store_id = #{storeId}
            <if test="status != null">
                AND status = #{status}
            </if>
            <choose>
                <when test="oldestFirst">ORDER BY created_at ASC, id ASC</when>
                <otherwise>ORDER BY created_at DESC, id DESC</otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OrderRow> listForStore(@Param("storeId") long storeId, @Param("status") OrderStatus status,
            @Param("oldestFirst") boolean oldestFirst, @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE orders SET status = 'PREPARING', updated_at = UTC_TIMESTAMP(6)
            WHERE store_id = #{storeId} AND id = #{orderId} AND status = 'CREATED' AND paid_at IS NULL
            """)
    int start(@Param("storeId") long storeId, @Param("orderId") long orderId);

    @Update("""
            UPDATE orders SET status = 'CANCELLED', updated_at = UTC_TIMESTAMP(6)
            WHERE customer_id = #{customerId} AND id = #{orderId} AND status = 'CREATED' AND paid_at IS NULL
            """)
    int cancel(@Param("customerId") long customerId, @Param("orderId") long orderId);

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
