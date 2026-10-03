package com.lorongtea.order;

import java.util.List;

import com.lorongtea.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class CampaignAdmission {
    private static final RedisScript<Long> BUCKET =
            RedisScript.of(new ClassPathResource("redis/campaign-token-bucket.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final String rate;
    private final String burst;

    CampaignAdmission(StringRedisTemplate redis, @Value("${lorong.campaign.rate-limit.enabled}") boolean enabled,
            @Value("${lorong.campaign.rate-limit.permits-per-second}") int rate,
            @Value("${lorong.campaign.rate-limit.burst}") int burst) {
        if (rate <= 0 || burst <= 0) {
            throw new IllegalArgumentException("Campaign token rate and burst must be positive");
        }
        this.redis = redis;
        this.enabled = enabled;
        this.rate = Integer.toString(rate);
        this.burst = Integer.toString(burst);
    }

    void requirePermit(long storeId, long campaignId) {
        if (!enabled) {
            return;
        }
        String key = "lt:rl:{" + campaignId + ":" + storeId + "}";
        Long waitMillis;
        try {
            waitMillis = redis.execute(BUCKET, List.of(key), rate, burst);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
        if (waitMillis == null) {
            throw unavailable();
        }
        if (waitMillis > 0) {
            long waitSeconds = Math.ceilDiv(waitMillis, 1000);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(waitSeconds));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CAMPAIGN_RATE_LIMITED",
                    "Too many requests. Try again shortly.", headers);
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAMPAIGN_ADMISSION_UNAVAILABLE",
                "The offer is temporarily unavailable. Check the same claim before retrying.");
    }
}
