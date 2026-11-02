package org.redisson;

import io.reactivex.rxjava3.core.Maybe;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.listener.SetMemberClearExpireListener;
import org.redisson.api.listener.SetMemberExpiredListener;
import org.redisson.api.options.PlainOptions;
import org.redisson.api.set.SetAddArgs;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonSetCacheNativeTest extends RedisDockerTest {

    // Set member expiration (redis/redis#15938) ships in 8.12, see createRedis812.
    static RedissonClient redisson;
    static GenericContainer<?> redis;

    @BeforeAll
    static void beforeAll() {
        redis = createRedis812();
        redis.start();

        redisson = Redisson.create(createConfig(redis));
    }

    @AfterAll
    static void afterAll() {
        redisson.shutdown();
        redis.stop();
    }

    private static void testWithEvents(String events, Consumer<RedissonClient> callback) {
        GenericContainer<?> container = createRedis812(NOTIFY_KEYSPACE_EVENTS, events);
        container.start();
        RedissonClient client = Redisson.create(createConfig(container));
        try {
            callback.accept(client);
        } finally {
            client.shutdown();
            container.stop();
        }
    }

    @Test
    public void testAddWithTtl() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddWithTtl", StringCodec.INSTANCE);

        assertThat(set.add("a", Duration.ofSeconds(10))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(9000L, 10000L);

        // a second add reports "already present" and still replaces the time to live
        assertThat(set.add("a", Duration.ofSeconds(100))).isFalse();
        assertThat(set.remainTimeToLive("a")).isBetween(99000L, 100000L);

        assertThat(set.add("b", Duration.ofMillis(300))).isTrue();
        assertThat(set.contains("b")).isTrue();
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !set.contains("b"));
        assertThat(set.readAll()).containsExactly("a");

        // an expired value counts as absent, so adding it again is an add
        assertThat(set.add("b", Duration.ofSeconds(10))).isTrue();
    }

    @Test
    public void testAddZeroTtlStoresInfinitely() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddZeroTtl", StringCodec.INSTANCE);

        assertThat(set.add("a", Duration.ZERO)).isTrue();
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);

        set.add("b", Duration.ofSeconds(10));
        assertThat(set.add("b", Duration.ZERO)).isFalse();
        assertThat(set.remainTimeToLive("b")).isEqualTo(-1);

        assertThatThrownBy(() -> set.add("c", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(set.contains("c")).isFalse();
    }

    @Test
    public void testAddInstant() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddInstant", StringCodec.INSTANCE);
        Instant time = Instant.now().plusSeconds(60);

        assertThat(set.add("a", time)).isTrue();
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());
        assertThat(set.add("a", time.plusSeconds(10))).isFalse();
        assertThat(set.getExpireTime("a")).isEqualTo(time.plusSeconds(10).toEpochMilli());

        Instant past = Instant.now().minusSeconds(10);
        assertThat(set.add("b", past)).isFalse();
        assertThat(set.contains("b")).isFalse();

        // a date in the past removes the value it would have updated
        assertThat(set.add("a", past)).isFalse();
        assertThat(set.contains("a")).isFalse();
        assertThat(set.isExists()).isFalse();
    }

    @Test
    public void testAddIfAbsent() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddIfAbsent", StringCodec.INSTANCE);

        assertThat(set.addIfAbsent("a", Duration.ofSeconds(10))).isTrue();
        assertThat(set.addIfAbsent("a", Duration.ofSeconds(100))).isFalse();
        assertThat(set.remainTimeToLive("a")).isBetween(9000L, 10000L);

        assertThat(set.addIfAbsent("b", Duration.ZERO)).isTrue();
        assertThat(set.remainTimeToLive("b")).isEqualTo(-1);

        Instant time = Instant.now().plusSeconds(30);
        assertThat(set.addIfAbsent("c", time)).isTrue();
        assertThat(set.getExpireTime("c")).isEqualTo(time.toEpochMilli());
        assertThat(set.addIfAbsent("c", time.plusSeconds(30))).isFalse();
        assertThat(set.getExpireTime("c")).isEqualTo(time.toEpochMilli());

        assertThat(set.addIfAbsent("d", Instant.now().minusSeconds(1))).isFalse();
        assertThat(set.contains("d")).isFalse();
    }

    @Test
    public void testAddIfExists() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddIfExists", StringCodec.INSTANCE);

        assertThat(set.addIfExists("a", Duration.ofSeconds(10))).isFalse();
        assertThat(set.contains("a")).isFalse();

        set.add("a");
        assertThat(set.addIfExists("a", Duration.ofSeconds(10))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(9000L, 10000L);

        assertThat(set.addIfExists("a", Duration.ZERO)).isTrue();
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);
        // nothing left to change: the value has no time to live already
        assertThat(set.addIfExists("a", Duration.ZERO)).isFalse();

        Instant time = Instant.now().plusSeconds(30);
        assertThat(set.addIfExists("a", time)).isTrue();
        assertThat(set.addIfExists("a", time)).isFalse();
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());

        assertThat(set.addIfExists("a", Instant.now().minusSeconds(1))).isTrue();
        assertThat(set.contains("a")).isFalse();
    }

    @Test
    public void testAddAllArgs() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddAllArgs", StringCodec.INSTANCE);

        assertThat(set.addAll(SetAddArgs.values(List.of("a", "b"))
                                        .timeToLive(Duration.ofSeconds(10)))).isTrue();
        assertThat(set.remainTimeToLive(Set.of("a", "b"))).allSatisfy((v, ttl) ->
                assertThat(ttl).isBetween(9000L, 10000L));

        // without an expiration option existing values lose theirs
        assertThat(set.addAll(SetAddArgs.values(List.of("a", "c")))).isTrue();
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);
        assertThat(set.remainTimeToLive("b")).isBetween(1L, 10000L);
        assertThat(set.remainTimeToLive("c")).isEqualTo(-1);

        assertThat(set.addAll(SetAddArgs.values(List.of("b", "d")).keepTTL())).isTrue();
        assertThat(set.remainTimeToLive("b")).isBetween(1L, 10000L);
        assertThat(set.remainTimeToLive("d")).isEqualTo(-1);
        assertThat(set.addAll(SetAddArgs.values(List.of("b", "d")).keepTTL())).isFalse();

        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.addAll(SetAddArgs.values(List.of("c", "d")).expireAt(time))).isTrue();
        assertThat(set.getExpireTime(Set.of("c", "d"))).containsOnly(
                Map.entry("c", time.toEpochMilli()), Map.entry("d", time.toEpochMilli()));

        assertThat(set.addAll(SetAddArgs.<String>values(List.of()).timeToLive(Duration.ofSeconds(1)))).isFalse();
        assertThat(set.readAll()).containsOnly("a", "b", "c", "d");
    }

    @Test
    public void testAddArgsLastCallWins() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddArgsLastCallWins", StringCodec.INSTANCE);
        Instant time = Instant.now().plusSeconds(60);

        set.addAll(SetAddArgs.values(List.of("a")).timeToLive(Duration.ofSeconds(5)).expireAt(time));
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());

        set.addAll(SetAddArgs.values(List.of("a")).keepTTL().timeToLive(Duration.ofSeconds(100)));
        assertThat(set.remainTimeToLive("a")).isBetween(99000L, 100000L);

        set.addAll(SetAddArgs.values(List.of("a")).expireAt(time).timeToLive(Duration.ZERO));
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);

        assertThatThrownBy(() -> SetAddArgs.values(List.of("a")).timeToLive(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SetAddArgs.values(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testAddIfAllAbsent() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddIfAllAbsent", StringCodec.INSTANCE);
        set.add("a");

        assertThat(set.addIfAllAbsent(SetAddArgs.values(List.of("a", "b"))
                                                .timeToLive(Duration.ofSeconds(10)))).isFalse();
        assertThat(set.readAll()).containsOnly("a");
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);

        assertThat(set.addIfAllAbsent(SetAddArgs.values(List.of("b", "c"))
                                                .timeToLive(Duration.ofSeconds(10)))).isTrue();
        assertThat(set.readAll()).containsOnly("a", "b", "c");
        assertThat(set.remainTimeToLive("c")).isBetween(9000L, 10000L);
    }

    @Test
    public void testAddIfAllExist() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAddIfAllExist", StringCodec.INSTANCE);
        set.add("a");
        set.add("b");

        assertThat(set.addIfAllExist(SetAddArgs.values(List.of("a", "c"))
                                               .timeToLive(Duration.ofSeconds(10)))).isFalse();
        assertThat(set.readAll()).containsOnly("a", "b");
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);

        assertThat(set.addIfAllExist(SetAddArgs.values(List.of("a", "b"))
                                               .timeToLive(Duration.ofSeconds(10)))).isTrue();
        assertThat(set.remainTimeToLive(Set.of("a", "b"))).allSatisfy((v, ttl) ->
                assertThat(ttl).isBetween(9000L, 10000L));

        assertThat(set.addIfAllExist(SetAddArgs.values(List.of("a", "b")).expireAt(Instant.now().minusSeconds(1))))
                .isTrue();
        assertThat(set.isExists()).isFalse();
    }

    @Test
    public void testExpireValue() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValue", StringCodec.INSTANCE);

        assertThat(set.expireValue("a", Duration.ofSeconds(10))).isFalse();

        set.add("a");
        set.add("b");
        assertThat(set.expireValue("a", Duration.ofSeconds(10))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(9000L, 10000L);

        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.expireValue("a", time)).isTrue();
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());

        // unlike adding, a zero time to live is an expiration that has already passed
        assertThat(set.expireValue("a", Duration.ZERO)).isTrue();
        assertThat(set.contains("a")).isFalse();
        assertThat(set.expireValue("b", Instant.now().minusSeconds(1))).isTrue();
        assertThat(set.isExists()).isFalse();

        assertThatThrownBy(() -> set.expireValue("a", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testExpireValueIfSet() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValueIfSet", StringCodec.INSTANCE);
        set.add("a");
        set.add("b", Duration.ofSeconds(10));

        assertThat(set.expireValueIfSet("a", Duration.ofSeconds(20))).isFalse();
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);
        assertThat(set.expireValueIfSet("b", Duration.ofSeconds(20))).isTrue();
        assertThat(set.remainTimeToLive("b")).isBetween(19000L, 20000L);

        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.expireValueIfSet("a", time)).isFalse();
        assertThat(set.expireValueIfSet("b", time)).isTrue();
        assertThat(set.getExpireTime("b")).isEqualTo(time.toEpochMilli());
        assertThat(set.expireValueIfSet("missing", time)).isFalse();
    }

    @Test
    public void testExpireValueIfNotSet() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValueIfNotSet", StringCodec.INSTANCE);
        set.add("a");
        set.add("b", Duration.ofSeconds(10));

        assertThat(set.expireValueIfNotSet("a", Duration.ofSeconds(20))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(19000L, 20000L);
        assertThat(set.expireValueIfNotSet("b", Duration.ofSeconds(20))).isFalse();
        assertThat(set.remainTimeToLive("b")).isBetween(9000L, 10000L);

        set.add("c");
        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.expireValueIfNotSet("c", time)).isTrue();
        assertThat(set.expireValueIfNotSet("c", time.plusSeconds(1))).isFalse();
        assertThat(set.getExpireTime("c")).isEqualTo(time.toEpochMilli());
    }

    @Test
    public void testExpireValueIfGreater() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValueIfGreater", StringCodec.INSTANCE);
        set.add("a", Duration.ofSeconds(10));
        set.add("noTtl");

        assertThat(set.expireValueIfGreater("a", Duration.ofSeconds(5))).isFalse();
        assertThat(set.expireValueIfGreater("a", Duration.ofSeconds(20))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(19000L, 20000L);
        // a value without expiration never expires, and nothing is greater than that
        assertThat(set.expireValueIfGreater("noTtl", Duration.ofSeconds(20))).isFalse();
        assertThat(set.remainTimeToLive("noTtl")).isEqualTo(-1);

        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.expireValueIfGreater("a", time)).isTrue();
        assertThat(set.expireValueIfGreater("a", time.minusSeconds(10))).isFalse();
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());
    }

    @Test
    public void testExpireValueIfLess() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValueIfLess", StringCodec.INSTANCE);
        set.add("a", Duration.ofSeconds(10));
        set.add("noTtl");

        assertThat(set.expireValueIfLess("a", Duration.ofSeconds(20))).isFalse();
        assertThat(set.expireValueIfLess("a", Duration.ofSeconds(5))).isTrue();
        assertThat(set.remainTimeToLive("a")).isBetween(4000L, 5000L);
        // a value without expiration never expires, so any expiration is less
        assertThat(set.expireValueIfLess("noTtl", Duration.ofSeconds(20))).isTrue();
        assertThat(set.remainTimeToLive("noTtl")).isBetween(19000L, 20000L);

        Instant time = Instant.now().plusSeconds(2);
        assertThat(set.expireValueIfLess("a", time)).isTrue();
        assertThat(set.expireValueIfLess("a", time.plusSeconds(10))).isFalse();
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());
    }

    @Test
    public void testExpireValues() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testExpireValues", StringCodec.INSTANCE);
        set.addAll(List.of("a", "b", "c"));
        set.add("d", Duration.ofSeconds(10));

        assertThat(set.expireValues(Set.of("a", "b", "missing"), Duration.ofSeconds(20))).isEqualTo(2);
        assertThat(set.remainTimeToLive(Set.of("a", "b"))).allSatisfy((v, ttl) ->
                assertThat(ttl).isBetween(19000L, 20000L));
        assertThat(set.expireValues(Collections.emptySet(), Duration.ofSeconds(20))).isZero();
        assertThat(set.expireValues(Collections.emptySet(), Instant.now())).isZero();

        assertThat(set.expireValuesIfSet(Set.of("a", "c", "d"), Duration.ofSeconds(30))).isEqualTo(2);
        assertThat(set.remainTimeToLive("c")).isEqualTo(-1);
        assertThat(set.expireValuesIfNotSet(Set.of("a", "c"), Duration.ofSeconds(40))).isEqualTo(1);
        assertThat(set.remainTimeToLive("c")).isBetween(39000L, 40000L);
        assertThat(set.expireValuesIfGreater(Set.of("a", "c"), Duration.ofSeconds(35))).isEqualTo(1);
        assertThat(set.expireValuesIfLess(Set.of("a", "c"), Duration.ofSeconds(32))).isEqualTo(2);

        Instant time = Instant.now().plusSeconds(60);
        assertThat(set.expireValues(Set.of("a", "b"), time)).isEqualTo(2);
        assertThat(set.expireValuesIfSet(Set.of("a", "b"), time.plusSeconds(1))).isEqualTo(2);
        assertThat(set.expireValuesIfGreater(Set.of("a", "b"), time)).isZero();
        assertThat(set.expireValuesIfLess(Set.of("a", "b"), time)).isEqualTo(2);
        set.clearExpire("c");
        assertThat(set.expireValuesIfNotSet(Set.of("a", "c"), time)).isEqualTo(1);
        assertThat(set.getExpireTime(Set.of("a", "b", "c"))).containsOnly(
                Map.entry("a", time.toEpochMilli()), Map.entry("b", time.toEpochMilli()),
                Map.entry("c", time.toEpochMilli()));

        // removed by a time already passed still counts as applied
        assertThat(set.expireValues(Set.of("a", "b"), Duration.ZERO)).isEqualTo(2);
        assertThat(set.readAll()).containsOnly("c", "d");
        assertThatThrownBy(() -> set.expireValues(Collections.emptySet(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testRemainTimeToLiveAndExpireTime() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testRemainTimeToLive", StringCodec.INSTANCE);
        assertThat(set.remainTimeToLive("a")).isEqualTo(-2);
        assertThat(set.getExpireTime("a")).isEqualTo(-2);

        Instant time = Instant.now().plusSeconds(60);
        set.add("a", time);
        set.add("b");

        assertThat(set.remainTimeToLive("a")).isBetween(59000L, 60000L);
        assertThat(set.remainTimeToLive("b")).isEqualTo(-1);
        assertThat(set.getExpireTime("a")).isEqualTo(time.toEpochMilli());
        assertThat(set.getExpireTime("b")).isEqualTo(-1);

        Map<String, Long> ttls = set.remainTimeToLive(new LinkedHashSet<>(List.of("a", "b", "c")));
        assertThat(ttls).containsOnlyKeys("a", "b", "c");
        assertThat(ttls.get("a")).isBetween(59000L, 60000L);
        assertThat(ttls).containsEntry("b", -1L).containsEntry("c", -2L);

        assertThat(set.getExpireTime(new LinkedHashSet<>(List.of("a", "b", "c")))).containsOnly(
                Map.entry("a", time.toEpochMilli()), Map.entry("b", -1L), Map.entry("c", -2L));
        assertThat(set.remainTimeToLive(Collections.emptySet())).isEmpty();
        assertThat(set.getExpireTime(Collections.emptySet())).isEmpty();
    }

    @Test
    public void testClearExpire() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testClearExpire", StringCodec.INSTANCE);
        set.add("a", Duration.ofSeconds(10));
        set.add("b", Duration.ofSeconds(10));
        set.add("c");
        set.add("d", Duration.ofSeconds(10));

        assertThat(set.clearExpire("a")).isTrue();
        assertThat(set.remainTimeToLive("a")).isEqualTo(-1);
        assertThat(set.clearExpire("a")).isFalse();
        assertThat(set.clearExpire("missing")).isNull();

        // all three replies (-1 no expiration, 1 removed, -2 missing) in one call, interleaved,
        // so a value paired with the wrong position would show up in the result
        Set<String> cleared = set.clearExpire(new LinkedHashSet<>(List.of("c", "b", "missing", "d")));
        assertThat(cleared).containsExactly("b", "d");
        assertThat(set.remainTimeToLive(Set.of("b", "d"))).containsOnly(Map.entry("b", -1L), Map.entry("d", -1L));
        assertThat(set.clearExpire(Set.of("b", "d"))).isEmpty();
        assertThat(set.clearExpire(Collections.emptySet())).isEmpty();

        // the set-level expiration is a different thing and stays where it is
        set.expire(Duration.ofSeconds(100));
        set.clearExpire("a");
        assertThat(set.remainTimeToLive()).isBetween(99000L, 100000L);
    }

    @Test
    public void testInheritedReadsSkipExpiredValues() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testInheritedReads", StringCodec.INSTANCE);
        set.add("keep");
        set.add("gone", Duration.ofMillis(200));

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !set.contains("gone"));
        assertThat(set.readAll()).containsExactly("keep");
        assertThat(set).containsExactly("keep");
        assertThat(set.random()).isEqualTo("keep");
        assertThat(set.containsAll(List.of("keep", "gone"))).isFalse();
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> set.size() == 1);
    }

    @Test
    public void testRetainAllKeepsExpiration() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testRetainAll", StringCodec.INSTANCE);
        set.add("a", Duration.ofSeconds(60));
        set.add("b", Duration.ofSeconds(60));
        set.add("c");

        // RSet.retainAll goes through SINTERSTORE, which carries member expirations over
        assertThat(set.retainAll(List.of("a", "c"))).isTrue();
        assertThat(set.readAll()).containsOnly("a", "c");
        assertThat(set.remainTimeToLive("a")).isBetween(59000L, 60000L);
        assertThat(set.remainTimeToLive("c")).isEqualTo(-1);
    }

    public static class Value implements Serializable {

        private String name;

        public Value() {
        }

        public Value(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Value && ((Value) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }

    @Test
    public void testDefaultCodec() {
        RSetCacheNative<Value> set = redisson.getSetCacheNative("testDefaultCodec");
        Value a = new Value("a");
        Value b = new Value("b");

        assertThat(set.add(a, Duration.ofSeconds(10))).isTrue();
        assertThat(set.addAll(SetAddArgs.values(List.of(b)).timeToLive(Duration.ofSeconds(20)))).isTrue();
        assertThat(set.readAll()).containsOnly(a, b);

        Map<Value, Long> ttls = set.remainTimeToLive(Set.of(a, b));
        assertThat(ttls.get(a)).isBetween(9000L, 10000L);
        assertThat(ttls.get(b)).isBetween(19000L, 20000L);
        assertThat(set.clearExpire(Set.of(a))).containsExactly(a);
        assertThat(set.expireValues(Set.of(a, b), Duration.ofSeconds(30))).isEqualTo(2);
    }

    @Test
    public void testPlainOptions() {
        RSetCacheNative<Integer> set = redisson.getSetCacheNative(PlainOptions.name("testPlainOptions")
                                                                               .codec(IntegerCodec.INSTANCE));
        set.add(1, Duration.ofSeconds(10));
        assertThat(set.remainTimeToLive(1)).isBetween(9000L, 10000L);
        assertThat(redisson.getSet("testPlainOptions", IntegerCodec.INSTANCE).readAll()).containsOnly(1);
    }

    @Test
    public void testAsync() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testAsync", StringCodec.INSTANCE);

        assertThat(set.addAsync("a", Duration.ofSeconds(10)).toCompletableFuture().join()).isTrue();
        assertThat(set.addIfAbsentAsync("a", Duration.ofSeconds(10)).toCompletableFuture().join()).isFalse();
        assertThat(set.expireValueIfGreaterAsync("a", Duration.ofSeconds(20)).toCompletableFuture().join()).isTrue();
        assertThat(set.remainTimeToLiveAsync("a").toCompletableFuture().join()).isBetween(19000L, 20000L);
        assertThat(set.clearExpireAsync(Set.of("a")).toCompletableFuture().join()).containsExactly("a");
    }

    @Test
    public void testBatch() {
        RBatch batch = redisson.createBatch();
        RSetCacheNativeAsync<String> set = batch.getSetCacheNative("testBatch", StringCodec.INSTANCE);
        RFuture<Boolean> added = set.addAsync("a", Duration.ofSeconds(10));
        RFuture<Boolean> addedAll = set.addAllAsync(SetAddArgs.values(List.of("b", "c")));
        RFuture<Integer> expired = set.expireValuesAsync(Set.of("b", "c", "missing"), Duration.ofSeconds(20));
        RFuture<Map<String, Long>> ttls = set.remainTimeToLiveAsync(Set.of("a", "missing"));
        RFuture<Boolean> cleared = set.clearExpireAsync("missing");
        batch.execute();

        assertThat(added.toCompletableFuture().join()).isTrue();
        assertThat(addedAll.toCompletableFuture().join()).isTrue();
        assertThat(expired.toCompletableFuture().join()).isEqualTo(2);
        assertThat(ttls.toCompletableFuture().join().get("a")).isBetween(9000L, 10000L);
        assertThat(ttls.toCompletableFuture().join()).containsEntry("missing", -2L);
        assertThat(cleared.toCompletableFuture().join()).isNull();

        RSetCacheNativeAsync<String> other = redisson.createBatch().getSetCacheNative("testBatch");
        assertThat(other).isNotNull();
    }

    @Test
    public void testReactive() {
        RedissonReactiveClient reactive = redisson.reactive();
        RSetCacheNativeReactive<String> set = reactive.getSetCacheNative("testReactive", StringCodec.INSTANCE);

        assertThat(set.add("a", Duration.ofSeconds(10)).block()).isTrue();
        assertThat(set.addAll(SetAddArgs.values(List.of("b", "c")).timeToLive(Duration.ofSeconds(20))).block())
                .isTrue();
        assertThat(set.expireValueIfLess("c", Duration.ofSeconds(5)).block()).isTrue();
        assertThat(set.remainTimeToLive(Set.of("a", "b")).block()).allSatisfy((v, ttl) ->
                assertThat(ttl).isBetween(9000L, 20000L));
        assertThat(set.clearExpire("missing").block()).isNull();
        assertThat(set.clearExpire("a").block()).isTrue();
        assertThat(set.clearExpire(Set.of("c", "missing")).block()).containsExactly("c");
        assertThat(Flux.from(set.iterator()).collectList().block()).containsOnly("a", "b", "c");

        RBatchReactive batch = reactive.createBatch();
        RSetCacheNativeReactive<String> batchSet = batch.getSetCacheNative("testReactive", StringCodec.INSTANCE);
        batchSet.expireValues(Set.of("a", "b"), Duration.ofSeconds(30)).subscribe();
        batch.execute().block();
        assertThat(set.remainTimeToLive("a").block()).isBetween(29000L, 30000L);

        assertThat(reactive.getSetCacheNative(PlainOptions.name("testReactive").codec(StringCodec.INSTANCE))
                           .size().block()).isEqualTo(3);
        assertThat(reactive.getSetCacheNative("testReactive")).isNotNull();
    }

    @Test
    public void testRx() {
        RedissonRxClient rx = redisson.rxJava();
        RSetCacheNativeRx<String> set = rx.getSetCacheNative("testRx", StringCodec.INSTANCE);

        assertThat(set.add("a", Duration.ofSeconds(10)).blockingGet()).isTrue();
        assertThat(set.addIfAllAbsent(SetAddArgs.values(List.of("b", "c")).timeToLive(Duration.ofSeconds(20)))
                      .blockingGet()).isTrue();
        assertThat(set.expireValuesIfGreater(Set.of("a", "b"), Duration.ofSeconds(15)).blockingGet()).isEqualTo(1);
        assertThat(set.getExpireTime("a").blockingGet()).isGreaterThan(System.currentTimeMillis());

        Maybe<Boolean> missing = set.clearExpire("missing");
        assertThat(missing.isEmpty().blockingGet()).isTrue();
        assertThat(set.clearExpire("a").blockingGet()).isTrue();
        assertThat(set.clearExpire(Set.of("b", "c", "missing")).blockingGet()).containsOnly("b", "c");
        assertThat(set.iterator().toList().blockingGet()).containsOnly("a", "b", "c");

        RBatchRx batch = rx.createBatch();
        RSetCacheNativeRx<String> batchSet = batch.getSetCacheNative("testRx", StringCodec.INSTANCE);
        batchSet.expireValues(Set.of("a", "b"), Duration.ofSeconds(30)).subscribe();
        batch.execute().blockingGet();
        assertThat(set.remainTimeToLive("a").blockingGet()).isBetween(29000L, 30000L);

        assertThat(rx.getSetCacheNative(PlainOptions.name("testRx").codec(StringCodec.INSTANCE))
                     .size().blockingGet()).isEqualTo(3);
        assertThat(rx.getSetCacheNative("testRx")).isNotNull();
    }

    @Test
    public void testExpiredListener() {
        testWithEvents("Ts", client -> {
            RSetCacheNative<String> set = client.getSetCacheNative("testExpiredListener", StringCodec.INSTANCE);
            // the expirations are set before the listener exists, so only their expiry can reach it
            set.addAll(SetAddArgs.values(List.of("a", "b")).timeToLive(Duration.ofMillis(1500)));
            set.add("keep");

            Queue<String> expired = new ConcurrentLinkedQueue<>();
            set.addListener((SetMemberExpiredListener) (name, member) -> expired.add(name + ":" + member));
            // setting an expiration publishes sexpire, which this listener must not take for sexpired
            set.expireValue("keep", Duration.ofMinutes(1));

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(expired).containsExactlyInAnyOrder("testExpiredListener:a", "testExpiredListener:b"));
            Awaitility.await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(expired).hasSize(2));
        });
    }

    @Test
    public void testExpiredListenerKeyEventOnly() {
        testWithEvents("Es", client -> {
            RSetCacheNative<String> set = client.getSetCacheNative("testExpiredKeyEvent", StringCodec.INSTANCE);
            Queue<String> expired = new ConcurrentLinkedQueue<>();
            set.addListenerAsync((SetMemberExpiredListener) (name, member) -> expired.add(name + ":" + member))
               .toCompletableFuture().join();

            set.add("keep");
            set.add("a", Duration.ofMillis(200));

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(expired).containsExactly("testExpiredKeyEvent:null"));
        });
    }

    @Test
    public void testClearExpireListener() {
        testWithEvents("Ts", client -> {
            RSetCacheNative<String> set = client.getSetCacheNative("testClearExpireListener", StringCodec.INSTANCE);
            set.add("a", Duration.ofSeconds(10));
            set.add("b", Duration.ofSeconds(10));
            set.add("c");

            Queue<String> cleared = new ConcurrentLinkedQueue<>();
            set.addListener((SetMemberClearExpireListener) (name, member) -> cleared.add(name + ":" + member));
            // setting an expiration publishes sexpire, which this listener must not take for spersist
            set.expireValue("c", Duration.ofMinutes(1));
            set.clearExpire(Set.of("a", "b"));

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(cleared).containsExactlyInAnyOrder(
                            "testClearExpireListener:a", "testClearExpireListener:b"));
            Awaitility.await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(cleared).hasSize(2));
        });
    }

    @Test
    public void testRemoveListener() {
        testWithEvents("Ts", client -> {
            RSetCacheNative<String> set = client.getSetCacheNative("testRemoveListener", StringCodec.INSTANCE);
            Queue<String> events = new ConcurrentLinkedQueue<>();
            // one of each listener type through each removal path, so that a channel either
            // path forgets to unsubscribe keeps delivering to a listener that was removed
            int expiredSync = set.addListener((SetMemberExpiredListener) (name, member) -> events.add("expired:" + member));
            int expiredAsync = set.addListener((SetMemberExpiredListener) (name, member) -> events.add("expired:" + member));
            int clearedSync = set.addListener((SetMemberClearExpireListener) (name, member) -> events.add("cleared:" + member));
            int clearedAsync = set.addListener((SetMemberClearExpireListener) (name, member) -> events.add("cleared:" + member));

            set.add("a", Duration.ofSeconds(10));
            set.clearExpire("a");
            set.add("z", Duration.ofMillis(100));
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(events).containsExactlyInAnyOrder("cleared:a", "cleared:a", "expired:z", "expired:z"));

            set.removeListener(expiredSync);
            set.removeListener(clearedSync);
            set.removeListenerAsync(expiredAsync).toCompletableFuture().join();
            set.removeListenerAsync(clearedAsync).toCompletableFuture().join();
            events.clear();

            set.add("b", Duration.ofSeconds(10));
            set.clearExpire("b");
            set.add("c", Duration.ofMillis(100));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !set.contains("c"));
            Awaitility.await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                      .untilAsserted(() -> assertThat(events).isEmpty());
        });
    }

    @Test
    public void testReplicaRouting() {
        // SPERSIST shares its reply shape with the read-only SPTTL and SPEXPIRETIME, so it is
        // the one per-member command that could be routed like them. A replica refuses it.
        try (Network network = Network.newNetwork();
             GenericContainer<?> master = createRedis812()
                                            .withNetwork(network).withNetworkAliases("master");
             GenericContainer<?> replica = createRedis812("--replicaof", "master", "6379")
                                            .withNetwork(network)) {
            master.start();
            replica.start();

            Config config = new Config();
            config.setProtocol(protocol);
            config.useMasterSlaveServers()
                  .setMasterAddress("redis://127.0.0.1:" + master.getFirstMappedPort())
                  .addSlaveAddress("redis://127.0.0.1:" + replica.getFirstMappedPort())
                  .setReadMode(ReadMode.SLAVE);
            RedissonClient client = Redisson.create(config);
            try {
                RSetCacheNative<String> set = client.getSetCacheNative("testReplicaRouting", StringCodec.INSTANCE);
                set.add("a", Duration.ofSeconds(60));
                set.add("b", Duration.ofSeconds(60));

                Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(set.remainTimeToLive(Set.of("a", "b"))).allSatisfy((v, ttl) ->
                                assertThat(ttl).isBetween(1L, 60000L)));

                assertThat(set.clearExpire(Set.of("a", "b"))).containsOnly("a", "b");
                Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(set.getExpireTime(Set.of("a", "b"))).containsOnly(
                                Map.entry("a", -1L), Map.entry("b", -1L)));
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    public void testUnsupportedListener() {
        RSetCacheNative<String> set = redisson.getSetCacheNative("testUnsupportedListener", StringCodec.INSTANCE);
        // RSetCache's listener carries a decoded value, which native notifications cannot supply
        assertThatThrownBy(() -> set.addListener((org.redisson.api.listener.SetExpiredListener<String>) v -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
