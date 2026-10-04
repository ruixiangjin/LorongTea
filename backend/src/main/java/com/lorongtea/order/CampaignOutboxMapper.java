package com.lorongtea.order;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface CampaignOutboxMapper {
    @Insert("INSERT INTO outbox_event (event_id, claim_id) VALUES (#{eventId}, #{claimId})")
    void enqueue(@Param("eventId") String eventId, @Param("claimId") String claimId);
}
