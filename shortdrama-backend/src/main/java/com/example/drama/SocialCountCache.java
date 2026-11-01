package com.example.drama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SocialCountCache {
    private static final Logger log = LoggerFactory.getLogger(SocialCountCache.class);
    private static final long WARNING_INTERVAL_MILLIS = 60_000;

    private final SocialCacheProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ConcurrentMap<Long, Long> dirtyGenerations = new ConcurrentHashMap<>();
    private final AtomicLong nextWarningAt = new AtomicLong();

    public SocialCountCache(SocialCacheProperties properties, Optional<StringRedisTemplate> redis,
                            ObjectMapper mapper) {
        this.properties = properties;
        this.redis = redis.orElse(null);
        this.mapper = mapper;
    }

    public SocialCounts getOrLoad(Long dramaId, Supplier<SocialCounts> databaseLoader) {
        if (!active()) return databaseLoader.get();

        CacheLookup lookup = lookup(dramaId);
        if (lookup != null && lookup.value() != null) return lookup.value();

        SocialCounts databaseValue = databaseLoader.get();
        if (lookup != null) storeIfCurrent(dramaId, lookup.epoch(), databaseValue);
        return databaseValue;
    }

    public void invalidateAfterCommit(Long dramaId) {
        if (!active()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { invalidateNow(dramaId); }
            });
            return;
        }
        invalidateNow(dramaId);
    }

    private CacheLookup lookup(Long dramaId) {
        try {
            recoverDirty(dramaId);
            if (dirtyGenerations.containsKey(dramaId)) return null;
            String epoch = epoch(dramaId);
            String json = redis.opsForValue().get(countsKey(dramaId, epoch));
            return new CacheLookup(epoch, json == null ? null : mapper.readValue(json, SocialCounts.class));
        } catch (Exception exception) {
            warn("read", dramaId, exception);
            return null;
        }
    }

    private void storeIfCurrent(Long dramaId, String loadedEpoch, SocialCounts value) {
        try {
            if (dirtyGenerations.containsKey(dramaId)) return;
            String currentEpoch = epoch(dramaId);
            if (!loadedEpoch.equals(currentEpoch)) return;
            redis.opsForValue().setIfAbsent(
                    countsKey(dramaId, loadedEpoch), mapper.writeValueAsString(value), properties.getTtl());
        } catch (Exception exception) {
            warn("write", dramaId, exception);
        }
    }

    private void invalidateNow(Long dramaId) {
        long generation = dirtyGenerations.merge(dramaId, 1L, Long::sum);
        try {
            redis.opsForValue().increment(epochKey(dramaId));
            dirtyGenerations.compute(dramaId, (ignored, current) ->
                    Objects.equals(current, generation) ? null : current);
        } catch (Exception exception) {
            warn("invalidate", dramaId, exception);
        }
    }

    private void recoverDirty(Long dramaId) {
        Long generation = dirtyGenerations.get(dramaId);
        if (generation == null) return;
        try {
            redis.opsForValue().increment(epochKey(dramaId));
            dirtyGenerations.compute(dramaId, (ignored, current) ->
                    Objects.equals(current, generation) ? null : current);
        } catch (Exception exception) {
            warn("recover", dramaId, exception);
        }
    }

    private String epoch(Long dramaId) {
        String value = redis.opsForValue().get(epochKey(dramaId));
        return value == null ? "0" : value;
    }

    private String epochKey(Long dramaId) {
        return properties.getKeyPrefix() + "drama:" + dramaId + ":epoch";
    }

    private String countsKey(Long dramaId, String epoch) {
        return properties.getKeyPrefix() + "drama:" + dramaId + ":counts:" + epoch;
    }

    private boolean active() { return properties.isEnabled() && redis != null; }

    private void warn(String operation, Long dramaId, Exception exception) {
        long now = System.currentTimeMillis();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_MILLIS)) {
            log.warn("Redis social cache {} failed for drama {}; using MySQL fallback: {}",
                    operation, dramaId, exception.getClass().getSimpleName());
        }
    }

    private record CacheLookup(String epoch, SocialCounts value) {}
}
