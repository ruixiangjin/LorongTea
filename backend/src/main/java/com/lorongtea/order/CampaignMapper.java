package com.lorongtea.order;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface CampaignMapper {
    @Select("""
            SELECT c.id AS campaign_id, cs.store_id, p.name AS drink_name, cs.price_cents,
                   c.starts_at, c.ends_at,
                   CASE
                       WHEN UTC_TIMESTAMP(6) < c.starts_at THEN 'UPCOMING'
                       WHEN UTC_TIMESTAMP(6) >= c.ends_at THEN 'ENDED'
                       WHEN EXISTS (
                           SELECT 1 FROM promotion_claim pc
                           WHERE pc.campaign_id = c.id AND pc.store_id = cs.store_id
                             AND pc.active_customer_id = #{customerId,jdbcType=BIGINT}
                       ) THEN 'LIMIT_REACHED'
                       WHEN cs.quota_remaining = 0 OR m.available_qty = 0 OR NOT m.listed THEN 'SOLD_OUT'
                       ELSE 'OPEN'
                   END AS availability
            FROM campaign_store cs
            JOIN campaign c ON c.id = cs.campaign_id
            JOIN product p ON p.id = cs.product_id
            JOIN store_menu_item m ON m.store_id = cs.store_id AND m.product_id = cs.product_id
            WHERE cs.store_id = #{storeId}
            ORDER BY (UTC_TIMESTAMP(6) >= c.ends_at), c.starts_at, c.id
            """)
    List<OfferRow> offers(@Param("storeId") long storeId, @Param("customerId") Long customerId);

    record OfferRow(long campaignId, long storeId, String drinkName, long priceCents,
                    Instant startsAt, Instant endsAt, String availability) {
    }
}
