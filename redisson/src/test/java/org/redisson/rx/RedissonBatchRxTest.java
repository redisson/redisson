package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.BaseTest;
import org.redisson.ClusterRunner;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatchRx;
import org.redisson.api.RBucketRx;
import org.redisson.api.RListRx;
import org.redisson.api.RMapCacheRx;
import org.redisson.api.RMapRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RScript;
import org.redisson.api.RScript.Mode;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public class RedissonBatchRxTest extends BaseRxTest {

    public static List<BatchOptions> data() {
        return Arrays.asList(BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY),
                            BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
    }

//    @Test
    public void testBatchRedirect(BatchOptions batchOptions) {
        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 5; i++) {
            batch.getMap("" + i).fastPut("" + i, i);
        }
        sync(batch.execute());

        batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 1; i++) {
            batch.getMap("" + i).size();
            batch.getMap("" + i).containsValue("" + i);
            batch.getMap("" + i).containsValue(i);
        }
        BatchResult<?> t = sync(batch.execute());
        System.out.println(t);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testConvertor(BatchOptions batchOptions) {
        RBatchRx batch = redisson.createBatch(batchOptions);

        Single<Double> f1 = batch.getScoredSortedSet("myZKey").addScore("abc", 1d);
        Completable f2 = batch.getBucket("test").set("1");
        sync(batch.execute());
        assertThat(sync(f1)).isEqualTo(1d);
        sync(f2);
        
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("myZKey");
        assertThat(sync(set.getScore("abc"))).isEqualTo(1d);
        RBucketRx<String> bucket = redisson.getBucket("test");
        assertThat(sync(bucket.get())).isEqualTo("1");
        
        RBatchRx batch2 = redisson.createBatch(batchOptions);
        Single<Double> b2f1 = batch2.getScoredSortedSet("myZKey2").addScore("abc", 1d);
        Single<Double> b2f2 = batch2.getScoredSortedSet("myZKey2").addScore("abc", 1d);
        sync(batch2.execute());
        
        assertThat(sync(b2f1)).isEqualTo(1d);
        assertThat(sync(b2f2)).isEqualTo(2d);
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testPerformance(BatchOptions batchOptions) {
        Assertions.assertTimeout(Duration.ofSeconds(21), () -> {
            RMapRx<String, String> map = redisson.getMap("map");
            Map<String, String> m = new HashMap<String, String>();
            for (int j = 0; j < 1000; j++) {
                m.put("" + j, "" + j);
            }
            sync(map.putAll(m));

            for (int i = 0; i < 10000; i++) {
                RBatchRx batch = redisson.createBatch(batchOptions);
                RMapRx<String, String> m1 = batch.getMap("map");
                Single<Map<String, String>> f = m1.getAll(m.keySet());
                sync(batch.execute());
                assertThat(sync(f)).hasSize(1000);
            }
        });
    }

    @Test
    public void testConnectionLeakAfterError() {
        Config config = BaseTest.createConfig();
        config.useSingleServer()
                .setRetryInterval(100)
                .setTimeout(200)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonRxClient redisson = Redisson.create(config).rxJava();
        
        BatchOptions batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC);
        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 25000; i++) {
            batch.getBucket("test").set(123);
        }
        
        try {
            sync(batch.execute());
            Assertions.fail();
        } catch (Exception e) {
            // skip
        }
        
        sync(redisson.getBucket("test3").set(4));
        assertThat(sync(redisson.getBucket("test3").get())).isEqualTo(4);
        
        batch = redisson.createBatch(batchOptions);
        batch.getBucket("test1").set(1);
        batch.getBucket("test2").set(2);
        sync(batch.execute());
        
        assertThat(sync(redisson.getBucket("test1").get())).isEqualTo(1);
        assertThat(sync(redisson.getBucket("test2").get())).isEqualTo(2);
        
        redisson.shutdown();
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testBigRequestAtomic(BatchOptions batchOptions) {
        batchOptions
                    .executionMode(ExecutionMode.IN_MEMORY_ATOMIC)
                    .responseTimeout(15, TimeUnit.SECONDS)
                    .retryInterval(1, TimeUnit.SECONDS)
                    .retryAttempts(5);
        
        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 100; i++) {
            batch.getBucket("" + i).set(i);
            batch.getBucket("" + i).get();
        }
        
        BatchResult<?> s = sync(batch.execute());
        assertThat(s.getResponses().size()).isEqualTo(200);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSyncSlaves(BatchOptions batchOptions) throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setTimeout(1000000)
        .setRetryInterval(1000000)
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonRxClient redisson = Redisson.create(config).rxJava();
        
        batchOptions
                .syncSlaves(1, 1, TimeUnit.SECONDS);
        
        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 100; i++) {
            RMapRx<String, String> map = batch.getMap("test");
            map.put("" + i, "" + i);
        }

        BatchResult<?> result = sync(batch.execute());
        assertThat(result.getResponses()).hasSize(100);
        assertThat(result.getSyncedSlaves()).isEqualTo(1);
        
        process.shutdown();
        redisson.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testWriteTimeout(BatchOptions batchOptions) throws InterruptedException {
        Config config = BaseTest.createConfig();
        config.useSingleServer().setRetryInterval(700).setTimeout(1500);
        RedissonRxClient redisson = Redisson.create(config).rxJava();

        RBatchRx batch = redisson.createBatch(batchOptions);
        RMapCacheRx<String, String> map = batch.getMapCache("test");
        int total = 10000;
        for (int i = 0; i < total; i++) {
            map.put("" + i, "" + i, 5, TimeUnit.MINUTES);
            if (batchOptions.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
                if (i % 100 == 0) {
                    Thread.sleep(10);
                }
            }
        }
        
        long s = System.currentTimeMillis();
        sync(batch.execute());
        long executionTime = System.currentTimeMillis() - s;
        if (batchOptions.getExecutionMode() == ExecutionMode.IN_MEMORY) {
            assertThat(executionTime).isLessThan(1000);
        } else {
            assertThat(executionTime).isLessThan(300);
        }
        assertThat(sync(redisson.getMapCache("test").size())).isEqualTo(total);
        redisson.shutdown();
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testSkipResult(BatchOptions batchOptions) {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.2.0") > 0);
        
        batchOptions
                                            .skipResult();

        RBatchRx batch = redisson.createBatch(batchOptions);
        batch.getBucket("A1").set("001");
        batch.getBucket("A2").set("001");
        batch.getBucket("A3").set("001");
        batch.getKeys().delete("A1");
        batch.getKeys().delete("A2");
        sync(batch.execute());
        
        assertThat(sync(redisson.getBucket("A1").isExists())).isFalse();
        assertThat(sync(redisson.getBucket("A3").isExists())).isTrue();
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testBatchNPE(BatchOptions batchOptions) {
        RBatchRx batch = redisson.createBatch(batchOptions);
        batch.getBucket("A1").set("001");
        batch.getBucket("A2").set("001");
        batch.getBucket("A3").set("001");
        batch.getKeys().delete("A1");
        batch.getKeys().delete("A2");
        sync(batch.execute());
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testAtomic(BatchOptions batchOptions) {
        batchOptions.executionMode(ExecutionMode.IN_MEMORY_ATOMIC);
        
        RBatchRx batch = redisson.createBatch(batchOptions);
        Single<Long> f1 = batch.getAtomicLong("A1").addAndGet(1);
        Single<Long> f2 = batch.getAtomicLong("A2").addAndGet(2);
        Single<Long> f3 = batch.getAtomicLong("A3").addAndGet(3);
        Single<Long> d1 = batch.getKeys().delete("A1", "A2");
        BatchResult<?> f = sync(batch.execute());
        
        List<Object> list = (List<Object>) f.getResponses();
        assertThat(list).containsExactly(1L, 2L, 3L, 2L);
        assertThat(sync(f1)).isEqualTo(1);
        assertThat(sync(f2)).isEqualTo(2);
        assertThat(sync(f3)).isEqualTo(3);
        assertThat(sync(d1)).isEqualTo(2);
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testAtomicSyncSlaves(BatchOptions batchOptions) throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setTimeout(123000)
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonRxClient redisson = Redisson.create(config).rxJava();
        
        batchOptions
                                            .executionMode(ExecutionMode.IN_MEMORY_ATOMIC)
                                            .syncSlaves(1, 1, TimeUnit.SECONDS);

        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 10; i++) {
            batch.getAtomicLong("{test}" + i).addAndGet(i);
        }

        BatchResult<?> result = sync(batch.execute());
        assertThat(result.getSyncedSlaves()).isEqualTo(1);
        int i = 0;
        for (Object res : result.getResponses()) {
            assertThat((Long)res).isEqualTo(i++);
        }
        
        process.shutdown();
        redisson.shutdown();
    }

    
    @ParameterizedTest
    @MethodSource("data")
    public void testDifferentCodecs(BatchOptions batchOptions) {
        RBatchRx b = redisson.createBatch(batchOptions);
        b.getMap("test1").put("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).put("21", "3");
        Maybe<Object> val1 = b.getMap("test1").get("1");
        Maybe<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).get("21");
        sync(b.execute());

        Assertions.assertEquals("2", sync(val1));
        Assertions.assertEquals("3", sync(val2));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDifferentCodecsAtomic(BatchOptions batchOptions) {
        RBatchRx b = redisson.createBatch(batchOptions.executionMode(ExecutionMode.IN_MEMORY_ATOMIC));
        b.getMap("test1").put("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).put("21", "3");
        Maybe<Object> val1 = b.getMap("test1").get("1");
        Maybe<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).get("21");
        sync(b.execute());

        Assertions.assertEquals("2", sync(val1));
        Assertions.assertEquals("3", sync(val2));
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testBatchList(BatchOptions batchOptions) {
        RBatchRx b = redisson.createBatch(batchOptions);
        RListRx<Integer> listAsync = b.getList("list");
        for (int i = 1; i < 540; i++) {
            listAsync.add(i);
        }
        BatchResult<?> res = sync(b.execute());
        Assertions.assertEquals(539, res.getResponses().size());
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testBatchBigRequest(BatchOptions batchOptions) {
        Config config = BaseTest.createConfig();
        config.useSingleServer().setTimeout(15000);
        RedissonRxClient redisson = Redisson.create(config).rxJava();

        RBatchRx batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 210; i++) {
            batch.getMap("test").fastPut("1", "2");
            batch.getMap("test").fastPut("2", "3");
            batch.getMap("test").put("2", "5");
            batch.getAtomicLong("counter").incrementAndGet();
            batch.getAtomicLong("counter").incrementAndGet();
        }
        BatchResult<?> res = sync(batch.execute());
        Assertions.assertEquals(210*5, res.getResponses().size());
        
        redisson.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testExceptionHandling(BatchOptions batchOptions) {
        Assertions.assertThrows(RedisException.class, () -> {
            RBatchRx batch = redisson.createBatch(batchOptions);
            batch.getMap("test").put("1", "2");
            batch.getScript().eval(Mode.READ_WRITE, "wrong_code", RScript.ReturnType.VALUE);
            sync(batch.execute());
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testTwice(BatchOptions batchOptions) {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            RBatchRx batch = redisson.createBatch(batchOptions);
            batch.getMap("test").put("1", "2");
            sync(batch.execute());
            sync(batch.execute());
        });
    }


    @ParameterizedTest
    @MethodSource("data")
    public void testEmpty(BatchOptions batchOptions) {
        RBatchRx batch = redisson.createBatch(batchOptions);
        sync(batch.execute());
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testOrdering(BatchOptions batchOptions) throws InterruptedException {
        ExecutorService e = Executors.newFixedThreadPool(16);
        RBatchRx batch = redisson.createBatch(batchOptions);
        AtomicLong index = new AtomicLong(-1);
        List<Single<Long>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 500; i++) {
            futures.add(null);
        }
        for (int i = 0; i < 500; i++) {
            final int j = i;
            e.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (RedissonBatchRxTest.this) {
                        int i = (int) index.incrementAndGet();
                        int ind = j % 3;
                        Single<Long> f1 = batch.getAtomicLong("test" + ind).addAndGet(j);
                        futures.set(i, f1);
                    }
                }
            });
        }
        e.shutdown();
        Assertions.assertTrue(e.awaitTermination(30, TimeUnit.SECONDS));
        List<?> s = sync(batch.execute()).getResponses();
        
        int i = 0;
        for (Object element : s) {
            Single<Long> a = futures.get(i);
            Assertions.assertEquals(sync(a), element);
            i++;
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(BatchOptions batchOptions) {
        RBatchRx batch = redisson.createBatch(batchOptions);
        batch.getMap("test").fastPut("1", "2");
        batch.getMap("test").fastPut("2", "3");
        batch.getMap("test").put("2", "5");
        batch.getAtomicLong("counter").incrementAndGet();
        batch.getAtomicLong("counter").incrementAndGet();

        List<?> res = sync(batch.execute()).getResponses();
        Assertions.assertEquals(5, res.size());
        Assertions.assertTrue((Boolean)res.get(0));
        Assertions.assertTrue((Boolean)res.get(1));
        Assertions.assertEquals("3", res.get(2));
        Assertions.assertEquals(1L, res.get(3));
        Assertions.assertEquals(2L, res.get(4));

        Map<String, String> map = new HashMap<String, String>();
        map.put("1", "2");
        map.put("2", "5");
        
        assertThat(sync(redisson.getAtomicLong("counter").get())).isEqualTo(2);
        Assertions.assertTrue(sync(redisson.getMap("test").remove("2", "5")));
        Assertions.assertTrue(sync(redisson.getMap("test").remove("1", "2")));
    }

}
