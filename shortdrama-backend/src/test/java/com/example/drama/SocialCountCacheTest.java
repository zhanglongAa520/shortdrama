package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SocialCountCacheTest {
    private static final String PREFIX = "ai-short-drama:social:";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    private AutoCloseable mocks;
    private SocialCacheProperties properties;
    private SocialCountCache cache;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        properties = new SocialCacheProperties();
        properties.setEnabled(true);
        properties.setKeyPrefix(PREFIX);
        properties.setTtl(Duration.ofSeconds(30));
        when(redis.opsForValue()).thenReturn(values);
        cache = new SocialCountCache(properties, Optional.of(redis), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        mocks.close();
    }

    @Test
    void disabledCacheAlwaysUsesDatabaseWithoutRedisCalls() {
        properties.setEnabled(false);
        SocialCounts expected = new SocialCounts(1, 2, 3);

        assertEquals(expected, cache.getOrLoad(7L, () -> expected));

        verifyNoInteractions(redis);
    }

    @Test
    void cacheHitDoesNotCallDatabase() throws Exception {
        SocialCounts expected = new SocialCounts(4, 5, 6);
        when(values.get(epochKey(7L))).thenReturn("3");
        when(values.get(countsKey(7L, "3"))).thenReturn(new ObjectMapper().writeValueAsString(expected));
        AtomicInteger loads = new AtomicInteger();

        SocialCounts actual = cache.getOrLoad(7L, () -> {
            loads.incrementAndGet();
            return new SocialCounts(0, 0, 0);
        });

        assertEquals(expected, actual);
        assertEquals(0, loads.get());
    }

    @Test
    void cacheMissLoadsDatabaseAndStoresOnlyForCurrentEpoch() {
        SocialCounts expected = new SocialCounts(4, 5, 6);
        when(values.get(epochKey(7L))).thenReturn("2");
        when(values.get(countsKey(7L, "2"))).thenReturn(null);

        assertEquals(expected, cache.getOrLoad(7L, () -> expected));

        verify(values).setIfAbsent(eq(countsKey(7L, "2")), any(String.class), eq(Duration.ofSeconds(30)));
    }

    @Test
    void epochChangeDuringColdLoadPreventsStaleRefill() {
        SocialCounts expected = new SocialCounts(4, 5, 6);
        when(values.get(epochKey(7L))).thenReturn("2", "3");
        when(values.get(countsKey(7L, "2"))).thenReturn(null);

        assertEquals(expected, cache.getOrLoad(7L, () -> expected));

        verify(values, never()).setIfAbsent(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void redisReadFailureFallsBackToDatabaseOnce() {
        when(values.get(epochKey(7L))).thenThrow(new RedisConnectionFailureException("offline"));
        AtomicInteger loads = new AtomicInteger();

        SocialCounts actual = cache.getOrLoad(7L, () -> {
            loads.incrementAndGet();
            return new SocialCounts(1, 2, 3);
        });

        assertEquals(new SocialCounts(1, 2, 3), actual);
        assertEquals(1, loads.get());
    }

    @Test
    void invalidationRunsAfterCommitButNotAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache.invalidateAfterCommit(7L);
        verify(values, never()).increment(epochKey(7L));

        TransactionSynchronization commit = TransactionSynchronizationManager.getSynchronizations().getFirst();
        commit.afterCommit();
        verify(values).increment(epochKey(7L));
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit(8L);
        TransactionSynchronization rollback = TransactionSynchronizationManager.getSynchronizations().getFirst();
        rollback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(values, never()).increment(epochKey(8L));
    }

    @Test
    void failedInvalidationStaysDirtyAndIsRetriedBeforeCacheRead() {
        when(values.increment(epochKey(7L)))
                .thenThrow(new RedisConnectionFailureException("offline"))
                .thenReturn(5L);
        when(values.get(epochKey(7L))).thenReturn("5");
        when(values.get(countsKey(7L, "5"))).thenReturn(null);
        SocialCounts expected = new SocialCounts(7, 8, 9);

        cache.invalidateAfterCommit(7L);
        assertEquals(expected, cache.getOrLoad(7L, () -> expected));

        verify(values, times(2)).increment(epochKey(7L));
        verify(values).setIfAbsent(eq(countsKey(7L, "5")), any(String.class), eq(Duration.ofSeconds(30)));
    }

    private String epochKey(long dramaId) { return PREFIX + "drama:" + dramaId + ":epoch"; }
    private String countsKey(long dramaId, String epoch) {
        return PREFIX + "drama:" + dramaId + ":counts:" + epoch;
    }
}
