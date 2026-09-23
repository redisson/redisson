package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucketRx;
import org.redisson.api.RKeysRx;
import org.redisson.api.RMapRx;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonKeysRxTest extends BaseRxTest {

    @Test
    public void testGetKeys() {
        RKeysRx keys = redisson.getKeys();
        sync(redisson.getBucket("test1").set(1));
        sync(redisson.getBucket("test2").set(1));
        Flowable<String> k = keys.getKeys();
        assertThat(k.blockingIterable()).contains("test1", "test2");
    }

    @Test
    public void testKeysIterablePattern() {
        sync(redisson.getBucket("test1").set("someValue"));
        sync(redisson.getBucket("test2").set("someValue"));

        sync(redisson.getBucket("test12").set("someValue"));

        Iterator<String> iterator = toIterator(redisson.getKeys().getKeysByPattern("test?"));
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testRandomKey() {
        RBucketRx<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue1"));

        RBucketRx<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));

        assertThat(sync(redisson.getKeys().randomKey())).isIn("test1", "test2");
        sync(redisson.getKeys().delete("test1"));
        Assertions.assertEquals("test2", sync(redisson.getKeys().randomKey()));
        sync(redisson.getKeys().flushdb());
        Assertions.assertNull(sync(redisson.getKeys().randomKey()));
    }

    @Test
    public void testDeleteByPattern() {
        RBucketRx<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue"));
        RMapRx<String, String> map = redisson.getMap("test2");
        sync(map.fastPut("1", "2"));

        Assertions.assertEquals(2, sync(redisson.getKeys().deleteByPattern("test?")).intValue());
    }

    @Test
    public void testUnlinkByPattern() {
        RBucketRx<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue"));
        RMapRx<String, String> map = redisson.getMap("test2");
        sync(map.fastPut("1", "2"));

        Assertions.assertEquals(2, sync(redisson.getKeys().unlinkByPattern("test?")).intValue());
    }

    @Test
    public void testMassDelete() {
        RBucketRx<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        RMapRx<String, String> map = redisson.getMap("map2");
        sync(map.fastPut("1", "2"));

        Assertions.assertEquals(2, sync(redisson.getKeys().delete("test", "map2")).intValue());
        Assertions.assertEquals(0, sync(redisson.getKeys().delete("test", "map2")).intValue());
    }

    @Test
    public void testExpire() {
        Long s = sync(redisson.getKeys().count());
        assertThat(s).isEqualTo(0);

        sync(redisson.getBucket("expire-test1").set(23, Duration.ofHours(1)));
        sync(redisson.getBucket("expire-test2").set(23, Duration.ofHours(1)));
        s = sync(redisson.getKeys().expire(Duration.ofDays(1), new String[]{"expire-test1", "expire-test2"}));
        assertThat(s).isEqualTo(2);

        long ttl1 = sync(redisson.getBucket("expire-test1").remainTimeToLive());
        long ttl2 = sync(redisson.getBucket("expire-test2").remainTimeToLive());
        assertThat(ttl1).isGreaterThan(TimeUnit.HOURS.toMillis(23));
        assertThat(ttl2).isGreaterThan(TimeUnit.HOURS.toMillis(23));

        long ts = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2);
        s = sync(redisson.getKeys().expireAt(Instant.ofEpochMilli(ts), "expire-test1", "expire-test2"));
        assertThat(s).isEqualTo(2);

        ttl1 = sync(redisson.getBucket("expire-test1").remainTimeToLive());
        assertThat(ttl1).isGreaterThan(TimeUnit.DAYS.toMillis(1));
        ttl2 = sync(redisson.getBucket("expire-test2").remainTimeToLive());
        assertThat(ttl2).isGreaterThan(TimeUnit.DAYS.toMillis(1));

        s = sync(redisson.getKeys().expire(Duration.ofDays(1), "expire-miss"));
        assertThat(s).isEqualTo(0);

        s = sync(redisson.getKeys().expireAt(Instant.ofEpochMilli(ts), "expire-miss"));
        assertThat(s).isEqualTo(0);
    }

    @Test
    public void testGetKeysByPatternFull() {
        int expected = 200;
        for (int i = 0; i < expected; i++) {
            redisson.getBucket("pattern-test:" + i, StringCodec.INSTANCE)
                    .set(Integer.toString(i)).blockingAwait();
        }

        assertThat(redisson.getKeys().count().blockingGet()).isEqualTo(expected);

        List<String> shareKeys = redisson.getKeys()
                .getKeys(KeysScanOptions.defaults().pattern("pattern-test:*"))
                .toList()
                .blockingGet();

        Map<String, Object> bucketMap = redisson
                .getBuckets(StringCodec.INSTANCE)
                .get(shareKeys.toArray(new String[0]))
                .blockingGet();

        assertThat(shareKeys).hasSize(expected);
        assertThat(bucketMap).hasSize(expected);
    }

    @Test
    public void testGetKeysEmptyDatabase() {
        List<String> result = redisson.getKeys().getKeys().toList().blockingGet();
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetKeysPartialRequestReturnsExactCount() {
        int total = 50;
        for (int i = 0; i < total; i++) {
            redisson.getBucket("partial-test:" + i, StringCodec.INSTANCE)
                    .set(Integer.toString(i)).blockingAwait();
        }

        // Requests fewer items than exist (via take(5)) while the chunk size (10)
        // forces multiple SCAN round-trips underneath. Exercises the RxIteratorConsumer's
        // request-counting path with partial downstream demand instead of unbounded demand.
        List<String> firstFive = redisson.getKeys()
                .getKeys(KeysScanOptions.defaults().pattern("partial-test:*").chunkSize(10))
                .take(5)
                .toList()
                .blockingGet();

        assertThat(firstFive).hasSize(5);
        assertThat(firstFive).doesNotHaveDuplicates();
        for (String key : firstFive) {
            assertThat(key).startsWith("partial-test:");
        }
    }

}
