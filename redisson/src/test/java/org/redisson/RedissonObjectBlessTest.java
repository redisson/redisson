package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.*;
import org.redisson.api.options.LocalCachedMapOptions;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.codec.StringCodec;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonObjectBlessTest extends RedisDockerTest {

    private static GenericContainer<?> redis812;
    private static RedissonClient redisson812;

    @BeforeAll
    static void startRedis812() {
        redis812 = createRedis812();
        redis812.start();
        redisson812 = Redisson.create(createConfig(redis812));
    }

    @AfterAll
    static void stopRedis812() {
        redisson812.shutdown();
        redis812.stop();
    }

    @BeforeEach
    public void flush812() {
        redisson812.getKeys().flushall();
    }

    public static class ReverseOrder implements Comparator<String>, Serializable {
        @Override
        public int compare(String a, String b) {
            return b.compareTo(a);
        }
    }

    record Sample(RObject object, int keyCount, Runnable close) {
    }

    static Stream<Object[]> samples() {
        Map<String, Function<RedissonClient, Sample>> samples = new LinkedHashMap<>();
        samples.put("RBucket", r -> {
            RBucket<String> bucket = r.getBucket("bucket");
            bucket.set("v");
            return new Sample(bucket, 1, () -> { });
        });
        samples.put("RMap", r -> {
            RMap<String, String> map = r.getMap("map");
            map.put("k", "v");
            return new Sample(map, 1, () -> { });
        });
        samples.put("RLocalCachedMap", r -> {
            RLocalCachedMap<String, String> map = r.getLocalCachedMap(LocalCachedMapOptions.name("localMap"));
            map.put("k", "v");
            return new Sample(map, 1, map::destroy);
        });
        samples.put("RMapCache", r -> {
            RMapCache<String, String> map = r.getMapCache("mapCache");
            map.setMaxSize(10);
            map.put("k", "v", 1, TimeUnit.HOURS, 1, TimeUnit.HOURS);
            return new Sample(map, 5, map::destroy);
        });
        samples.put("MapCacheNativeWrapper", r -> {
            RMapCacheNative<String, String> map = r.getMapCacheNative("mapCacheNative");
            map.put("k", "v", Duration.ofHours(1));
            return new Sample(new MapCacheNativeWrapper<>(map), 1, () -> { });
        });
        samples.put("RSetCache", r -> {
            RSetCache<String> set = r.getSetCache("setCache");
            set.add("v", 1, TimeUnit.HOURS);
            return new Sample(set, 1, set::destroy);
        });
        samples.put("RBloomFilter", r -> {
            RBloomFilter<String> filter = r.getBloomFilter("bloomFilter");
            filter.tryInit(1000, 0.01);
            filter.add("v");
            return new Sample(filter, 2, () -> { });
        });
        samples.put("RBoundedBlockingQueue", r -> {
            RBoundedBlockingQueue<String> queue = r.getBoundedBlockingQueue("boundedQueue");
            queue.trySetCapacity(10);
            queue.add("v");
            return new Sample(queue, 2, () -> { });
        });
        samples.put("RCircularBuffer", r -> {
            RCircularBuffer<String> buffer = r.getCircularBuffer("circularBuffer");
            buffer.trySetCapacity(10);
            buffer.add("v");
            return new Sample(buffer, 2, () -> { });
        });
        samples.put("RRingBuffer", r -> {
            RRingBuffer<String> buffer = r.getRingBuffer("ringBuffer");
            buffer.trySetCapacity(10);
            buffer.add("v");
            return new Sample(buffer, 2, () -> { });
        });
        samples.put("RDelayedQueue", r -> {
            // the destination queue is another object, and stays empty for an hour
            RDelayedQueue<String> queue = r.getDelayedQueue(r.getQueue("destination"));
            queue.offer("v", 1, TimeUnit.HOURS);
            return new Sample(queue, 2, queue::destroy);
        });
        // RGcra is left out: the 8.12-rc3 image has no GCRA command, so its keys can't be created.
        samples.put("RIdGenerator", r -> {
            RIdGenerator generator = r.getIdGenerator("idGenerator");
            generator.tryInit(1, 10);
            generator.nextId();
            return new Sample(generator, 2, () -> { });
        });
        samples.put("RPermitExpirableSemaphore", r -> {
            RPermitExpirableSemaphore semaphore = r.getPermitExpirableSemaphore("semaphore");
            semaphore.trySetPermits(5);
            try {
                semaphore.acquire(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return new Sample(semaphore, 2, () -> { });
        });
        samples.put("RPriorityQueue", r -> {
            RPriorityQueue<String> queue = r.getPriorityQueue("priorityQueue");
            queue.trySetComparator(new ReverseOrder());
            queue.add("v");
            return new Sample(queue, 2, () -> { });
        });
        samples.put("RSortedSet", r -> {
            // a serializable comparator is stored as an instance too
            RSortedSet<String> set = r.getSortedSet("sortedSet");
            set.trySetComparator(new ReverseOrder());
            set.add("v");
            return new Sample(set, 3, () -> { });
        });
        samples.put("RRateLimiter", r -> {
            RRateLimiter limiter = r.getRateLimiter("rateLimiter");
            limiter.trySetRate(RateType.OVERALL, 10, Duration.ofSeconds(1));
            limiter.tryAcquire();
            return new Sample(limiter, 3, () -> { });
        });
        samples.put("RReliableTopic", r -> {
            RReliableTopic topic = r.getReliableTopic("reliableTopic", StringCodec.INSTANCE);
            String id = topic.addListener(String.class, (channel, msg) -> { });
            topic.publish("v");
            return new Sample(topic, 2, () -> topic.removeListener(id));
        });
        samples.put("RTimeSeries", r -> {
            RTimeSeries<String, Object> series = r.getTimeSeries("timeSeries");
            series.add(1, "v", Duration.ofHours(1));
            return new Sample(series, 3, series::destroy);
        });
        samples.put("RListMultimap", r -> {
            RListMultimap<String, String> multimap = r.getListMultimap("listMultimap");
            multimap.put("a", "1");
            multimap.put("b", "2");
            return new Sample(multimap, 3, () -> { });
        });
        samples.put("RSetMultimap", r -> {
            RSetMultimap<String, String> multimap = r.getSetMultimap("setMultimap");
            multimap.put("a", "1");
            multimap.put("b", "2");
            return new Sample(multimap, 3, () -> { });
        });
        samples.put("RListMultimapCache", r -> {
            RListMultimapCache<String, String> multimap = r.getListMultimapCache("listMultimapCache");
            multimap.put("a", "1");
            multimap.put("b", "2");
            multimap.expireKey("a", 1, TimeUnit.HOURS);
            return new Sample(multimap, 4, multimap::destroy);
        });
        samples.put("RSetMultimapCache", r -> {
            RSetMultimapCache<String, String> multimap = r.getSetMultimapCache("setMultimapCache");
            multimap.put("a", "1");
            multimap.put("b", "2");
            multimap.expireKey("a", 1, TimeUnit.HOURS);
            return new Sample(multimap, 4, multimap::destroy);
        });
        samples.put("RListMultimapCacheNative", r -> {
            RListMultimapCacheNative<String, String> multimap = r.getListMultimapCacheNative("listMultimapCacheNative");
            multimap.put("a", "1");
            multimap.put("b", "2");
            multimap.expireKey("a", 1, TimeUnit.HOURS);
            return new Sample(multimap, 3, () -> { });
        });
        samples.put("RSetMultimapCacheNative", r -> {
            RSetMultimapCacheNative<String, String> multimap = r.getSetMultimapCacheNative("setMultimapCacheNative");
            multimap.put("a", "1");
            multimap.put("b", "2");
            multimap.expireKey("a", 1, TimeUnit.HOURS);
            return new Sample(multimap, 3, () -> { });
        });
        return samples.entrySet().stream().map(e -> new Object[] {e.getKey(), e.getValue()});
    }

    private static final String EVICTION_LATCH_PREFIX = "redisson__execute_task_once_latch:";

    private static Set<String> keys(RedissonClient client) {
        Set<String> keys = new TreeSet<>();
        for (String key : client.getKeys().getKeys()) {
            if (!key.startsWith(EVICTION_LATCH_PREFIX)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Set<String> blessedKeys(RedissonClient client) {
        Set<String> keys = new TreeSet<>();
        client.getKeys().getBlessedKeys().forEach(keys::add);
        return keys;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    public void testBlessCoversEveryKey(String type, Function<RedissonClient, Sample> factory) {
        Sample sample = factory.apply(redisson812);
        try {
            RObject object = sample.object();
            Set<String> keys = keys(redisson812);
            assertThat(keys).as("keys of %s", type).hasSize(sample.keyCount());

            assertThat(object.isBlessed()).isFalse();
            assertThat(object.bless()).isTrue();
            assertThat(object.bless()).isFalse();
            assertThat(object.isBlessed()).isTrue();
            assertThat(blessedKeys(redisson812)).isEqualTo(keys);

            assertThat(object.unbless()).isTrue();
            assertThat(object.unbless()).isFalse();
            assertThat(object.isBlessed()).isFalse();
            assertThat(blessedKeys(redisson812)).isEmpty();
        } finally {
            sample.close().run();
        }
    }

    @Test
    public void testMissingObject() {
        RMap<String, String> map = redisson812.getMap("testMissingObject");
        assertThat(map.bless()).isFalse();
        assertThat(map.isBlessed()).isFalse();
        assertThat(map.unbless()).isFalse();

        RBloomFilter<String> filter = redisson812.getBloomFilter("testMissingObject");
        assertThat(filter.bless()).isFalse();
        assertThat(filter.isBlessed()).isFalse();
        assertThat(filter.unbless()).isFalse();
    }

    @Test
    public void testIsBlessedNeedsEveryKey() {
        RBloomFilter<String> filter = redisson812.getBloomFilter("testIsBlessedNeedsEveryKey");
        filter.tryInit(1000, 0.01);
        filter.add("v");
        filter.bless();

        // one key unblessed by name leaves the filter half protected
        String config = RedissonObject.suffixName(filter.getName(), "config");
        assertThat(redisson812.getKeys().unbless(config)).isTrue();
        assertThat(filter.isBlessed()).isFalse();

        assertThat(filter.bless()).isTrue();
        assertThat(filter.isBlessed()).isTrue();
    }

    @Test
    public void testKeyCreatedLaterIsNotProtected() {
        // tryInit writes the configuration only; the bit array appears on the first add
        RBloomFilter<String> filter = redisson812.getBloomFilter("testKeyCreatedLaterIsNotProtected");
        filter.tryInit(1000, 0.01);
        assertThat(filter.bless()).isTrue();
        assertThat(filter.isBlessed()).isTrue();

        filter.add("v");
        assertThat(filter.isBlessed()).isFalse();
        assertThat(blessedKeys(redisson812)).hasSize(1);

        assertThat(filter.bless()).isTrue();
        assertThat(blessedKeys(redisson812)).hasSize(2);

        RSet<String> set = redisson812.getSet("testKeyCreatedLaterIsNotProtected2");
        set.add("a");
        set.bless();
        set.remove("a");
        set.add("b");
        assertThat(set.isBlessed()).isFalse();
    }

    @Test
    public void testMultimapValuesRefuse() {
        RListMultimap<String, String> list = redisson812.getListMultimap("testMultimapValuesRefuse1");
        list.put("a", "1");
        assertThatThrownBy(() -> list.get("a").bless()).isInstanceOf(UnsupportedOperationException.class);

        RSetMultimap<String, String> set = redisson812.getSetMultimap("testMultimapValuesRefuse2");
        set.put("a", "1");
        assertThatThrownBy(() -> set.get("a").bless()).isInstanceOf(UnsupportedOperationException.class);

        RListMultimapCache<String, String> listCache = redisson812.getListMultimapCache("testMultimapValuesRefuse3");
        listCache.put("a", "1");
        assertThatThrownBy(() -> listCache.get("a").isBlessed()).isInstanceOf(UnsupportedOperationException.class);

        RSetMultimapCache<String, String> setCache = redisson812.getSetMultimapCache("testMultimapValuesRefuse4");
        setCache.put("a", "1");
        assertThatThrownBy(() -> setCache.get("a").unbless()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testTransactionRefuses() {
        redisson812.getMap("testTransactionRefuses").put("k", "v");
        RTransaction transaction = redisson812.createTransaction(TransactionOptions.defaults());
        try {
            RMap<String, String> map = transaction.getMap("testTransactionRefuses");
            assertThatThrownBy(map::bless).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(map::unbless).isInstanceOf(UnsupportedOperationException.class);
            assertThat(map.isBlessed()).isFalse();
            assertThatThrownBy(() -> transaction.getBucket("b").bless()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> transaction.getMapCache("c").bless()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> transaction.getSet("d").bless()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> transaction.getSetCache("e").bless()).isInstanceOf(UnsupportedOperationException.class);
        } finally {
            transaction.rollback();
        }
        assertThat(redisson812.getMap("testTransactionRefuses").isBlessed()).isFalse();
    }

}
