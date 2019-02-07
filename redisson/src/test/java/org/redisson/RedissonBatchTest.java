package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.BatchResult;
import org.redisson.api.ClusterNode;
import org.redisson.api.NodeType;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RListAsync;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RMapCacheAsync;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RScript.Mode;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

@RunWith(Parameterized.class)
public class RedissonBatchTest extends BaseTest {

    @Parameterized.Parameters(name= "{index} - {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY)},
            {BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC)}
            });
    }

    @Parameterized.Parameter(0)
    public BatchOptions batchOptions;
    
    @Before
    public void before() throws IOException, InterruptedException {
        super.before();
        if (batchOptions.getExecutionMode() == ExecutionMode.IN_MEMORY) {
            batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY);
        }
        if (batchOptions.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
            batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC);
        }
    }
    
//    @Test
    public void testBatchRedirect() {
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 5; i++) {
            batch.getMap("" + i).fastPutAsync("" + i, i);
        }
        batch.execute();

        batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 1; i++) {
            batch.getMap("" + i).sizeAsync();
            batch.getMap("" + i).containsValueAsync("" + i);
            batch.getMap("" + i).containsValueAsync(i);
        }
        List<?> t = batch.execute();
        System.out.println(t);
    }

    @Test
    public void testConvertor() throws InterruptedException, ExecutionException {
        RBatch batch = redisson.createBatch(batchOptions);

        RFuture<Double> f1 = batch.getScoredSortedSet("myZKey").addScoreAsync("abc", 1d);
        RFuture<Void> f2 = batch.getBucket("test").setAsync("1");
        batch.execute();
        assertThat(f1.get()).isEqualTo(1d);
        assertThat(f2.get()).isNull();
        
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("myZKey");
        assertThat(set.getScore("abc")).isEqualTo(1d);
        RBucket<String> bucket = redisson.getBucket("test");
        assertThat(bucket.get()).isEqualTo("1");
        
        RBatch batch2 = redisson.createBatch(batchOptions);
        RFuture<Double> b2f1 = batch2.getScoredSortedSet("myZKey2").addScoreAsync("abc", 1d);
        RFuture<Double> b2f2 = batch2.getScoredSortedSet("myZKey2").addScoreAsync("abc", 1d);
        batch2.execute();
        
        assertThat(b2f1.get()).isEqualTo(1d);
        assertThat(b2f2.get()).isEqualTo(2d);
    }
    
    @Test(timeout = 22000)
    public void testPerformance() {
        RMap<String, String> map = redisson.getMap("map");
        Map<String, String> m = new HashMap<String, String>();
        for (int j = 0; j < 1000; j++) {
            m.put("" + j, "" + j);
        }
        map.putAll(m);
        
        for (int i = 0; i < 10000; i++) {
            RBatch rBatch = redisson.createBatch();
            RMapAsync<String, String> m1 = rBatch.getMap("map");
            m1.getAllAsync(m.keySet());
            try {
                rBatch.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void testConnectionLeakAfterError() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
                .setRetryInterval(1500)
                .setTimeout(3000)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        
        BatchOptions batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC);
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 400000; i++) {
            batch.getBucket("test").setAsync(123);
        }
        
        try {
            batch.execute();
            Assert.fail();
        } catch (Exception e) {
            // skip
        }
        
        redisson.getBucket("test3").set(4);
        assertThat(redisson.getBucket("test3").get()).isEqualTo(4);
        
        batch = redisson.createBatch(batchOptions);
        batch.getBucket("test1").setAsync(1);
        batch.getBucket("test2").setAsync(2);
        batch.execute();
        
        assertThat(redisson.getBucket("test1").get()).isEqualTo(1);
        assertThat(redisson.getBucket("test2").get()).isEqualTo(2);
        
        redisson.shutdown();
    }
    
    @Test
    public void testBigRequestAtomic() {
        batchOptions
                    .atomic()
                    .responseTimeout(15, TimeUnit.SECONDS)
                    .retryInterval(1, TimeUnit.SECONDS)
                    .retryAttempts(5);
        
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 100; i++) {
            batch.getBucket("" + i).setAsync(i);
            batch.getBucket("" + i).getAsync();
        }
        
        BatchResult<?> s = batch.execute();
        assertThat(s.getResponses().size()).isEqualTo(200);
    }

    @Test
    public void testSyncSlaves() throws FailedToStartRedisException, IOException, InterruptedException {
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
        RedissonClient redisson = Redisson.create(config);
        
        batchOptions
                .syncSlaves(1, 1, TimeUnit.SECONDS);
        
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 100; i++) {
            RMapAsync<String, String> map = batch.getMap("test");
            map.putAsync("" + i, "" + i);
        }

        BatchResult<?> result = batch.execute();
        assertThat(result.getResponses()).hasSize(100);
        assertThat(result.getSyncedSlaves()).isEqualTo(1);
        
        process.shutdown();
        redisson.shutdown();
    }

    @Test(timeout = 60000)
    public void testWriteTimeout() {
        Config config = createConfig();
        config.useSingleServer().setTimeout(15000);
        RedissonClient redisson = Redisson.create(config);

        RBatch batch = redisson.createBatch(batchOptions);
        RMapCacheAsync<String, String> map = batch.getMapCache("test");
        int total = 200000;
        for (int i = 0; i < total; i++) {
            RFuture<String> f = map.putAsync("" + i, "" + i, 5, TimeUnit.MINUTES);
            if (batchOptions.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
                f.syncUninterruptibly();
            }
        }
        
        batch.execute();
        assertThat(redisson.getMapCache("test").size()).isEqualTo(total);
        redisson.shutdown();
    }
    
    @Test
    public void testSkipResult() {
        Assume.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.2.0") > 0);
        
        batchOptions
                                            .skipResult();

        RBatch batch = redisson.createBatch(batchOptions);
        batch.getBucket("A1").setAsync("001");
        batch.getBucket("A2").setAsync("001");
        batch.getBucket("A3").setAsync("001");
        batch.getKeys().deleteAsync("A1");
        batch.getKeys().deleteAsync("A2");
        batch.execute();
        
        assertThat(redisson.getBucket("A1").isExists()).isFalse();
        assertThat(redisson.getBucket("A3").isExists()).isTrue();
    }
    
    @Test
    public void testBatchNPE() {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getBucket("A1").setAsync("001");
        batch.getBucket("A2").setAsync("001");
        batch.getBucket("A3").setAsync("001");
        batch.getKeys().deleteAsync("A1");
        batch.getKeys().deleteAsync("A2");
        batch.execute();
    }

    @Test
    public void testAtomic() {
        batchOptions
                                            .atomic();
        
        RBatch batch = redisson.createBatch(batchOptions);
        RFuture<Long> f1 = batch.getAtomicLong("A1").addAndGetAsync(1);
        RFuture<Long> f2 = batch.getAtomicLong("A2").addAndGetAsync(2);
        RFuture<Long> f3 = batch.getAtomicLong("A3").addAndGetAsync(3);
        RFuture<Long> d1 = batch.getKeys().deleteAsync("A1", "A2");
        BatchResult<?> f = batch.execute();
        
        List<Object> list = (List<Object>) f.getResponses();
        assertThat(list).containsExactly(1L, 2L, 3L, 2L);
        assertThat(f1.getNow()).isEqualTo(1);
        assertThat(f2.getNow()).isEqualTo(2);
        assertThat(f3.getNow()).isEqualTo(3);
        assertThat(d1.getNow()).isEqualTo(2);
    }
    
    @Test
    public void testAtomicSyncSlaves() throws FailedToStartRedisException, IOException, InterruptedException {
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
        RedissonClient redisson = Redisson.create(config);
        
        batchOptions
                                            .atomic()
                                            .syncSlaves(1, 1, TimeUnit.SECONDS);

        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 10; i++) {
            batch.getAtomicLong("{test}" + i).addAndGetAsync(i);
        }

        BatchResult<?> result = batch.execute();
        assertThat(result.getSyncedSlaves()).isEqualTo(1);
        int i = 0;
        for (Object res : result.getResponses()) {
            assertThat((Long)res).isEqualTo(i++);
        }
        
        process.shutdown();
        redisson.shutdown();
    }

    
    @Test
    public void testDifferentCodecs() {
        RBatch b = redisson.createBatch(batchOptions);
        b.getMap("test1").putAsync("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).putAsync("21", "3");
        RFuture<Object> val1 = b.getMap("test1").getAsync("1");
        RFuture<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).getAsync("21");
        b.execute();

        Assert.assertEquals("2", val1.getNow());
        Assert.assertEquals("3", val2.getNow());
    }

    @Test
    public void testDifferentCodecsAtomic() {
        RBatch b = redisson.createBatch(batchOptions.atomic());
        b.getMap("test1").putAsync("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).putAsync("21", "3");
        RFuture<Object> val1 = b.getMap("test1").getAsync("1");
        RFuture<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).getAsync("21");
        b.execute();

        Assert.assertEquals("2", val1.getNow());
        Assert.assertEquals("3", val2.getNow());
    }
    
    @Test
    public void testBatchList() {
        RBatch b = redisson.createBatch(batchOptions);
        RListAsync<Integer> listAsync = b.getList("list");
        for (int i = 1; i < 540; i++) {
            listAsync.addAsync(i);
        }
        BatchResult<?> res = b.execute();
        Assert.assertEquals(539, res.getResponses().size());
    }

    @Test
    public void testBatchBigRequest() {
        Config config = createConfig();
        config.useSingleServer().setTimeout(15000);
        RedissonClient redisson = Redisson.create(config);

        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 210; i++) {
            batch.getMap("test").fastPutAsync("1", "2");
            batch.getMap("test").fastPutAsync("2", "3");
            batch.getMap("test").putAsync("2", "5");
            batch.getAtomicLong("counter").incrementAndGetAsync();
            batch.getAtomicLong("counter").incrementAndGetAsync();
        }
        BatchResult<?> res = batch.execute();
        Assert.assertEquals(210*5, res.getResponses().size());
        
        redisson.shutdown();
    }

    @Test(expected=RedisException.class)
    public void testExceptionHandling() {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getMap("test").putAsync("1", "2");
        batch.getScript().evalAsync(Mode.READ_WRITE, "wrong_code", RScript.ReturnType.VALUE);
        batch.execute();
    }

    @Test(expected=IllegalStateException.class)
    public void testTwice() {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getMap("test").putAsync("1", "2");
        batch.execute();
        batch.execute();
    }


    @Test
    public void testEmpty() {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.execute();
    }
    
    @Test
    public void testOrdering() throws InterruptedException {
        ExecutorService e = Executors.newFixedThreadPool(16);
        final RBatch batch = redisson.createBatch(batchOptions);
        final AtomicLong index = new AtomicLong(-1);
        final List<RFuture<Long>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 500; i++) {
            futures.add(null);
        }
        for (int i = 0; i < 500; i++) {
            final int j = i;
            e.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (RedissonBatchTest.this) {
                        int i = (int) index.incrementAndGet();
                        int ind = j % 3;
                        RFuture<Long> f1 = batch.getAtomicLong("test" + ind).addAndGetAsync(j);
                        futures.set(i, f1);
                    }
                }
            });
        }
        e.shutdown();
        Assert.assertTrue(e.awaitTermination(30, TimeUnit.SECONDS));
        List<?> s = batch.execute();
        
        int i = 0;
        for (Object element : s) {
            RFuture<Long> a = futures.get(i);
            Assert.assertEquals(a.getNow(), element);
            i++;
        }
    }

    @Test
    public void test() {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getMap("test").fastPutAsync("1", "2");
        batch.getMap("test").fastPutAsync("2", "3");
        batch.getMap("test").putAsync("2", "5");
        batch.getAtomicLong("counter").incrementAndGetAsync();
        batch.getAtomicLong("counter").incrementAndGetAsync();

        List<?> res = batch.execute();
        Assert.assertEquals(5, res.size());
        Assert.assertTrue((Boolean)res.get(0));
        Assert.assertTrue((Boolean)res.get(1));
        Assert.assertEquals("3", res.get(2));
        Assert.assertEquals(1L, res.get(3));
        Assert.assertEquals(2L, res.get(4));

        Map<String, String> map = new HashMap<String, String>();
        map.put("1", "2");
        map.put("2", "5");
        Assert.assertEquals(map, redisson.getMap("test"));

        Assert.assertEquals(redisson.getAtomicLong("counter").get(), 2);
    }

}
