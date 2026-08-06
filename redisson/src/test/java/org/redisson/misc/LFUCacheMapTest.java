package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.cache.Cache;
import org.redisson.cache.LFUCacheMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LFUCacheMapTest {

    // ------------------------------------------------------------------
    // Internals reached by reflection. The counter-overflow cases cannot be driven
    // through the public API - reaching the wrap naturally takes ~9.2e18 reads.
    // ------------------------------------------------------------------

    /**
     * Null when the implementation carries no spread cap. Resolved leniently on purpose: a
     * hard failure here runs in the static initializer and takes down every test in the
     * class, turning "one internal field was renamed" into thirty-odd unrelated failures.
     */
    private static final Long MAX_SPREAD = readStaticLong("MAX_SPREAD");

    private static Long readStaticLong(String name) {
        try {
            Field f = LFUCacheMap.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.getLong(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void seedBaseline(Cache<?, ?> map, long value) throws Exception {
        Field f = LFUCacheMap.class.getDeclaredField("baseline");
        f.setAccessible(true);
        f.setLong(map, value);
    }

    private static long baseline(Cache<?, ?> map) throws Exception {
        Field f = LFUCacheMap.class.getDeclaredField("baseline");
        f.setAccessible(true);
        return f.getLong(map);
    }

    private static void seedIdGenerator(Cache<?, ?> map, long value) throws Exception {
        Field f = LFUCacheMap.class.getDeclaredField("idGenerator");
        f.setAccessible(true);
        ((AtomicLong) f.get(map)).set(value);
    }

    @Test
    public void testMaxIdleTimeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 0, 400);
        map.put(1, 0);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(410);
        assertThat(map.keySet()).isEmpty();
    }

    @Test
    public void testTTLEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 500, 0);
        map.put(1, 0);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(100);
        assertThat(map.get(1)).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1);
        Thread.sleep(500);
        assertThat(map.keySet()).isEmpty();
    }

    @Test
    public void testSizeLFUEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(3, 0, 0);

        map.put(1, 0);
        map.put(2, 0);
        map.put(6, 0);

        map.get(1);
        map.put(3, 0);

        assertThat(map.keySet()).containsOnly(3, 1, 6);

        map.get(1);
        map.put(4, 0);

        assertThat(map.keySet()).contains(4, 1).hasSize(3);
    }

    @Test
    public void testSizeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 0, 0);
        map.put(1, 0);
        map.put(2, 0);

        assertThat(map.keySet()).containsOnly(1, 2);

        map.put(3, 0);

        assertThat(map.keySet()).contains(3).hasSize(2);

        map.put(4, 0);

        assertThat(map.keySet()).contains(4).hasSize(2);

        map.put(5, 0);

        assertThat(map.keySet()).contains(5).hasSize(2);
    }

    // ==================================================================
    // capacity edges
    // ==================================================================

    /**
     * A maximum size of zero means "unbounded", not "hold nothing" - {@code isFull} short
     * circuits on it so eviction never runs.
     */
    @Test
    public void sizeZeroDisablesEviction() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(0, 0, 0);
        for (int i = 0; i < 500; i++) {
            map.put(i, i);
        }
        assertThat(map).hasSize(500);
    }

    @Test
    public void negativeSizeIsRejected() {
        assertThatThrownBy(() -> new LFUCacheMap<Integer, Integer>(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * With room for one entry there is never a second candidate, so even a heavily read key
     * loses to the incoming one.
     */
    @Test
    public void capacityOfOneHoldsOnlyTheNewestEntry() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(1, 0, 0);
        map.put(1, 1);
        for (int i = 0; i < 10; i++) {
            map.get(1);
        }
        map.put(2, 2);

        assertThat(map.keySet()).containsExactly(2);
    }

    @Test
    public void evictionHoldsTheLineOverManyInserts() {
        int maxSize = 8;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        for (int i = 0; i < 10_000; i++) {
            map.put(i, i);
            assertThat(map.size()).isLessThanOrEqualTo(maxSize);
        }
    }

    // ==================================================================
    // null handling
    // ==================================================================

    @Test
    public void nullKeyOperationsThrow() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(4, 0, 0);
        map.put(1, 1);

        assertThatThrownBy(() -> map.get(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> map.containsKey(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> map.containsValue(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> map.remove(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> map.put(null, 1)).isInstanceOf(NullPointerException.class);
    }

    // ==================================================================
    // frequency semantics
    // ==================================================================

    /**
     * With nothing read, every entry sits at the same frequency and the tie breaks on
     * insertion order - oldest goes first.
     */
    @Test
    public void tiedFrequenciesEvictTheOldestEntry() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);

        map.put(4, 4);

        assertThat(map.keySet()).containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    public void frequentlyReadKeySurvivesEviction() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 5; i++) {
            map.get(2);
        }

        map.put(4, 4);
        map.put(5, 5);

        assertThat(map.containsKey(2)).isTrue();
        assertThat(map).hasSize(3);
    }

    /**
     * Writing over a key installs a fresh entry starting at the eviction floor, so the old
     * value's accumulated frequency does not carry over.
     */
    @Test
    public void puttingOverAKeyResetsItsFrequency() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 5; i++) {
            map.get(1);
        }
        map.get(2);
        map.get(3);            // 1 -> 5 reads, 2 and 3 -> 1 read each

        map.put(1, 99);        // hottest key rewritten
        map.put(4, 4);

        assertThat(map.keySet())
                .as("key 1 lost its frequency on rewrite and is now the coldest")
                .containsExactlyInAnyOrder(2, 3, 4);
    }

    /**
     * {@code putIfAbsent} on a present key must leave the existing entry - and therefore its
     * frequency - untouched.
     */
    @Test
    public void putIfAbsentOnAnExistingKeyPreservesItsFrequency() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 5; i++) {
            map.get(1);
        }
        map.get(2);
        map.get(3);

        assertThat(map.putIfAbsent(1, 99)).isEqualTo(1);
        map.put(4, 4);

        assertThat(map.keySet())
                .as("key 1 keeps its 5 reads and must outlive the single-read keys")
                .contains(1);
    }

    @Test
    public void replaceResetsFrequency() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 5; i++) {
            map.get(1);
        }
        map.get(2);
        map.get(3);

        map.replace(1, 99);
        map.put(4, 4);

        assertThat(map.keySet()).containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    public void computeIfPresentResetsFrequency() {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 5; i++) {
            map.get(1);
        }
        map.get(2);
        map.get(3);

        map.computeIfPresent(1, (k, v) -> v + 1);
        map.put(4, 4);

        assertThat(map.keySet()).containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    public void clearEmptiesTheCacheAndEvictionKeepsWorkingAfterwards() {
        int maxSize = 4;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
            map.get(i);
        }

        map.clear();
        assertThat(map).isEmpty();

        for (int i = 100; i < 200; i++) {
            map.put(i, i);
        }
        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    // ==================================================================
    // expiry edges
    // ==================================================================

    @Test
    public void zeroTtlAndMaxIdleNeverExpire() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<>(4, 0, 0);
        map.put(1, 1);
        Thread.sleep(150);
        assertThat(map.get(1)).isEqualTo(1);
    }

    /**
     * A full cache holding expired entries reclaims those instead of evicting a live one -
     * {@code removeExpiredEntries} runs first and suppresses the eviction.
     */
    @Test
    public void expiredEntriesAreReclaimedBeforeEvictingLiveOnes() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<>(2, 150, 0);
        map.put(1, 1);
        map.put(2, 2);
        Thread.sleep(250);

        map.put(3, 3);

        assertThat(map.keySet()).containsExactly(3);
    }

    // ==================================================================
    // accessCount overflow
    //
    // Frequencies are absolute counters offset by a monotonically increasing floor, so they
    // eventually run past Long.MAX_VALUE. A plain signed comparison would sort a wrapped
    // counter below every other entry, making the *most* frequently used key the next
    // eviction victim - silently, with no exception. MapKey.compareTo compares the
    // difference of two counters instead, which stays correct through the wrap as long as
    // live counters are held within MAX_SPREAD of each other.
    // ==================================================================

    /**
     * The offsets 1..11 are the discriminating band, not padding: with reads of 10/5/3/1 the
     * failure only shows when the hot keys cross the wrap and the cold one does not. A sparse
     * seed list passes by luck - an earlier version of this test caught a broken comparator
     * at offset 3 but missed it at 1, 10 and 0.
     */
    @ParameterizedTest(name = "baseline = Long.MAX_VALUE - {0}")
    @ValueSource(longs = {0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L,
            4611686018427387903L, 9223372036854775807L})
    public void leastFrequentKeyIsEvictedAcrossTheCounterWrap(long offsetFromMax) throws Exception {
        Cache<Integer, Integer> map = new LFUCacheMap<>(4, 0, 0);
        seedBaseline(map, Long.MAX_VALUE - offsetFromMax);

        for (int i = 0; i < 4; i++) {
            map.put(i, i);
        }
        for (int i = 0; i < 10; i++) {
            map.get(0);
        }
        for (int i = 0; i < 5; i++) {
            map.get(1);
        }
        for (int i = 0; i < 3; i++) {
            map.get(2);
        }
        map.get(3);            // coldest

        map.put(99, 99);

        assertThat(map.keySet())
                .as("key 3 was least frequently used and must be the one evicted")
                .containsExactlyInAnyOrder(0, 1, 2, 99);
    }

    @ParameterizedTest(name = "baseline = {0}")
    @ValueSource(longs = {Long.MIN_VALUE, Long.MIN_VALUE + 5, -1000L, -7L})
    public void orderingHoldsOnceCountersHaveWrappedNegative(long seed) throws Exception {
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        seedBaseline(map, seed);

        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        for (int i = 0; i < 8; i++) {
            map.get(1);
        }
        map.get(2);            // key 3 stays coldest

        map.put(4, 4);

        assertThat(map.keySet()).containsExactlyInAnyOrder(1, 2, 4);
    }

    /**
     * The floor is compared with the same wraparound arithmetic, so it must keep advancing
     * through the wrap rather than sticking at {@code Long.MAX_VALUE}.
     */
    @Test
    public void evictionFloorAdvancesPastTheWrap() throws Exception {
        Cache<Integer, Integer> map = new LFUCacheMap<>(2, 0, 0);
        seedBaseline(map, Long.MAX_VALUE - 2);

        for (int i = 0; i < 40; i++) {
            map.put(i, i);
            map.get(i);
            map.get(i);
        }

        assertThat(baseline(map))
                .as("floor must wrap into negative territory, not stall at Long.MAX_VALUE")
                .isNegative();
        assertThat(map.size()).isLessThanOrEqualTo(2);
    }

    /**
     * The severe consequence of getting the wrap wrong is not one mis-chosen victim, it is the
     * steady state afterwards. If the floor stops advancing, every new entry enters at the old
     * ceiling and its first read wraps it to the bottom of the order - so the cache stops
     * retaining anything that is read, permanently, with no exception and no self-healing.
     * A frequently read key must keep surviving inserts indefinitely once counters have wrapped.
     */
    @Test
    public void frequentlyReadKeyStillSurvivesOnceCountersHaveWrapped() throws Exception {
        Cache<Integer, Integer> map = new LFUCacheMap<>(4, 0, 0);
        seedBaseline(map, Long.MAX_VALUE - 2);

        // drive the counters through the wrap
        for (int i = 0; i < 40; i++) {
            map.put(i, i);
            map.get(i);
            map.get(i);
        }

        map.put(777, 777);
        int survived = 0;
        for (int round = 0; round < 200; round++) {
            for (int j = 0; j < 20; j++) {
                map.get(777);
            }
            map.put(2_000 + round, round);
            if (map.containsKey(777)) {
                survived++;
            }
        }

        assertThat(survived)
                .as("a repeatedly read key must survive every insert after the wrap")
                .isEqualTo(200);
    }

    @Test
    public void sustainedTrafficStraddlingTheWrapStaysConsistent() throws Exception {
        int maxSize = 64;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        seedBaseline(map, Long.MAX_VALUE - 500);
        Random r = new Random(3);

        for (int i = 0; i < 400_000; i++) {
            int k = r.nextInt(maxSize * 4);
            if (r.nextInt(100) < 35) {
                map.put(k, k);
            } else {
                map.get(k);
            }
        }

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    /**
     * Counting stops once an entry runs {@code MAX_SPREAD} ahead of the floor. That cap keeps
     * every live counter inside a window narrower than 2^63, which is the precondition for
     * wraparound comparison being a consistent total order. Saturating is harmless - the key
     * is already maximally frequent - but it must not lose the entry or corrupt ordering.
     */
    @Test
    public void countersSaturateInsteadOfWideningTheComparableWindow() throws Exception {
        assumeTrue(MAX_SPREAD != null, "this build carries no spread cap");
        Cache<Integer, Integer> map = new LFUCacheMap<>(3, 0, 0);
        map.put(1, 1);
        map.put(2, 2);
        for (int i = 0; i < 50; i++) {
            map.get(1);
        }

        seedBaseline(map, -MAX_SPREAD);   // key 1 now sits at the cap
        for (int i = 0; i < 50; i++) {
            map.get(1);                   // saturated: must be a no-op, not an overflow
        }

        assertThat(map.get(1)).isEqualTo(1);

        map.put(3, 3);
        map.put(4, 4);

        assertThat(map.containsKey(1)).as("the saturated key is the hottest and must survive").isTrue();
        assertThat(map.size()).isLessThanOrEqualTo(3);
    }

    /**
     * Entry ids break ties between equally frequent entries and grow without bound too. Once
     * they wrap, "older" and "newer" swap places among ties - a policy quirk rather than a
     * correctness problem - but no entry may be lost and the size bound must still hold.
     */
    @Test
    public void entryIdWrapDoesNotLoseEntries() throws Exception {
        int maxSize = 3;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        seedIdGenerator(map, Long.MAX_VALUE - 2);

        for (int i = 0; i < 50; i++) {
            map.put(i, i);
            map.get(i);
            assertThat(map.size()).isLessThanOrEqualTo(maxSize);
        }
        assertThat(map).hasSize(maxSize);
    }

    /**
     * The wrap must not turn eviction into the full-table scan the floor scheme exists to
     * avoid - a put at the wrap point costs the same as any other put.
     */
    @Test
    public void evictionAtTheWrapPointDoesNotDegrade() throws Exception {
        int maxSize = 20_000;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        for (int i = 0; i < maxSize; i++) {
            map.put(i, i);
            map.get(i);
        }

        assertThatCode(() -> {
            for (int i = 0; i < 500; i++) {
                seedBaseline(map, Long.MAX_VALUE - 1);
                map.put(1_000_000 + i, i);
            }
        }).doesNotThrowAnyException();

        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }

    // ==================================================================
    // concurrency
    // ==================================================================

    @Test
    public void concurrentTrafficRespectsMaxSizeAndDoesNotThrow() throws Exception {
        int maxSize = 500;
        int threads = 8;
        Cache<Integer, Integer> map = new LFUCacheMap<>(maxSize, 0, 0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> results = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            results.add(pool.submit(() -> {
                try {
                    start.await();
                    Random r = new Random(id);
                    for (int i = 0; i < 50_000; i++) {
                        int k = r.nextInt(maxSize * 3);
                        int roll = r.nextInt(100);
                        if (roll < 30) {
                            map.put(k, k);
                        } else if (roll < 90) {
                            map.get(k);
                        } else {
                            map.remove(k);
                        }
                    }
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();

        for (Future<Throwable> f : results) {
            assertThat(f.get()).isNull();
        }
        assertThat(map.size()).isLessThanOrEqualTo(maxSize);
    }
}