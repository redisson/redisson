package org.redisson;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.api.*;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.client.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBatchTest extends BaseTest {

    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY)},
            {BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC)}
            });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSlotMigrationInCluster(BatchOptions batchOptions) throws Exception {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slot1)
                .addNode(master2, slot2)
                .addNode(master3, slot3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setScanInterval(1000)
        .setSubscriptionMode(SubscriptionMode.MASTER)
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisClientConfig cfg = new RedisClientConfig();
        cfg.setAddress(process.getNodes().iterator().next().getRedisServerAddressAndPort());
        RedisClient c = RedisClient.create(cfg);
        RedisConnection cc = c.connect();
        List<ClusterNodeInfo> mastersList = cc.sync(RedisCommands.CLUSTER_NODES);
        mastersList = mastersList.stream().filter(i -> i.containsFlag(ClusterNodeInfo.Flag.MASTER)).collect(Collectors.toList());
        c.shutdown();

        ClusterNodeInfo destination = mastersList.stream().filter(i -> i.getSlotRanges().iterator().next().getStartSlot() != 10922).findAny().get();
        ClusterNodeInfo source = mastersList.stream().filter(i -> i.getSlotRanges().iterator().next().getStartSlot() == 10922).findAny().get();

        RedisClientConfig sourceCfg = new RedisClientConfig();
        sourceCfg.setAddress(source.getAddress());
        RedisClient sourceClient = RedisClient.create(sourceCfg);
        RedisConnection sourceConnection = sourceClient.connect();

        RedisClientConfig destinationCfg = new RedisClientConfig();
        destinationCfg.setAddress(destination.getAddress());
        RedisClient destinationClient = RedisClient.create(destinationCfg);
        RedisConnection destinationConnection = destinationClient.connect();

        String lockName = "test{kaO}";

        RBatch batch = redisson.createBatch(batchOptions);
        List<RFuture<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RFuture<Boolean> f = batch.getMap(lockName).fastPutAsync("" + i, i);
            futures.add(f);
        }

        destinationConnection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "IMPORTING", source.getNodeId());
        sourceConnection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "MIGRATING", destination.getNodeId());

        List<String> keys = sourceConnection.sync(RedisCommands.CLUSTER_GETKEYSINSLOT, source.getSlotRanges().iterator().next().getStartSlot(), 100);
        List<Object> params = new ArrayList<Object>();
        params.add(destination.getAddress().getHost());
        params.add(destination.getAddress().getPort());
        params.add("");
        params.add(0);
        params.add(2000);
        params.add("KEYS");
        params.addAll(keys);
        sourceConnection.async(RedisCommands.MIGRATE, params.toArray());

        for (ClusterNodeInfo node : mastersList) {
            RedisClientConfig cc1 = new RedisClientConfig();
            cc1.setAddress(node.getAddress());
            RedisClient ccc = RedisClient.create(cc1);
            RedisConnection connection = ccc.connect();
            connection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "NODE", destination.getNodeId());
            ccc.shutdownAsync();
        }

        Thread.sleep(2000);

        batch.execute();

        futures.forEach(f -> assertThat(f.awaitUninterruptibly(1)).isTrue());

        sourceClient.shutdown();
        destinationClient.shutdown();
        redisson.shutdown();
        process.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testConnectionLeak(BatchOptions batchOptions) throws Exception {
        Assumptions.assumeTrue(batchOptions.getExecutionMode() == ExecutionMode.IN_MEMORY);

        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(1000);

        Config config = new Config();
        config.useClusterServers()
				.setConnectTimeout(500).setPingConnectionInterval(2000)
                .setMasterConnectionMinimumIdleSize(1)
                .setMasterConnectionPoolSize(1)
                .setSlaveConnectionMinimumIdleSize(1)
                .setSlaveConnectionPoolSize(1)
                .setTimeout(100)
                .setRetryAttempts(0)
                .setRetryInterval(20)
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

		ExecutorService executorService = Executors.newFixedThreadPool(5);
		AtomicInteger counter = new AtomicInteger(5*150);
		AtomicBoolean hasErrors = new AtomicBoolean();
		for (int i = 0; i < 5; i++) {
			executorService.submit(() -> {
				for (int j = 0 ; j < 150; j++) {
					executeBatch(redisson, batchOptions).whenComplete((r, e) -> {
                        if (e != null) {
                            hasErrors.set(true);
                        }
                        counter.decrementAndGet();
					});
				}
			});
		}

		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> counter.get() == 0);
		Assertions.assertThat(hasErrors).isTrue();

		executeBatch(redisson, batchOptions).syncUninterruptibly();

        redisson.shutdown();
        process.shutdown();
    }

	public RFuture<BatchResult<?>> executeBatch(RedissonClient client, BatchOptions batchOptions) {
		RBatch batch = client.createBatch(batchOptions);
		for (int i = 0; i < 10; i++) {
			String key = "" + i;
			batch.getBucket(key).getAsync();
		}
		return batch.executeAsync();
	}

    @ParameterizedTest
    @MethodSource("data")
    public void testConvertor(BatchOptions batchOptions) throws InterruptedException, ExecutionException {
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
    
    @ParameterizedTest
    @MethodSource("data")
    public void testPerformance() {
        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(20), () -> {
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
        });
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testConnectionLeakAfterError() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
                .setRetryInterval(100)
                .setTimeout(200)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        
        BatchOptions batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC);
        RBatch batch1 = redisson.createBatch(batchOptions);
        for (int i = 0; i < 25000; i++) {
            batch1.getBucket("test").setAsync(123);
        }

        Assertions.assertThatThrownBy(() -> {
            batch1.execute();
        });

        redisson.getBucket("test3").set(4);
        assertThat(redisson.getBucket("test3").get()).isEqualTo(4);
        
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getBucket("test1").setAsync(1);
        batch.getBucket("test2").setAsync(2);
        batch.execute();
        
        assertThat(redisson.getBucket("test1").get()).isEqualTo(1);
        assertThat(redisson.getBucket("test2").get()).isEqualTo(2);
        
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
        
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 100; i++) {
            batch.getBucket("" + i).setAsync(i);
            batch.getBucket("" + i).getAsync();
        }
        
        BatchResult<?> s = batch.execute();
        assertThat(s.getResponses().size()).isEqualTo(200);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSyncSlavesWait(BatchOptions batchOptions) {
        Config config = createConfig();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);

        try {
                    batchOptions
                    .skipResult()
                    .syncSlaves(2, 1, TimeUnit.SECONDS);
            RBatch batch = redisson.createBatch(batchOptions);
            RBucketAsync<Integer> bucket = batch.getBucket("1");
            bucket.setAsync(1);
            batch.execute();
            String[] t = redisson.getKeys().getKeysStreamByPattern("*").toArray(String[]::new);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

    @ParameterizedTest
    @MethodSource("data")
    public void testWriteTimeout(BatchOptions batchOptions) {
        Config config = createConfig();
        config.useSingleServer().setRetryInterval(700).setTimeout(1500);
        RedissonClient redisson = Redisson.create(config);

        RBatch batch = redisson.createBatch(batchOptions);
        RMapCacheAsync<String, String> map = batch.getMapCache("test");
        int total = 10000;
        for (int i = 0; i < total; i++) {
            RFuture<String> f = map.putAsync("" + i, "" + i, 5, TimeUnit.MINUTES);
            if (batchOptions.getExecutionMode() == ExecutionMode.REDIS_WRITE_ATOMIC) {
                f.syncUninterruptibly();
            }
        }
        
        long s = System.currentTimeMillis();
        batch.execute();
        long executionTime = System.currentTimeMillis() - s;
        if (batchOptions.getExecutionMode() == ExecutionMode.IN_MEMORY) {
            assertThat(executionTime).isLessThan(1000);
        } else {
            assertThat(executionTime).isLessThan(300);
        }
        assertThat(redisson.getMapCache("test").size()).isEqualTo(total);
        redisson.shutdown();
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testSkipResult(BatchOptions batchOptions) {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.2.0") > 0);
        
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
    
    @ParameterizedTest
    @MethodSource("data")
    public void testBatchNPE(BatchOptions batchOptions) {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getBucket("A1").setAsync("001");
        batch.getBucket("A2").setAsync("001");
        batch.getBucket("A3").setAsync("001");
        batch.getKeys().deleteAsync("A1");
        batch.getKeys().deleteAsync("A2");
        batch.execute();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testAtomic(BatchOptions batchOptions) {
        batchOptions
                                            .executionMode(ExecutionMode.IN_MEMORY_ATOMIC);
        
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
        RedissonClient redisson = Redisson.create(config);
        
        batchOptions
                                            .executionMode(ExecutionMode.IN_MEMORY_ATOMIC)
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

    
    @ParameterizedTest
    @MethodSource("data")
    public void testDifferentCodecs(BatchOptions batchOptions) {
        RBatch b = redisson.createBatch(batchOptions);
        b.getMap("test1").putAsync("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).putAsync("21", "3");
        RFuture<Object> val1 = b.getMap("test1").getAsync("1");
        RFuture<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).getAsync("21");
        b.execute();

        org.junit.jupiter.api.Assertions.assertEquals("2", val1.getNow());
        org.junit.jupiter.api.Assertions.assertEquals("3", val2.getNow());
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDifferentCodecsAtomic(BatchOptions batchOptions) {
        RBatch b = redisson.createBatch(batchOptions.executionMode(ExecutionMode.IN_MEMORY_ATOMIC));
        b.getMap("test1").putAsync("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).putAsync("21", "3");
        RFuture<Object> val1 = b.getMap("test1").getAsync("1");
        RFuture<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).getAsync("21");
        b.execute();

        org.junit.jupiter.api.Assertions.assertEquals("2", val1.getNow());
        org.junit.jupiter.api.Assertions.assertEquals("3", val2.getNow());
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testBatchList(BatchOptions batchOptions) {
        RBatch b = redisson.createBatch(batchOptions);
        RListAsync<Integer> listAsync = b.getList("list");
        for (int i = 1; i < 540; i++) {
            listAsync.addAsync(i);
        }
        BatchResult<?> res = b.execute();
        org.junit.jupiter.api.Assertions.assertEquals(539, res.getResponses().size());
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testBatchCancel() {
        RedissonClient redisson = createInstance();
        
        BatchOptions batchOptions = BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY);
        RBatch batch = redisson.createBatch(batchOptions);
        for (int i = 0; i < 10; i++) {
            RFuture<Void> f = batch.getBucket("test").setAsync(123);
            assertThat(f.cancel(true)).isTrue();
        }
        
        BatchResult<?> res = batch.execute();
        org.junit.jupiter.api.Assertions.assertEquals(0, res.getResponses().size());
        
        RBatch b2 = redisson.createBatch(batchOptions);
        RListAsync<Integer> listAsync2 = b2.getList("list");
        for (int i = 0; i < 6; i++) {
            RFuture<Boolean> t = listAsync2.addAsync(i);
            assertThat(t.cancel(true)).isTrue();
        }

        RFuture<BatchResult<?>> res2 = b2.executeAsync();
        assertThat(res2.cancel(true)).isFalse();
        org.junit.jupiter.api.Assertions.assertEquals(0, res.getResponses().size());
        
        redisson.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testBatchPing(BatchOptions batchOptions) throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer().setPingConnectionInterval(100);
        RedissonClient redisson =  Redisson.create(config);

        RBatch batch = redisson.createBatch(batchOptions);
        batch.getBucket("test").trySetAsync("1232");
        Thread.sleep(500);
        BatchResult<?> r = batch.execute();
        assertThat((List<Object>)r.getResponses()).containsExactly(true);

        redisson.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testShutdownTimeout(BatchOptions batchOptions) {
        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofMillis(500), () -> {
            RedissonClient redisson = createInstance();

            RBatch batch = redisson.createBatch(batchOptions);
            for (int i = 0; i < 10; i++) {
                RFuture<Void> f = batch.getBucket("test").setAsync(123);
            }
            batch.execute();
            redisson.shutdown();
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testBatchBigRequest(BatchOptions batchOptions) {
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
        org.junit.jupiter.api.Assertions.assertEquals(210*5, res.getResponses().size());
        
        redisson.shutdown();
    }

//    @ParameterizedTest
//@MethodSource("data")
//(expected=RedisException.class)
//    public void testExceptionHandling() {
//        RBatch batch = redisson.createBatch(batchOptions);
//        batch.getMap("test").putAsync("1", "2");
//        batch.getScript().evalAsync(Mode.READ_WRITE, "wrong_code", RScript.ReturnType.VALUE);
//        batch.execute();
//    }
//
//    @ParameterizedTest
//@MethodSource("data")
//(expected=IllegalStateException.class)
//    public void testTwice() {
//        RBatch batch = redisson.createBatch(batchOptions);
//        batch.getMap("test").putAsync("1", "2");
//        batch.execute();
//        batch.execute();
//    }


    @ParameterizedTest
@MethodSource("data")
    public void testEmpty(BatchOptions batchOptions) {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.execute();
    }
    
    @ParameterizedTest
@MethodSource("data")

    public void testOrdering(BatchOptions batchOptions) throws InterruptedException {
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
        org.junit.jupiter.api.Assertions.assertTrue(e.awaitTermination(30, TimeUnit.SECONDS));
        BatchResult<?> s = batch.execute();
        
        int i = 0;
        for (Object element : s.getResponses()) {
            RFuture<Long> a = futures.get(i);
            org.junit.jupiter.api.Assertions.assertEquals(a.getNow(), element);
            i++;
        }
    }

    @ParameterizedTest
@MethodSource("data")

    public void test(BatchOptions batchOptions) {
        RBatch batch = redisson.createBatch(batchOptions);
        batch.getMap("test").fastPutAsync("1", "2");
        batch.getMap("test").fastPutAsync("2", "3");
        batch.getMap("test").putAsync("2", "5");
        batch.getAtomicLong("counter").incrementAndGetAsync();
        batch.getAtomicLong("counter").incrementAndGetAsync();

        List<?> res = batch.execute().getResponses();
        org.junit.jupiter.api.Assertions.assertEquals(5, res.size());
        org.junit.jupiter.api.Assertions.assertTrue((Boolean)res.get(0));
        org.junit.jupiter.api.Assertions.assertTrue((Boolean)res.get(1));
        org.junit.jupiter.api.Assertions.assertEquals("3", res.get(2));
        org.junit.jupiter.api.Assertions.assertEquals(1L, res.get(3));
        org.junit.jupiter.api.Assertions.assertEquals(2L, res.get(4));

        Map<String, String> map = new HashMap<String, String>();
        map.put("1", "2");
        map.put("2", "5");
        org.junit.jupiter.api.Assertions.assertEquals(map, redisson.getMap("test"));

        org.junit.jupiter.api.Assertions.assertEquals(redisson.getAtomicLong("counter").get(), 2);
    }

}
