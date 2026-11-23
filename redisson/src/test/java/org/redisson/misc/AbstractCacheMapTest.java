package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.cache.AbstractCacheMap;
import org.redisson.cache.CachedValue;
import org.redisson.cache.LFUCacheMap;
import org.redisson.cache.LRUCacheMap;

import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AbstractCacheMapTest {

    @FunctionalInterface
    interface CacheFactory {
        AbstractCacheMap<Integer, Integer> create(int size, long ttl, long maxIdle);
    }

    static Stream<Arguments> caches() {
        return Stream.of(
                Arguments.of("LFUCacheMap", (CacheFactory) LFUCacheMap::new),
                Arguments.of("LRUCacheMap", (CacheFactory) LRUCacheMap::new));
    }

    // ------------------------------------------------------------------
    // Defect 1: computeIfPresent drops a mapping without onValueRemove
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void computeIfPresentReturningNullNotifiesRemovalListener(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        AtomicInteger removals = new AtomicInteger();
        map.removalListener(v -> removals.incrementAndGet());

        for (int i = 0; i < 5; i++) {
            map.put(i, i);
        }
        for (int i = 0; i < 5; i++) {
            assertThat(map.computeIfPresent(i, (k, v) -> null)).isNull();
        }

        assertThat(map).isEmpty();
        assertThat(removals).hasValue(5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void computeIfPresentDroppingExpiredEntryNotifiesRemovalListener(String name, CacheFactory factory)
            throws InterruptedException {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 100, 0);
        AtomicInteger removals = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            map.put(i, i);
        }
        map.removalListener(v -> removals.incrementAndGet());
        Thread.sleep(200);

        for (int i = 0; i < 5; i++) {
            assertThat(map.computeIfPresent(i, (k, v) -> v + 1)).isNull();
        }

        assertThat(map).isEmpty();
        assertThat(removals).hasValue(5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void computeIfPresentReturningNullKeepsCacheWithinMaxSize(String name, CacheFactory factory) {
        int maxSize = 10;
        AbstractCacheMap<Integer, Integer> map = factory.create(maxSize, 0, 0);

        // fill, then drain via computeIfPresent -> orphans maxSize entries in the eviction index
        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
        }
        for (int i = 0; i < maxSize; i++) {
            map.computeIfPresent(i, (k, v) -> null);
        }
        assertThat(map).isEmpty();

        // refill with fresh keys - eviction should hold the line at maxSize
        for (int i = 0; i < maxSize * 3; i++) {
            map.put(1_000 + i, i);
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void computeIfPresentReplacingValueNotifiesRemovalListener(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        AtomicInteger removals = new AtomicInteger();
        map.put(1, 1);
        map.removalListener(v -> removals.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            assertThat(map.computeIfPresent(1, (k, v) -> v + 1)).isEqualTo(i + 2);
        }

        assertThat(map).hasSize(1);
        assertThat(removals).hasValue(10);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void updatingViaComputeIfPresentKeepsCacheWithinMaxSize(String name, CacheFactory factory) {
        int maxSize = 10;
        AbstractCacheMap<Integer, Integer> map = factory.create(maxSize, 0, 0);

        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
        }
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < maxSize; i++) {
                map.computeIfPresent(i, (k, v) -> v + 1);
            }
        }
        assertThat(map).hasSize(maxSize);

        for (int i = 0; i < maxSize * 3; i++) {
            map.put(1_000 + i, i);
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    // ------------------------------------------------------------------
    // Defect 2: view iterators cannot remove
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void keySetIteratorSupportsRemove(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 1);
        map.put(2, 2);

        Iterator<Integer> it = map.keySet().iterator();
        assertThat(it.hasNext()).isTrue();
        it.next();
        assertThatCode(it::remove).doesNotThrowAnyException();

        assertThat(map).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void valuesIteratorSupportsRemove(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 1);
        map.put(2, 2);

        Iterator<Integer> it = map.values().iterator();
        assertThat(it.hasNext()).isTrue();
        it.next();
        assertThatCode(it::remove).doesNotThrowAnyException();

        assertThat(map).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void entrySetIteratorSupportsRemove(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 1);
        map.put(2, 2);

        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        assertThat(it.hasNext()).isTrue();
        it.next();
        assertThatCode(it::remove).doesNotThrowAnyException();

        assertThat(map).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void keySetIteratorRemoveNotifiesRemovalListener(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        AtomicInteger removals = new AtomicInteger();
        map.removalListener(v -> removals.incrementAndGet());
        map.put(1, 1);

        Iterator<Integer> it = map.keySet().iterator();
        assertThat(it.hasNext()).isTrue();
        it.next();
        it.remove();

        assertThat(map).isEmpty();
        assertThat(removals).hasValue(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void iteratorNextWorksWithoutCallingHasNextFirst(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 1);

        assertThat(map.keySet().iterator().next()).isEqualTo(1);
        assertThat(map.values().iterator().next()).isEqualTo(1);
        assertThat(map.entrySet().iterator().next()).isEqualTo(new AbstractMap.SimpleEntry<>(1, 1));
    }

    // ------------------------------------------------------------------
    // Defect 3: entrySet() remove/contains compare the wrong types
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void entrySetRemoveRemovesMatchingEntry(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 99);

        boolean removed = map.entrySet().remove(new AbstractMap.SimpleEntry<>(1, 99));

        assertThat(removed).isTrue();
        assertThat(map.containsKey(1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void entrySetRemoveIgnoresNonMatchingValue(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 99);

        boolean removed = map.entrySet().remove(new AbstractMap.SimpleEntry<>(1, 1234));

        assertThat(removed).isFalse();
        assertThat(map.containsKey(1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void entrySetContainsFindsPresentEntry(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        map.put(1, 99);

        assertThat(map.entrySet().contains(new AbstractMap.SimpleEntry<>(1, 99))).isTrue();
        assertThat(map.entrySet().contains(new AbstractMap.SimpleEntry<>(1, 1234))).isFalse();
        assertThat(map.entrySet().contains(new AbstractMap.SimpleEntry<>(2, 99))).isFalse();
    }

    // ------------------------------------------------------------------
    // Defect 4: putIfAbsent never evicts
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void putIfAbsentKeepsCacheWithinMaxSize(String name, CacheFactory factory) {
        int maxSize = 10;
        AbstractCacheMap<Integer, Integer> map = factory.create(maxSize, 0, 0);

        for (int i = 0; i < maxSize * 10; i++) {
            assertThat(map.putIfAbsent(i, i)).isNull();
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
        assertThat(map.containsKey(maxSize * 10 - 1)).as("the key added last").isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void putIfAbsentOfAPresentKeyEvictsNothing(String name, CacheFactory factory) {
        int maxSize = 10;
        AbstractCacheMap<Integer, Integer> map = factory.create(maxSize, 0, 0);
        AtomicInteger removals = new AtomicInteger();
        map.removalListener(v -> removals.incrementAndGet());
        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
        }

        for (int i = 0; i < maxSize; i++) {
            assertThat(map.putIfAbsent(i, 100 + i)).isEqualTo(i);
        }

        assertThat(map).hasSize(maxSize);
        assertThat(removals).hasValue(0);
    }

    // ------------------------------------------------------------------
    // Defect 5: put shows an entry to eviction before the entry is in the map
    // ------------------------------------------------------------------

    static final class KeyWithAction {

        Runnable action;
        private boolean running;

        @Override
        public int hashCode() {
            if (action != null && !running) {
                running = true;
                try {
                    action.run();
                } finally {
                    running = false;
                }
            }
            return 15;
        }
    }

    static Stream<Arguments> cachesOfObjects() {
        return Stream.of(
                Arguments.of("LFUCacheMap",
                        (IntFunction<AbstractCacheMap<Object, Object>>) size -> new LFUCacheMap<>(size, 0, 0)),
                Arguments.of("LRUCacheMap",
                        (IntFunction<AbstractCacheMap<Object, Object>>) size -> new LRUCacheMap<>(size, 0, 0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachesOfObjects")
    public void entryPutWhileOtherPutsEvictIsEvictedInItsTurn(String name,
                                                    IntFunction<AbstractCacheMap<Object, Object>> factory) {
        int maxSize = 10;
        AbstractCacheMap<Object, Object> map = factory.apply(maxSize);
        AtomicInteger nextKey = new AtomicInteger();
        Runnable putMoreKeysThanTheCacheHolds = () -> {
            for (int i = 0; i < maxSize * 2; i++) {
                map.put(nextKey.incrementAndGet(), 0);
            }
        };
        putMoreKeysThanTheCacheHolds.run();

        KeyWithAction key = new KeyWithAction();
        key.action = putMoreKeysThanTheCacheHolds;
        map.put(key, 0);
        key.action = null;
        assertThat(map.containsKey(key)).isTrue();

        putMoreKeysThanTheCacheHolds.run();

        assertThat(map.containsKey(key))
                .as("an entry that the evictions during its put() took out of their order is never evicted")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void concurrentPutsOfNewKeysKeepCacheWithinMaxSize(String name, CacheFactory factory) throws Exception {
        int maxSize = 10;
        int threads = 8;
        int putsPerThread = 250_000;
        AbstractCacheMap<Integer, Integer> map = factory.create(maxSize, 0, 0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int firstKey = t * putsPerThread;
                results.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < putsPerThread; i++) {
                        map.put(firstKey + i, i);
                        map.get(firstKey + i);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize + threads);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachesOfObjects")
    public void removedEntriesAreNotKept(String name, IntFunction<AbstractCacheMap<Object, Object>> factory)
            throws Exception {
        int keys = 4;
        int threads = 4;
        int callsPerThread = 250_000;
        // a size that is never reached, so no eviction takes an entry out
        AbstractCacheMap<Object, Object> map = factory.apply(1_000_000);
        Object[] values = new Object[1000];
        List<WeakReference<Object>> references = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            values[i] = new Object();
            references.add(new WeakReference<>(values[i]));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                results.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        Integer key = i % keys;
                        Object value = values[(i + thread) % values.length];
                        if (thread == 0) {
                            map.putIfAbsent(key, value);
                        } else if (thread == 1) {
                            map.remove(key);
                        } else {
                            map.put(key, value);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        for (int key = 0; key < keys; key++) {
            map.remove(key);
        }
        assertThat(map).isEmpty();

        Arrays.fill(values, null);
        long reachable = references.size();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (reachable > 0 && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(50);
            reachable = references.stream().filter(reference -> reference.get() != null).count();
        }
        assertThat(reachable).as("values of removed entries that the cache still holds").isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caches")
    public void putOverAKeyReturnsThePreviousValueAndNotifiesItsRemoval(String name, CacheFactory factory) {
        AbstractCacheMap<Integer, Integer> map = factory.create(10, 0, 0);
        List<Integer> removed = new ArrayList<>();
        map.removalListener(v -> removed.add(v.getValue()));

        assertThat(map.put(1, 100)).isNull();
        assertThat(map.put(1, 200)).isEqualTo(100);
        assertThat(map.put(1, 300)).isEqualTo(200);

        assertThat(map.get(1)).isEqualTo(300);
        assertThat(map).hasSize(1);
        assertThat(removed).containsExactly(100, 200);
    }

    // ------------------------------------------------------------------
    // Defect 6: a cache that is above its size stays above it
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachesOfObjects")
    public void cacheAboveMaxSizeGoesBackToIt(String name, IntFunction<AbstractCacheMap<Object, Object>> factory) {
        int maxSize = 10;
        AbstractCacheMap<Object, Object> map = factory.apply(maxSize);
        AtomicInteger nextKey = new AtomicInteger();
        for (int i = 0; i < maxSize; i++) {
            map.put(nextKey.incrementAndGet(), 0);
        }

        KeyWithAction key = new KeyWithAction();
        key.action = () -> map.put(nextKey.incrementAndGet(), 0);
        map.put(key, 0);
        key.action = null;

        for (int i = 0; i < maxSize; i++) {
            map.put(nextKey.incrementAndGet(), 0);
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    @Test
    public void removalListenerFiresOnPlainRemove() {
        AbstractCacheMap<Integer, Integer> map = new LFUCacheMap<>(10, 0, 0);
        AtomicInteger removals = new AtomicInteger();
        map.removalListener((CachedValue<Integer, Integer> v) -> removals.incrementAndGet());

        map.put(1, 1);
        map.remove(1);

        assertThat(removals).hasValue(1);
    }
}