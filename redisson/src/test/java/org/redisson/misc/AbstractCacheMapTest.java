package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.cache.AbstractCacheMap;
import org.redisson.cache.CachedValue;
import org.redisson.cache.LFUCacheMap;
import org.redisson.cache.LRUCacheMap;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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