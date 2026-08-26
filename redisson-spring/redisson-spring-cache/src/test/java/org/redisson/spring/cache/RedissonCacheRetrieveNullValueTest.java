package org.redisson.spring.cache;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reproduces a bug in {@link RedissonCache#retrieve(Object, java.util.function.Supplier)}:
 * once a key has a cached null value (allowNullValues=true, i.e. negative caching),
 * every subsequent retrieve() call for that key returns the internal {@link NullValue}
 * sentinel object instead of unwrapping it back to a real null, because the inner
 * re-check under the per-key lock reads the raw stored value without calling
 * fromStoreValue().
 */
public class RedissonCacheRetrieveNullValueTest extends RedisDockerTest {

    @Test
    public void retrieve_secondCallOnCachedNull_shouldReturnNullNotSentinel() throws Exception {
        RedissonClient redisson = createInstance();
        try {
            RMap<Object, Object> map = redisson.getMap("retrieve-null-test");
            RedissonCache cache = new RedissonCache(map, true);

            AtomicInteger loaderCalls = new AtomicInteger();

            // First call: cache miss, loader runs, returns business null -> stored as NullValue.INSTANCE
            Object first = cache.retrieve("k1", () -> {
                loaderCalls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }).get();
            assertNull(first, "first call should yield null");
            assertEquals(1, loaderCalls.get());

            // Second call: key already has a cached null. Loader must NOT run again,
            // and the returned value must be null, not the NullValue sentinel object.
            Object second = cache.retrieve("k1", () -> {
                loaderCalls.incrementAndGet();
                fail("valueLoader should not be invoked again for an already-cached null value");
                return CompletableFuture.completedFuture("should-not-happen");
            }).get();

            assertEquals(1, loaderCalls.get(), "loader must not be invoked again");
            assertNull(second, "BUG: retrieve() returned " + second
                    + " instead of null for an already-cached negative entry");
        } finally {
            redisson.shutdown();
        }
    }
}
