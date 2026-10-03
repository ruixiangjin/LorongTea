package com.lorongtea.catalog;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
class MenuCache {
    private static final Logger log = LoggerFactory.getLogger(MenuCache.class);
    private static final TypeReference<List<CatalogService.MenuItemView>> MENU_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final MeterRegistry metrics;
    private final boolean enabled;

    MenuCache(StringRedisTemplate redis, JsonMapper json, MeterRegistry metrics,
            @Value("${lorong.redis.enabled}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    List<CatalogService.MenuItemView> getOrLoad(long storeId, Supplier<List<CatalogService.MenuItemView>> load) {
        if (!enabled) {
            return load.get();
        }

        List<CatalogService.MenuItemView> cached;
        try {
            String value = redis.opsForValue().get(key(storeId));
            cached = value == null ? null : json.readValue(value, MENU_TYPE);
        } catch (DataAccessException | JacksonException exception) {
            metrics.counter("lorong.menu.cache.read", "result", "error").increment();
            log.debug("Menu cache read failed for store {}", storeId, exception);
            return load.get();
        }

        if (cached != null) {
            metrics.counter("lorong.menu.cache.read", "result", "hit").increment();
            return cached;
        }
        metrics.counter("lorong.menu.cache.read", "result", "miss").increment();
        var menu = load.get();
        write(storeId, menu);
        return menu;
    }

    private void write(long storeId, List<CatalogService.MenuItemView> menu) {
        try {
            Duration ttl = Duration.ofSeconds(60 + ThreadLocalRandom.current().nextInt(16));
            redis.opsForValue().set(key(storeId), json.writeValueAsString(menu), ttl);
        } catch (DataAccessException | JacksonException exception) {
            metrics.counter("lorong.menu.cache.failure", "operation", "write").increment();
            log.debug("Menu cache fill failed for store {}", storeId, exception);
        }
    }

    @TransactionalEventListener
    void invalidate(MenuChanged event) {
        if (!enabled) {
            return;
        }
        try {
            redis.delete(key(event.storeId()));
        } catch (DataAccessException exception) {
            metrics.counter("lorong.menu.cache.failure", "operation", "invalidate").increment();
            log.warn("Committed menu update could not invalidate the Redis cache for store {}", event.storeId());
        }
    }

    private static String key(long storeId) {
        return "lt:menu:v1:" + storeId;
    }
}
