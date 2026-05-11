package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLocalCachedScoredSortedSet;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedScoredSortedSetOptions;
import org.redisson.api.options.LocalCachedScoredSortedSetParams;
import org.redisson.client.protocol.ScoredEntry;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RLocalCachedScoredSortedSet} covering:
 * - All overridden methods (LOCALCACHE_REDIS mode: local cache + Redis in sync)
 * - LocalOnly mode (StoreMode.LOCALCACHE: local-only, no Redis writes)
 * - Cross-instance cache synchronization via pub/sub
 * - Preload from Redis
 */
class RedissonLocalCachedScoredSortedSetTest extends RedisDockerTest {

    // ───────────────────────── helpers ──────────────────────────

    private RLocalCachedScoredSortedSet<String> createSet(String name) {
        return createSet(redisson, name);
    }

    /**
     * LOCALCACHE_REDIS – reads from local, writes to both.
     */
    private RLocalCachedScoredSortedSet<String> createSet(RedissonClient client, String name) {
        LocalCachedScoredSortedSetOptions<String> options = LocalCachedScoredSortedSetOptions.<String>name(name)
                .storeMode(LocalCachedScoredSortedSetOptions.StoreMode.LOCALCACHE_REDIS)
                .readMode(LocalCachedScoredSortedSetOptions.ReadMode.LOCALCACHE)
                .evictionPolicy(LocalCachedScoredSortedSetOptions.EvictionPolicy.NONE)
                .cacheSize(0)
                .reconnectionStrategy(LocalCachedScoredSortedSetOptions.ReconnectionStrategy.NONE);
        return client.getLocalCachedScoredSortedSet(name, options);
    }

    /**
     * StoreMode.LOCALCACHE – local only, never touches Redis.
     */
    private RLocalCachedScoredSortedSet<String> createLocalOnlySet(String name) {
        LocalCachedScoredSortedSetOptions<String> options = LocalCachedScoredSortedSetOptions.<String>name(name)
                .storeMode(LocalCachedScoredSortedSetOptions.StoreMode.LOCALCACHE)
                .evictionPolicy(LocalCachedScoredSortedSetOptions.EvictionPolicy.NONE)
                .cacheSize(0)
                .reconnectionStrategy(LocalCachedScoredSortedSetOptions.ReconnectionStrategy.NONE);
        return redisson.getLocalCachedScoredSortedSet(options);
    }

    /**
     * Creates a set with preload=true so the cache is populated from Redis on construction.
     */
    private RLocalCachedScoredSortedSet<String> createPreloadSet(String name) {
        LocalCachedScoredSortedSetParams<String> params =
                (LocalCachedScoredSortedSetParams<String>) LocalCachedScoredSortedSetOptions.<String>name(name);
        params.storeMode(LocalCachedScoredSortedSetOptions.StoreMode.LOCALCACHE_REDIS)
                .readMode(LocalCachedScoredSortedSetOptions.ReadMode.LOCALCACHE)
                .evictionPolicy(LocalCachedScoredSortedSetOptions.EvictionPolicy.NONE)
                .cacheSize(0)
                .reconnectionStrategy(LocalCachedScoredSortedSetOptions.ReconnectionStrategy.NONE)
                .preload(true);
        return redisson.getLocalCachedScoredSortedSet(params);
    }

    private RScoredSortedSet<String> redis(String name) {
        return redisson.getScoredSortedSet(name);
    }

    /**
     * Assert that local cache == Redis state (entries, scores, size).
     */
    private void assertConsistent(RLocalCachedScoredSortedSet<String> local, RScoredSortedSet<String> redis) {
        Collection<ScoredEntry<String>> redisEntries = redis.entryRange(0, -1);
        Collection<ScoredEntry<String>> localEntries = local.entryRange(0, -1);

        assertThat(localEntries).containsExactlyElementsOf(redisEntries);

        Map<String, Double> redisAsMap = redisEntries.stream()
                .collect(Collectors.toMap(ScoredEntry::getValue, ScoredEntry::getScore));
        assertThat(local.getCache()).containsExactlyInAnyOrderEntriesOf(redisAsMap);
        assertThat(local.size()).isEqualTo(redis.size());
    }

    // ─────────────────────────────────────────────────────────────
    //  1. add / addAll / contains / size / readAll / getScore
    // ─────────────────────────────────────────────────────────────

