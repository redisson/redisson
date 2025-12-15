package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucketRx;
import org.redisson.api.RKeysRx;
import org.redisson.api.RMapRx;
import reactor.core.publisher.Flux;

import java.util.Iterator;

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
}
