package com.lorongtea.order;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface CampaignOutboxMapper {
    @Insert("INSERT INTO outbox_event (event_id, claim_id) VALUES (#{eventId}, #{claimId})")
    void enqueue(@Param("eventId") String eventId, @Param("claimId") String claimId);

    @Select("""
            SELECT e.event_id, e.claim_id, e.attempts
            FROM outbox_event e JOIN promotion_claim pc ON pc.claim_id = e.claim_id
            WHERE e.status = 'PENDING' AND e.available_at <= UTC_TIMESTAMP(6)
              AND (pc.status = 'ORDER_CREATED' OR (pc.status = 'ACCEPTED' AND pc.expires_at > UTC_TIMESTAMP(6)))
            ORDER BY e.available_at, e.event_id LIMIT 12
            """)
    List<Event> dueEvents();

    @Update("""
            UPDATE outbox_event SET status = 'PUBLISHED', attempts = attempts + 1, last_error = NULL
            WHERE event_id = #{eventId} AND status = 'PENDING'
            """)
    int markPublished(String eventId);

    @Update("""
            UPDATE outbox_event
            SET attempts = attempts + 1, available_at = TIMESTAMPADD(SECOND, #{delay}, UTC_TIMESTAMP(6)),
                last_error = #{error}
            WHERE event_id = #{eventId} AND status = 'PENDING'
            """)
    void defer(@Param("eventId") String eventId, @Param("delay") int delay, @Param("error") String error);

    @Select("SELECT event_id FROM outbox_event WHERE claim_id = #{claimId}")
    String eventId(String claimId);

    record Event(String eventId, String claimId, int attempts) {
    }
}
