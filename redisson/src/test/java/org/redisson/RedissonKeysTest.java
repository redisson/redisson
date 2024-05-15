package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.listener.FlushListener;
import org.redisson.api.listener.NewObjectListener;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonKeysTest extends RedisDockerTest {

    @Test
    public void testNewObjectListener() {
        testWithParams(redisson -> {
            AtomicReference<String> ref = new AtomicReference<>();
            int id = redisson.getKeys().addListener(new NewObjectListener() {
                @Override
                public void onNew(String name) {
                    ref.set(name);
                }
            });

            redisson.getBucket("test").set("123");

            Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
                assertThat(ref.get()).isEqualTo("test");
            });
        }, NOTIFY_KEYSPACE_EVENTS, "En");
    }

    @Test
    public void testDeleteListener() {
        testWithParams(redisson -> {
            AtomicReference<String> ref = new AtomicReference<>();
            int id = redisson.getKeys().addListener(new DeletedObjectListener() {
                @Override
                public void onDeleted(String name) {
                    ref.set(name);
                }
            });

            redisson.getBucket("test").set("123");
            redisson.getBucket("test").delete();

            Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
                assertThat(ref.getAndSet(null)).isEqualTo("test");
            });

            redisson.getKeys().removeListener(id);

            redisson.getBucket("test2").set("123");
            redisson.getBucket("test2").delete();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertThat(ref.get()).isNull();
        }, NOTIFY_KEYSPACE_EVENTS, "Eg");
    }

    @Test
    public void testFlushListener() throws InterruptedException {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient r = Redisson.create(c);

        AtomicInteger counter = new AtomicInteger();
        int id = r.getKeys().addListener((FlushListener) address -> {
            assertThat(address).isNotNull();
            counter.incrementAndGet();
        });
        int id2 = r.getKeys().addListener((FlushListener) address -> {
            assertThat(address).isNotNull();
            counter.incrementAndGet();
        });

        r.getKeys().flushall();

        Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(counter.get()).isEqualTo(2);
        });

        r.getKeys().removeListener(id);
        r.getKeys().removeListener(id2);

        r.getKeys().flushall();

        Thread.sleep(100);

        assertThat(counter.get()).isEqualTo(2);

        r.shutdown();
    }

    @Test
    public void testReadKeys() {
        for (int i = 0; i < 10; i++) {
            redisson.getBucket("test" + i).set(i);
        }

        Iterable<String> keys = redisson.getKeys().getKeysWithLimit(3);
        assertThat(keys).hasSize(3);

        Iterable<String> keys2 = redisson.getKeys().getKeysWithLimit(20);
        assertThat(keys2).hasSize(10);
    }

    @Test
    public void testReadKeysPattern() {
        for (int i = 0; i < 10; i++) {
            redisson.getBucket("test" + i).set(i);
        }
        for (int i = 0; i < 5; i++) {
            redisson.getBucket("red" + i).set(i);
        }

        Iterable<String> keys = redisson.getKeys().getKeysWithLimit("test*", 3);
        assertThat(keys).hasSize(3);

        Iterable<String> keys2 = redisson.getKeys().getKeysWithLimit("test*", 20);
        assertThat(keys2).hasSize(10);

        Iterable<String> keys3 = redisson.getKeys().getKeysWithLimit("red*", 3);
        assertThat(keys3).hasSize(3);

        Iterable<String> keys4 = redisson.getKeys().getKeysWithLimit("red*", 10);
        assertThat(keys4).hasSize(5);
    }

    @Test
    public void testTouch() {
        redisson.getSet("test").add("1");
        redisson.getSet("test10").add("1");

        assertThat(redisson.getKeys().touch("test")).isEqualTo(1);
        assertThat(redisson.getKeys().touch("test", "test2")).isEqualTo(1);
        assertThat(redisson.getKeys().touch("test3", "test2")).isEqualTo(0);
        assertThat(redisson.getKeys().touch("test3", "test10", "test")).isEqualTo(2);
    }

    @Test
    public void testExistsInCluster() {
        testInCluster(redisson -> {
            int size = 10000;
            List<String> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add("test" + i);
                redisson.getBucket("test" + i).set(i);
            }

            assertThat(redisson.getKeys().countExists("test1", "test2", "test34", "test45", "asdfl;jasf")).isEqualTo(4);
            long deletedSize = redisson.getKeys().delete(list.toArray(new String[list.size()]));

            assertThat(deletedSize).isEqualTo(size);
        });
    }

    @Test
    public void testExists() {
        redisson.getSet("test").add("1");
        redisson.getSet("test10").add("1");

        assertThat(redisson.getKeys().countExists("test")).isEqualTo(1);
        assertThat(redisson.getKeys().countExists("test", "test2")).isEqualTo(1);
        assertThat(redisson.getKeys().countExists("test3", "test2")).isEqualTo(0);
        assertThat(redisson.getKeys().countExists("test3", "test10", "test")).isEqualTo(2);
    }

    @Test
    public void testType() {
        redisson.getSet("test").add("1");

        assertThat(redisson.getKeys().getType("test")).isEqualTo(RType.SET);
        assertThat(redisson.getKeys().getType("test1")).isNull();
    }

    @Test
    public void testEmptyKeys() {
        Iterable<String> keysIterator = redisson.getKeys().getKeysByPattern("test*", 10);
        assertThat(keysIterator.iterator().hasNext()).isFalse();
    }

    @Test
    public void testKeysByPattern() {
        testInCluster(redisson -> {
            int size = 10000;
            for (int i = 0; i < size; i++) {
                redisson.getBucket("test" + i).set(i);
            }

            assertThat(redisson.getKeys().count()).isEqualTo(size);

            Long noOfKeysDeleted = 0L;
            int chunkSize = 20;
            Iterable<String> keysIterator = redisson.getKeys().getKeysByPattern("test*", chunkSize);
            Set<String> keys = new HashSet<>();
            for (String key : keysIterator) {
                keys.add(key);

                if (keys.size() % chunkSize == 0) {
                    long res = redisson.getKeys().delete(keys.toArray(new String[keys.size()]));
                    assertThat(res).isEqualTo(chunkSize);
                    noOfKeysDeleted += res;
                    keys.clear();
                }
            }
            //Delete remaining keys
            if (!keys.isEmpty()) {
                noOfKeysDeleted += redisson.getKeys().delete(keys.toArray(new String[keys.size()]));
            }

            assertThat(noOfKeysDeleted).isEqualTo(size);
        });
    }


    @Test
    public void testKeysIterablePattern() {
        redisson.getBucket("test1").set("someValue");
        redisson.getBucket("test2").set("someValue");

        redisson.getBucket("test12").set("someValue");

        Iterator<String> iterator = redisson.getKeys().getKeysByPattern("test?").iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testKeysIterable() throws InterruptedException {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < 115; i++) {
            String key = "key" + Math.random();
            RBucket<String> bucket = redisson.getBucket(key);
            keys.add(key);
            bucket.set("someValue");
        }

        Iterator<String> iterator = redisson.getKeys().getKeys().iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            keys.remove(redisson.getConfig().useSingleServer().getNameMapper().map(key));
            iterator.remove();
        }
        Assertions.assertEquals(0, keys.size());
        Assertions.assertFalse(redisson.getKeys().getKeys().iterator().hasNext());
    }

    @Test
    public void testRandomKey() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue1");

        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue2");

        assertThat(redisson.getKeys().randomKey()).isIn("test1", "test2");
        redisson.getKeys().delete("test1");
        Assertions.assertEquals("test2", redisson.getKeys().randomKey());
        redisson.getKeys().flushdb();
        Assertions.assertNull(redisson.getKeys().randomKey());
    }

    @Test
    public void testDeleteInCluster() {
        testInCluster(redisson -> {
            int size = 10000;
            List<String> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add("test" + i);
                redisson.getBucket("test" + i).set(i);
            }

            long deletedSize = redisson.getKeys().delete(list.toArray(new String[list.size()]));

            assertThat(deletedSize).isEqualTo(size);
        });
    }

    @Test
    public void testDeleteByPattern() {
        RBucket<String> bucket = redisson.getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redisson.getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redisson.getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();

        assertThat(redisson.getKeys().deleteByPattern("test?")).isEqualTo(4);
        assertThat(redisson.getKeys().deleteByPattern("test?")).isZero();
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testDeleteByPatternBatch() {
        RBucket<String> bucket = redisson.getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redisson.getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redisson.getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();


        RBatch batch = redisson.createBatch();
        batch.getKeys().deleteByPatternAsync("test?");
        BatchResult<?> r = batch.execute();
        Assertions.assertEquals(4L, r.getResponses().get(0));
    }


    @Test
    public void testFindKeys() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");

        Iterable<String> keys = redisson.getKeys().getKeysByPattern("test?");
        assertThat(keys).containsOnly("test1", "test2");

        Iterable<String> keys2 = redisson.getKeys().getKeysByPattern("test");
        assertThat(keys2).isEmpty();
    }

    @Test
    public void testMassDelete() {
        RBucket<String> bucket0 = redisson.getBucket("test0");
        bucket0.set("someValue");
        RBucket<String> bucket1 = redisson.getBucket("test1");
        bucket1.set("someValue");
        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue");
        RBucket<String> bucket3 = redisson.getBucket("test3");
        bucket3.set("someValue");
        RBucket<String> bucket10 = redisson.getBucket("test10");
        bucket10.set("someValue");

        RBucket<String> bucket12 = redisson.getBucket("test12");
        bucket12.set("someValue");
        RMap<String, String> map = redisson.getMap("map2");
        map.fastPut("1", "2");

        Assertions.assertEquals(7, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
        Assertions.assertEquals(0, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
    }

    @Test
    public void testCount() {
        Long s = redisson.getKeys().count();
        assertThat(s).isEqualTo(0);
        redisson.getBucket("test1").set(23);
        s = redisson.getKeys().count();
        assertThat(s).isEqualTo(1);
    }

}
