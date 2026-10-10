package com.lorongtea.order;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("""
            SELECT product_id, price_cents, quota_remaining
            FROM campaign_store WHERE campaign_id = #{campaignId} AND store_id = #{storeId}
            FOR UPDATE
            """)
    StoreOffer lockOffer(@Param("campaignId") long campaignId, @Param("storeId") long storeId);

    @Select("""
            SELECT CASE
                WHEN UTC_TIMESTAMP(6) < starts_at THEN 'UPCOMING'
                WHEN UTC_TIMESTAMP(6) >= ends_at THEN 'ENDED'
                ELSE 'OPEN'
            END FROM campaign WHERE id = #{campaignId}
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    String windowStatus(long campaignId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM promotion_claim
                WHERE campaign_id = #{campaignId} AND store_id = #{storeId}
                  AND active_customer_id = #{customerId}
            )
            """)
    boolean hasClaim(@Param("campaignId") long campaignId, @Param("storeId") long storeId,
            @Param("customerId") long customerId);

    @Update("""
            UPDATE campaign_store SET quota_remaining = quota_remaining - 1
            WHERE campaign_id = #{campaignId} AND store_id = #{storeId} AND quota_remaining > 0
            """)
    int deductQuota(@Param("campaignId") long campaignId, @Param("storeId") long storeId);

    @Insert("""
            INSERT INTO promotion_claim (claim_id, customer_id, campaign_id, store_id, product_id, price_cents)
            VALUES (#{claimId}, #{customerId}, #{campaignId}, #{storeId}, #{offer.productId}, #{offer.priceCents})
            """)
    void insertClaim(@Param("claimId") String claimId, @Param("customerId") long customerId,
            @Param("campaignId") long campaignId, @Param("storeId") long storeId, @Param("offer") StoreOffer offer);

    @Insert("""
            INSERT INTO promotion_claim (claim_id, customer_id, campaign_id, store_id, product_id, price_cents,
                                         product_name, status, expires_at)
            VALUES (#{claimId}, #{customerId}, #{campaignId}, #{storeId}, #{item.productId}, #{item.unitPriceCents},
                    #{item.productName}, 'ACCEPTED', TIMESTAMPADD(SECOND, #{deadline}, UTC_TIMESTAMP(6)))
            """)
    void reserveClaim(@Param("claimId") String claimId, @Param("customerId") long customerId,
            @Param("campaignId") long campaignId, @Param("storeId") long storeId,
            @Param("item") OrderItem item, @Param("deadline") int deadline);

    @Select("""
            SELECT pc.claim_id, pc.customer_id, pc.campaign_id, pc.store_id, pc.status, o.id AS order_id, pc.failure_code
            FROM promotion_claim pc LEFT JOIN orders o ON o.promotion_claim_id = pc.claim_id
            WHERE pc.claim_id = #{claimId}
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    ReceiptRow receipt(String claimId);

    @Select("""
            SELECT customer_id, campaign_id, store_id, product_id, price_cents, product_name, status
            FROM promotion_claim WHERE claim_id = #{claimId} FOR UPDATE
            """)
    ReservedClaim lockClaim(String claimId);

    @Select("SELECT expires_at <= UTC_TIMESTAMP(6) FROM promotion_claim WHERE claim_id = #{claimId}")
    boolean hasExpired(String claimId);

    @Update("UPDATE promotion_claim SET status = 'ORDER_CREATED' WHERE claim_id = #{claimId}")
    void markOrderCreated(String claimId);

    @Select("""
            SELECT claim_id, campaign_id, store_id FROM promotion_claim
            WHERE status = 'ACCEPTED' AND expires_at <= UTC_TIMESTAMP(6)
            ORDER BY expires_at, claim_id LIMIT 24
            """)
    List<ExpiredClaim> expiredClaims();

    @Select("SELECT COUNT(*) FROM promotion_claim WHERE status = 'ACCEPTED'")
    long countReserved();

    @Update("""
            UPDATE promotion_claim SET status = 'FAILED_RELEASED', failure_code = 'CAMPAIGN_RESERVATION_EXPIRED'
            WHERE claim_id = #{claimId} AND status = 'ACCEPTED' AND expires_at <= UTC_TIMESTAMP(6)
              AND NOT EXISTS (SELECT 1 FROM orders WHERE promotion_claim_id = #{claimId})
            """)
    int markReleased(String claimId);

    @Update("""
            UPDATE campaign_store SET quota_remaining = quota_remaining + 1
            WHERE campaign_id = #{campaignId} AND store_id = #{storeId} AND quota_remaining < quota_total
            """)
    int restoreQuota(@Param("campaignId") long campaignId, @Param("storeId") long storeId);

    record ExpiredClaim(String claimId, long campaignId, long storeId) {
    }

    record ReservedClaim(long customerId, long campaignId, long storeId, long productId,
                         long priceCents, String productName, String status) {
    }

    record StoreOffer(long productId, long priceCents, int quotaRemaining) {
    }

    record ReceiptRow(String claimId, long customerId, long campaignId, long storeId, String status, Long orderId,
                      String failureCode) {
    }

    record OfferRow(long campaignId, long storeId, String drinkName, long priceCents,
                    Instant startsAt, Instant endsAt, String availability) {
    }
}