    @Test
    void testAdd() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:add");
        try {
            assertThat(set.add(1.0, "a")).isTrue();
            assertThat(set.add(1.0, "a")).isFalse();  // duplicate → false
            assertThat(set.add(2.0, "a")).isFalse();  // duplicate → false
            assertThat(set.add(2.0, "a")).isFalse();  // duplicate → false
            assertThat(set.getCache()).containsEntry("a", 2.0);
            assertConsistent(set, redis("t:add"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAll() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAll");
        try {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("a", 1.0);
            m.put("b", 2.0);
            m.put("c", 3.0);
            assertThat(set.addAll(m)).isEqualTo(3);
            assertThat(set.getCache()).containsEntry("a", 1.0)
                    .containsEntry("b", 2.0)
                    .containsEntry("c", 3.0);
            assertConsistent(set, redis("t:addAll"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testContains() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:contains");
        try {
            set.add(1.0, "x");
            assertThat(set.contains("x")).isTrue();
            assertThat(set.contains("y")).isFalse();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testContainsAll() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:containsAll");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.containsAll(Arrays.asList("a", "b"))).isTrue();
            assertThat(set.containsAll(Arrays.asList("a", "z"))).isFalse();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testSizeAndReadAll() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:sizeReadAll");
        try {
            assertThat(set.size()).isZero();
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.size()).isEqualTo(2);
            assertThat(set.readAll()).containsExactlyInAnyOrder("a", "b");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testGetScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:getScore");
        try {
            set.add(3.5, "x");
            assertThat(set.getScore("x")).isEqualTo(3.5);
            assertThat(set.getScore("missing")).isNull();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testGetScoreCollection() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:getScoreCol");
        try {
            set.add(1.0, "a");
            set.add(3.0, "c");
            List<Double> scores = set.getScore(Arrays.asList("a", "missing", "c"));
            assertThat(scores).containsExactly(1.0, null, 3.0);
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  2. remove / removeAll / removeRangeByScore / removeRangeByRank
    // ─────────────────────────────────────────────────────────────

    @Test
    void testRemove() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:remove");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.remove("a")).isTrue();
            assertThat(set.remove("missing")).isFalse();
            assertThat(set.getCache()).containsOnlyKeys("b");
            assertConsistent(set, redis("t:remove"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testRemoveAll() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:removeAll");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.removeAll(Arrays.asList("a", "c"))).isTrue();
            assertThat(set.getCache()).containsOnlyKeys("b");
            assertConsistent(set, redis("t:removeAll"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testRemoveRangeByScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:removeRangeByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            set.add(4.0, "d");
            int removed = set.removeRangeByScore(2.0, true, 3.0, true);
            assertThat(removed).isEqualTo(2);
            assertThat(set.getCache()).containsOnlyKeys("a", "d");
            assertConsistent(set, redis("t:removeRangeByScore"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testRemoveRangeByScoreInvalidRange() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:removeByScoreInvalid");
        try {
            set.add(1.0, "a");
            assertThat(set.removeRangeByScore(5.0, true, 1.0, true)).isZero();
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testRemoveRangeByRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:removeByRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            set.add(4.0, "d");
            assertThat(set.removeRangeByRank(1, 2)).isEqualTo(2);
            assertThat(set.getCache()).containsOnlyKeys("a", "d");
            assertConsistent(set, redis("t:removeByRank"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testDelete() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:delete");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.delete()).isTrue();
            assertThat(set.getCache()).isEmpty();
            assertThat(set.getScoreCache()).isEmpty();
            assertThat(redis("t:delete").size()).isZero();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testDeleteAsync_CrossInstance_BroadcastsClear() throws ExecutionException, InterruptedException {
        String name = "t:deleteAsync:crossInstance";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            // Populate both instances
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");

            // Wait for s2 to sync
            awaitCacheSize(s2, 3);
            assertThat(s2.getCache()).hasSize(3);
            assertThat(s2.getScoreCache()).isNotEmpty();

            // Delete via s1
            assertThat(s1.deleteAsync().get()).isTrue();

            // Verify s1 is cleared
            assertThat(s1.getCache()).isEmpty();
            assertThat(s1.getScoreCache()).isEmpty();

            // Verify s2 receives delete broadcast and clears its cache
            awaitCacheSize(s2, 0);
            assertThat(s2.getCache()).isEmpty();
            assertThat(s2.getScoreCache()).isEmpty();

            // Verify Redis is also cleared
            assertThat(redis(name).size()).isZero();

        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testDeleteAsync_LocalOnly_NoSync() throws ExecutionException, InterruptedException {
        String name = "t:deleteAsync:localOnly";
        RLocalCachedScoredSortedSet<String> s1 = createLocalOnlySet(name);
        try {
            s1.add(1.0, "x");
            s1.add(2.0, "y");
            assertThat(s1.deleteAsync().get()).isTrue();
            assertThat(s1.getCache()).isEmpty();
            assertThat(s1.getScoreCache()).isEmpty();
            // Redis should still be empty (local-only never touches Redis)
            assertThat(redis(name).size()).isZero();
        } finally {
            s1.destroy();
        }
    }

    @Test
    void testDeleteAsync_ClearsAllData() {
        String name = "t:deleteAsync:clearsAll";
        RLocalCachedScoredSortedSet<String> set = createSet(name);
        try {
            // Add multiple entries
            set.add(1.0, "a");
            set.add(1.0, "b");  // same score
            set.add(2.0, "c");
            set.add(3.0, "d");

            // Verify data structure state before delete
            assertThat(set.getCache()).hasSize(4);
            assertThat(set.getScoreCache()).isNotEmpty();

            // Delete
            assertThat(set.deleteAsync().get()).isTrue();

            // Verify both cache structures are completely empty
            assertThat(set.getCache()).isEmpty();
            assertThat(set.getScoreCache()).isEmpty();

            // Verify Redis is empty
            assertThat(redis(name).size()).isZero();

            // Verify we can add new data after delete
            set.add(10.0, "z");
            assertThat(set.getCache()).containsEntry("z", 10.0);
            assertConsistent(set, redis(name));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testDeleteAsync_MultipleDeletesHaveNoEffect() throws ExecutionException, InterruptedException {
        String name = "t:deleteAsync:multiDelete";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            awaitCacheSize(s2, 1);

            // First delete
            assertThat(s1.deleteAsync().get()).isTrue();
            awaitCacheSize(s2, 0);

            // Second delete (should succeed but be no-op)
            assertThat(s1.deleteAsync().get()).isFalse();
            assertThat(s1.getCache()).isEmpty();
            assertThat(s2.getCache()).isEmpty();
            assertThat(redis(name).size()).isZero();
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testDeleteAsync_BothInstancesCanDelete() {
        String name = "t:deleteAsync:bothDelete";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            // Add via s1
            s1.add(1.0, "a");
            awaitCacheSize(s2, 1);

            // Delete via s2
            assertThat(s2.deleteAsync().get()).isTrue();

            // Both should be empty
            awaitCacheSize(s1, 0);
            assertThat(s1.getCache()).isEmpty();
            assertThat(s2.getCache()).isEmpty();
            assertThat(redis(name).size()).isZero();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }


    // ─────────────────────────────────────────────────────────────
    //  3. Conditional adds
    // ─────────────────────────────────────────────────────────────

    @Test
    void testAddIfAbsent() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addIfAbsent");
        try {
            assertThat(set.addIfAbsent(1.0, "a")).isTrue();
            assertThat(set.addIfAbsent(9.0, "a")).isFalse();  // already present
            assertThat(set.getScore("a")).isEqualTo(1.0);
            assertConsistent(set, redis("t:addIfAbsent"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddIfExists() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addIfExists");
        try {
            assertThat(set.addIfExists(9.0, "missing")).isFalse();
            set.add(1.0, "a");
            assertThat(set.addIfExists(9.0, "a")).isTrue();
            assertThat(set.getScore("a")).isEqualTo(9.0);
            assertConsistent(set, redis("t:addIfExists"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddIfLess() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addIfLess");
        try {
            set.add(10.0, "a");
            assertThat(set.addIfLess(5.0, "a")).isTrue();     // 5 < 10 → update
            assertThat(set.getScore("a")).isEqualTo(5.0);
            assertThat(set.addIfLess(20.0, "a")).isFalse();   // 20 > 5 → no update
            assertThat(set.getScore("a")).isEqualTo(5.0);
            assertConsistent(set, redis("t:addIfLess"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddIfGreater() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addIfGreater");
        try {
            set.add(5.0, "a");
            assertThat(set.addIfGreater(10.0, "a")).isTrue();  // 10 > 5 → update
            assertThat(set.getScore("a")).isEqualTo(10.0);
            assertThat(set.addIfGreater(3.0, "a")).isFalse();  // 3 < 10 → no update
            assertThat(set.getScore("a")).isEqualTo(10.0);
            assertConsistent(set, redis("t:addIfGreater"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  4. Bulk conditional adds
    // ─────────────────────────────────────────────────────────────

    @Test
    void testAddAllIfAbsent() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAllIfAbsent");
        try {
            set.add(1.0, "existing");
            Map<String, Double> input = new LinkedHashMap<>();
            input.put("existing", 99.0);  // already present – must be ignored
            input.put("new1", 2.0);
            input.put("new2", 3.0);
            assertThat(set.addAllIfAbsent(input)).isEqualTo(2);
            assertThat(set.getScore("existing")).isEqualTo(1.0);
            assertThat(set.getScore("new1")).isEqualTo(2.0);
            assertThat(set.getScore("new2")).isEqualTo(3.0);
            assertConsistent(set, redis("t:addAllIfAbsent"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAllIfExist() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAllIfExist");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            Map<String, Double> input = new LinkedHashMap<>();
            input.put("a", 10.0);
            input.put("b", 20.0);
            input.put("ghost", 99.0);
            assertThat(set.addAllIfExist(input)).isEqualTo(2);
            assertThat(set.getScore("a")).isEqualTo(10.0);
            assertThat(set.getScore("b")).isEqualTo(20.0);
            assertThat(set.getScore("ghost")).isNull();
            assertConsistent(set, redis("t:addAllIfExist"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAllIfGreater() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAllIfGreater");
        try {
            set.add(10.0, "a");
            set.add(20.0, "b");
            Map<String, Double> input = new LinkedHashMap<>();
            input.put("a", 15.0);  // 15 > 10 → update
            input.put("b", 5.0);   // 5 < 20 → skip
            input.put("c", 1.0);   // new → insert
            assertThat(set.addAllIfGreater(input)).isEqualTo(2);  // a and c
            assertThat(set.getScore("a")).isEqualTo(15.0);
            assertThat(set.getScore("b")).isEqualTo(20.0);
            assertThat(set.getScore("c")).isEqualTo(1.0);
            assertConsistent(set, redis("t:addAllIfGreater"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAllIfLess() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAllIfLess");
        try {
            set.add(10.0, "a");
            set.add(20.0, "b");
            Map<String, Double> input = new LinkedHashMap<>();
            input.put("a", 5.0);   // 5 < 10 → update
            input.put("b", 25.0);  // 25 > 20 → skip
            input.put("c", 3.0);   // new → insert
            assertThat(set.addAllIfLess(input)).isEqualTo(2);  // a and c
            assertThat(set.getScore("a")).isEqualTo(5.0);
            assertThat(set.getScore("b")).isEqualTo(20.0);
            assertThat(set.getScore("c")).isEqualTo(3.0);
            assertConsistent(set, redis("t:addAllIfLess"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  5. addScore / addAndGetRank / addAndGetRevRank / addScoreAndGetRank …
    // ─────────────────────────────────────────────────────────────

    @Test
    void testAddScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addScore");
        try {
            set.add(1.0, "a");
            assertThat(set.addScore("a", 4.0)).isEqualTo(5.0);
            assertThat(set.getScore("a")).isEqualTo(5.0);
            // element not yet present → starts from 0
            assertThat(set.addScore("new", 3.0)).isEqualTo(3.0);
            assertConsistent(set, redis("t:addScore"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAndGetRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAndGetRank");
        try {
            assertThat(set.addAndGetRank(1.0, "a")).isZero();
            assertThat(set.addAndGetRank(3.0, "c")).isEqualTo(1);
            assertThat(set.addAndGetRank(2.0, "b")).isEqualTo(1);
            assertConsistent(set, redis("t:addAndGetRank"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddAndGetRevRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addAndGetRevRank");
        try {
            assertThat(set.addAndGetRevRank(1.0, "a")).isZero();
            assertThat(set.addAndGetRevRank(3.0, "c")).isZero();  // now highest
            assertThat(set.addAndGetRevRank(2.0, "b")).isEqualTo(1);
            assertConsistent(set, redis("t:addAndGetRevRank"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddScoreAndGetRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addScoreGetRank");
        try {
            set.add(1.0, "a");
            set.add(5.0, "b");
            // addScoreAndGetRank("a", 2) → new score 3, rank 0
            assertThat(set.addScoreAndGetRank("a", 2.0)).isZero();
            assertThat(set.getScore("a")).isEqualTo(3.0);
            assertConsistent(set, redis("t:addScoreGetRank"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testAddScoreAndGetRevRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:addScoreGetRevRank");
        try {
            set.add(1.0, "a");
            set.add(5.0, "b");
            // addScoreAndGetRevRank("b", 2) → new score 7, revRank 0
            assertThat(set.addScoreAndGetRevRank("b", 2.0)).isZero();
            assertThat(set.getScore("b")).isEqualTo(7.0);
            assertConsistent(set, redis("t:addScoreGetRevRank"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  6. rank / revRank
    // ─────────────────────────────────────────────────────────────

    @Test
    void testRankAndRevRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:rankRevRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.rank("a")).isZero();
            assertThat(set.rank("c")).isEqualTo(2);
            assertThat(set.rank("missing")).isNull();
            assertThat(set.revRank("c")).isZero();
            assertThat(set.revRank("a")).isEqualTo(2);
            assertThat(set.revRank("missing")).isNull();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testRevRankCollection() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:revRankCol");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            List<Integer> ranks = set.revRank(Arrays.asList("c", "a", "missing"));
            assertThat(ranks).containsExactly(0, 2, null);
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  7. First / Last score and values
    // ─────────────────────────────────────────────────────────────

    @Test
    void testFirstAndLastScoreAndValue() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:firstLast");
        try {
            assertThat(set.firstScore()).isNull();
            assertThat(set.lastScore()).isNull();
            assertThat(set.first()).isNull();
            assertThat(set.last()).isNull();
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.firstScore()).isEqualTo(1.0);
            assertThat(set.lastScore()).isEqualTo(3.0);
            assertThat(set.first()).isEqualTo("a");
            assertThat(set.last()).isEqualTo("c");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testFirstAndLastEntry() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:firstLastEntry");
        try {
            assertThat(set.firstEntry()).isNull();
            assertThat(set.lastEntry()).isNull();
            set.add(1.0, "a");
            set.add(3.0, "c");
            assertThat(set.firstEntry()).isEqualTo(new ScoredEntry<>(1.0, "a"));
            assertThat(set.lastEntry()).isEqualTo(new ScoredEntry<>(3.0, "c"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  8. count / valueRange / entryRange (by score)
    // ─────────────────────────────────────────────────────────────

    @Test
    void testCount() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:count");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            set.add(4.0, "d");
            assertThat(set.count(1.0, true, 3.0, false)).isEqualTo(2); // [1,3)
            assertThat(set.count(1.0, true, 3.0, true)).isEqualTo(3); // [1,3]
            assertThat(set.count(5.0, true, 10.0, true)).isZero(); // out of range
        } finally {
            set.destroy();
        }
    }

    @Test
    void testValueRangeByScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:valueRangeByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.valueRange(1.0, true, 2.0, true)).containsExactly("a", "b");
            assertThat(set.valueRange(1.0, false, 3.0, false)).containsExactly("b");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testEntryRangeByScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:entryRangeByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            Collection<ScoredEntry<String>> r = set.entryRange(1.0, true, 2.0, true);
            assertThat(r).containsExactly(new ScoredEntry<>(1.0, "a"), new ScoredEntry<>(2.0, "b"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testEntryRangeByScoreEmptyOnInvalidRange() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:entryRangeInvalid");
        try {
            set.add(1.0, "a");
            assertThat(set.entryRange(5.0, true, 1.0, true)).isEmpty();
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  9. valueRange / entryRange by rank (index)
    // ─────────────────────────────────────────────────────────────

    @Test
    void testValueRangeByRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:valueRangeByRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.valueRange(0, 1)).containsExactly("a", "b");
            assertThat(set.valueRange(0, -1)).containsExactly("a", "b", "c");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testValueRangeReversedByRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:valueRangeRevByRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.valueRangeReversed(0, 1)).containsExactly("c", "b");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testValueRangeReversedByScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:valueRangeRevByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.valueRangeReversed(1.0, true, 3.0, true)).containsExactly("c", "b", "a");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testEntryRangeByRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:entryRangeByRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.entryRange(0, 1))
                    .containsExactly(new ScoredEntry<>(1.0, "a"), new ScoredEntry<>(2.0, "b"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testEntryRangeReversedByRank() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:entryRangeRevByRank");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.entryRangeReversed(0, 1))
                    .containsExactly(new ScoredEntry<>(3.0, "c"), new ScoredEntry<>(2.0, "b"));
        } finally {
            set.destroy();
        }
    }

    @Test
    void testEntryRangeReversedByScore() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:entryRangeRevByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.entryRangeReversed(1.0, true, 3.0, true))
                    .containsExactly(
                            new ScoredEntry<>(3.0, "c"),
                            new ScoredEntry<>(2.0, "b"),
                            new ScoredEntry<>(1.0, "a"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  10. poll operations (localOnlySet for deterministic ordering)
    // ─────────────────────────────────────────────────────────────

    @Test
    void testPollFirstSingle() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollFirst1");
        try {
            assertThat(set.pollFirst()).isNull();
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.pollFirst()).isEqualTo("a");
            assertThat(set.size()).isEqualTo(2);
            assertThat(set.getCache()).doesNotContainKey("a");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollLastSingle() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollLast1");
        try {
            assertThat(set.pollLast()).isNull();
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.pollLast()).isEqualTo("c");
            assertThat(set.size()).isEqualTo(2);
            assertThat(set.getCache()).doesNotContainKey("c");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollFirstCount() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollFirstN");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            Collection<String> polled = set.pollFirst(2);
            assertThat(polled).containsExactly("a", "b");
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollLastCount() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollLastN");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            Collection<String> polled = set.pollLast(2);
            assertThat(polled).containsExactly("c", "b");
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollFirstEntry() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollFirstEntry");
        try {
            assertThat(set.pollFirstEntry()).isNull();
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.pollFirstEntry()).isEqualTo(new ScoredEntry<>(1.0, "a"));
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollLastEntry() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollLastEntry");
        try {
            assertThat(set.pollLastEntry()).isNull();
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.pollLastEntry()).isEqualTo(new ScoredEntry<>(2.0, "b"));
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollFirstEntries() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollFirstEntries");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            List<ScoredEntry<String>> polled = set.pollFirstEntries(2);
            assertThat(polled).containsExactly(new ScoredEntry<>(1.0, "a"), new ScoredEntry<>(2.0, "b"));
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollLastEntries() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollLastEntries");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            List<ScoredEntry<String>> polled = set.pollLastEntries(2);
            assertThat(polled).containsExactly(new ScoredEntry<>(3.0, "c"), new ScoredEntry<>(2.0, "b"));
            assertThat(set.size()).isEqualTo(1);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPollZeroCount() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:pollZero");
        try {
            set.add(1.0, "a");
            assertTrue(set.pollFirst(0).isEmpty());
            assertTrue(set.pollLast(0).isEmpty());
            assertTrue(set.pollFirstEntries(0).isEmpty());
            assertTrue(set.pollLastEntries(0).isEmpty());
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  11. replace
    // ─────────────────────────────────────────────────────────────

    @Test
    void testReplace() {
        RLocalCachedScoredSortedSet<String> set = createSet("t:replace");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            assertThat(set.replace("a", "newA")).isTrue();
            assertThat(set.getScore("newA")).isEqualTo(1.0);
            assertThat(set.getScore("a")).isNull();
            assertThat(set.replace("missing", "x")).isFalse();
            assertConsistent(set, redis("t:replace"));
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  12. LocalOnly mode
    // ─────────────────────────────────────────────────────────────

    @Test
    void testLocalOnlyModeDoesNotWriteToRedis() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnly");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            // Redis key must not exist
            assertThat(redis("t:localOnly").size()).isZero();
            assertThat(set.size()).isEqualTo(2);
            assertThat(set.getCache()).containsEntry("a", 1.0).containsEntry("b", 2.0);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyReadOps() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyRead");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.getScore("a")).isEqualTo(1.0);
            assertThat(set.rank("b")).isEqualTo(1);
            assertThat(set.revRank("b")).isEqualTo(1);
            assertThat(set.first()).isEqualTo("a");
            assertThat(set.last()).isEqualTo("c");
            assertThat(set.firstScore()).isEqualTo(1.0);
            assertThat(set.lastScore()).isEqualTo(3.0);
            assertThat(set.count(1.0, true, 2.0, true)).isEqualTo(2);
            assertThat(set.readAll()).containsExactlyInAnyOrder("a", "b", "c");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddIfAbsent() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyAddIfAbsent");
        try {
            assertThat(set.addIfAbsent(1.0, "a")).isTrue();
            assertThat(set.addIfAbsent(9.0, "a")).isFalse();
            assertThat(set.getScore("a")).isEqualTo(1.0);
            assertThat(redis("t:localOnlyAddIfAbsent").size()).isZero();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddIfExists() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyAddIfExists");
        try {
            assertThat(set.addIfExists(9.0, "missing")).isFalse();
            set.add(1.0, "a");
            assertThat(set.addIfExists(5.0, "a")).isTrue();
            assertThat(set.getScore("a")).isEqualTo(5.0);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddIfLessAndGreater() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyAddIfLessGreater");
        try {
            set.add(5.0, "a");
            assertThat(set.addIfLess(3.0, "a")).isTrue();
            assertThat(set.getScore("a")).isEqualTo(3.0);
            assertThat(set.addIfGreater(10.0, "a")).isTrue();
            assertThat(set.getScore("a")).isEqualTo(10.0);
            assertThat(set.addIfGreater(2.0, "a")).isFalse();
            assertThat(set.getScore("a")).isEqualTo(10.0);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyRemoveRangeByScore() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyRmByScore");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.removeRangeByScore(1.0, true, 2.0, true)).isEqualTo(2);
            assertThat(set.size()).isEqualTo(1);
            assertThat(set.getCache()).containsOnlyKeys("c");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddAllConditionals() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyBulkCond");
        try {
            set.add(10.0, "a");
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("a", 99.0);
            m.put("b", 2.0);
            assertThat(set.addAllIfAbsent(m)).isEqualTo(1);
            assertThat(set.getScore("a")).isEqualTo(10.0);
            assertThat(set.getScore("b")).isEqualTo(2.0);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddScore() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyAddScore");
        try {
            set.add(5.0, "a");
            assertThat(set.addScore("a", 3.0)).isEqualTo(8.0);
            assertThat(set.getScore("a")).isEqualTo(8.0);
            assertThat(redis("t:localOnlyAddScore").size()).isZero();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyAddAndGetRankRevRank() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyRank");
        try {
            assertThat(set.addAndGetRank(1.0, "a")).isZero();
            assertThat(set.addAndGetRevRank(3.0, "c")).isZero();
            assertThat(set.addScoreAndGetRank("a", 1.5)).isZero(); // new score 2.5
            assertThat(set.addScoreAndGetRevRank("c", 0.5)).isZero(); // new score 3.5
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyRetainAllAndDelete() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyRetain");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            set.retainAll(Collections.singletonList("b"));
            assertThat(set.getCache()).containsOnlyKeys("b");
            assertThat(set.delete()).isTrue();
            assertThat(set.size()).isZero();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyRemoveAll() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyRemoveAll");
        try {
            set.add(1.0, "a");
            set.add(2.0, "b");
            set.add(3.0, "c");
            assertThat(set.removeAll(Arrays.asList("a", "c"))).isTrue();
            assertThat(set.getCache()).containsOnlyKeys("b");
        } finally {
            set.destroy();
        }
    }

    @Test
    void testLocalOnlyReplace() {
        RLocalCachedScoredSortedSet<String> set = createLocalOnlySet("t:localOnlyReplace");
        try {
            set.add(5.0, "old");
            assertThat(set.replace("old", "newVal")).isTrue();
            assertThat(set.getScore("newVal")).isEqualTo(5.0);
            assertThat(set.getScore("old")).isNull();
            assertThat(redis("t:localOnlyReplace").size()).isZero();
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  13. Preload from Redis
    // ─────────────────────────────────────────────────────────────

    @Test
    void testPreloadPopulatesCache() {
        String name = "t:preload";
        redis(name).add(1.0, "a");
        redis(name).add(2.0, "b");
        redis(name).add(3.0, "c");
        RLocalCachedScoredSortedSet<String> set = createPreloadSet(name);
        try {
            assertThat(set.getCache()).containsEntry("a", 1.0)
                    .containsEntry("b", 2.0)
                    .containsEntry("c", 3.0);
            assertThat(set.size()).isEqualTo(3);
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPreloadOnEmptySet() {
        RLocalCachedScoredSortedSet<String> set = createPreloadSet("t:preloadEmpty");
        try {
            assertThat(set.getCache()).isEmpty();
            assertThat(set.getScoreCache()).isEmpty();
        } finally {
            set.destroy();
        }
    }

    @Test
    void testPreloadCacheManualRefresh() {
        String name = "t:preloadRefresh";
        redis(name).add(5.0, "x");
        redis(name).add(10.0, "y");
        RLocalCachedScoredSortedSet<String> set = createPreloadSet(name);
        try {
            assertThat(set.getCache()).hasSize(2);
            // Add more directly to Redis, then re-preload
            redis(name).add(15.0, "z");
            set.preloadCache();
            assertThat(set.getCache()).hasSize(3).containsKey("z");
        } finally {
            set.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  14. Cross-instance synchronization
    // ─────────────────────────────────────────────────────────────

    @Test
    void testCrossInstanceSync_AddAndRemove() {
        String name = "t:sync:addRemove";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "one");
            awaitCacheContainsEntry(s2, "one", 1.0);

            s2.addScore("one", 2.0);
            awaitCacheContainsEntry(s1, "one", 3.0);

            s1.add(2.0, "two");
            awaitCacheContainsEntry(s2, "two", 2.0);

            s2.remove("two");
            awaitCacheNotContainsKey(s1, "two");
            awaitCacheNotContainsKey(s2, "two");

            assertConsistent(s1, redis(name));
            assertConsistent(s2, redis(name));
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_AddAll() {
        String name = "t:sync:addAll";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            Map<String, Double> batch = new LinkedHashMap<>();
            batch.put("x", 1.0);
            batch.put("y", 2.0);
            batch.put("z", 3.0);
            s1.addAll(batch);
            awaitCacheSize(s2, 3);
            assertThat(s2.getCache()).containsEntry("x", 1.0)
                    .containsEntry("y", 2.0)
                    .containsEntry("z", 3.0);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_RemoveRangeByScore() {
        String name = "t:sync:rmByScore";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            awaitCacheSize(s2, 3);
            s1.removeRangeByScore(1.0, true, 2.0, true);
            awaitCacheNotContainsKey(s2, "a");
            awaitCacheNotContainsKey(s2, "b");
            assertThat(s2.getCache()).containsKey("c");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_RemoveRangeByRank() {
        String name = "t:sync:rmByRank";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            s1.add(4.0, "d");
            awaitCacheSize(s2, 4);
            s1.removeRangeByRank(0, 1);
            awaitCacheNotContainsKey(s2, "a");
            awaitCacheNotContainsKey(s2, "b");
            assertThat(s2.getCache()).containsKey("c").containsKey("d");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_RemoveAll() {
        String name = "t:sync:removeAll";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            awaitCacheSize(s2, 3);
            s1.removeAll(Arrays.asList("a", "c"));
            awaitCacheNotContainsKey(s2, "a");
            awaitCacheNotContainsKey(s2, "c");
            assertThat(s2.getCache()).containsKey("b");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_RetainAll() {
        String name = "t:sync:retainAll";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            awaitCacheSize(s2, 3);
            s1.retainAll(Collections.singletonList("b"));
            awaitCacheSize(s2, 1);
            assertThat(s2.getCache()).containsOnlyKeys("b");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_Replace() {
        String name = "t:sync:replace";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(5.0, "old");
            awaitCacheContainsEntry(s2, "old", 5.0);
            s1.replace("old", "newVal");
            awaitCacheContainsEntry(s2, "newVal", 5.0);
            awaitCacheNotContainsKey(s2, "old");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_AddScore() {
        String name = "t:sync:addScore";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            awaitCacheContainsEntry(s2, "a", 1.0);
            s1.addScore("a", 4.0);
            awaitCacheContainsEntry(s2, "a", 5.0);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_PollFirst() {
        String name = "t:sync:pollFirst";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            awaitCacheSize(s2, 3);
            assertThat(s1.pollFirst()).isEqualTo("a");
            awaitCacheNotContainsKey(s2, "a");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_PollFirstCount() {
        String name = "t:sync:pollFirstN";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "a");
            s1.add(2.0, "b");
            s1.add(3.0, "c");
            awaitCacheSize(s2, 3);
            s1.pollFirst(2);
            awaitCacheSize(s2, 1);
            assertThat(s2.getCache()).containsKey("c");
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_AddAllIfAbsent() {
        String name = "t:sync:addAllIfAbsent";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(1.0, "existing");
            awaitCacheContainsEntry(s2, "existing", 1.0);
            Map<String, Double> toAdd = new LinkedHashMap<>();
            toAdd.put("existing", 99.0);   // must be skipped
            toAdd.put("newKey", 5.0);
            s1.addAllIfAbsent(toAdd);
            awaitCacheContainsEntry(s2, "newKey", 5.0);
            assertThat(s2.getCache()).containsEntry("existing", 1.0);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_AddAndGetRank() {
        String name = "t:sync:addGetRank";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(10.0, "high");
            awaitCacheContainsEntry(s2, "high", 10.0);
            // addAndGetRank writes to both caches via broadcast
            s1.addAndGetRank(1.0, "low");
            awaitCacheContainsEntry(s2, "low", 1.0);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    @Test
    void testCrossInstanceSync_AddAllIfGreater() {
        String name = "t:sync:addAllIfGreater";
        RedissonClient c2 = createInstance();
        RLocalCachedScoredSortedSet<String> s1 = createSet(redisson, name);
        RLocalCachedScoredSortedSet<String> s2 = createSet(c2, name);
        try {
            s1.add(10.0, "a");
            awaitCacheContainsEntry(s2, "a", 10.0);
            Map<String, Double> update = new LinkedHashMap<>();
            update.put("a", 20.0);  // 20 > 10 → update
            s1.addAllIfGreater(update);
            awaitCacheContainsEntry(s2, "a", 20.0);
        } finally {
            s1.destroy();
            s2.destroy();
            c2.shutdown();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  15. Awaitility helpers
    // ─────────────────────────────────────────────────────────────

    private static final Duration SYNC_TIMEOUT = Duration.ofMillis(500);

    private void awaitCacheContainsEntry(RLocalCachedScoredSortedSet<String> set, String key, double val) {
        Awaitility.await().atMost(SYNC_TIMEOUT).untilAsserted(() ->
                assertThat(set.getCache()).containsEntry(key, val));
    }

    private void awaitCacheNotContainsKey(RLocalCachedScoredSortedSet<String> set, String key) {
        Awaitility.await().atMost(SYNC_TIMEOUT).untilAsserted(() ->
                assertThat(set.getCache()).doesNotContainKey(key));
    }

    private void awaitCacheSize(RLocalCachedScoredSortedSet<String> set, int size) {
        Awaitility.await().atMost(SYNC_TIMEOUT).untilAsserted(() ->
                assertThat(set.getCache()).hasSize(size));
    }
}
