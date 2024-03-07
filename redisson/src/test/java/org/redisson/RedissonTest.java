package org.redisson;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import net.bytebuddy.utility.RandomString;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisMaster;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.api.redisnode.RedisSingle;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.client.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.cluster.ClusterNodeInfo.Flag;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.Credentials;
import org.redisson.config.CredentialsResolver;
import org.redisson.connection.CRC16;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTest extends RedisDockerTest {

    @Test
    public void testVirtualThreads() {
        Config c = redisson.getConfig();
        c.setNettyExecutor(Executors.newVirtualThreadPerTaskExecutor());

        RedissonClient r = Redisson.create(c);
        RBucket<String> b = r.getBucket("test");
        b.set("1");
        assertThat(b.get()).isEqualTo("1");
        r.shutdown();
    }

    @Test
    public void testStopThreads() throws IOException {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();

        List<String> cfgs = Arrays.asList("{\"clusterServersConfig\":{\"nodeAddresses\": []}}",
                                          "{\"singleServerConfig\":{\"address\": \"\"}}",
                                          "{\"replicatedServersConfig\":{\"nodeAddresses\": []}}",
                                          "{\"sentinelServersConfig\":{\"sentinelAddresses\": []}}",
                                          "{\"masterSlaveServersConfig\":{\"masterAddress\": \"\"}}");
        for (String cfg : cfgs) {
            ConfigSupport support = new ConfigSupport();
            Config config = support.fromJSON(cfg, Config.class);

            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                Redisson.create(config);
            });
        }

        Set<Thread> newThreads = Thread.getAllStackTraces().keySet();
        newThreads.removeAll(threads);
        newThreads.removeIf(r -> r.getName().contains("Jndi-Dns-address-change-listener")
                                    || r.getName().contains("globalEventExecutor"));
        assertThat(newThreads).isEmpty();
    }

//    @Test
    public void testLeak() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer()
              .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient localRedisson = Redisson.create(config);

        String key = RandomString.make(120);
        for (int i = 0; i < 500; i++) {
            RMapCache<String, String> cache = localRedisson.getMapCache("mycache");
            RLock keyLock = cache.getLock(key);
            keyLock.lockInterruptibly(10, TimeUnit.SECONDS);
            try {
                cache.get(key);
                cache.put(key, RandomString.make(4*1024*1024), 5, TimeUnit.SECONDS);
            } finally {
                if (keyLock != null) {
                    keyLock.unlock();
                }
            }
        }
    }

    @Test
    public void testLazyInitialization() {
        Config config = new Config();
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:4431");

        RedissonClient redisson = Redisson.create(config);
        assertThat(redisson).isNotNull();
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            redisson.getStream("test").createGroup(StreamCreateGroupArgs.name("test").makeStream());
        });
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            redisson.getStream("test").createGroup(StreamCreateGroupArgs.name("test").makeStream());
        });

        FixedHostPortGenericContainer c = new FixedHostPortGenericContainer("redis:7.2")
                .withFixedExposedPort(4431, 6379);
        c.start();

        redisson.getStream("test").createGroup(StreamCreateGroupArgs.name("test").makeStream());

        redisson.shutdown();
        c.stop();
    }

    @Test
    public void testPerformance() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer().setConnectionPoolSize(1).setConnectionMinimumIdleSize(1);
        RedissonClient inst = Redisson.create(config);
        RAtomicLong s = inst.getAtomicLong("counter");

        ExecutorService ex = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 200_000; i++) {
            ex.execute(() -> {
                s.incrementAndGet();
            });
        }

        ex.shutdown();
        assertThat(ex.awaitTermination(12, TimeUnit.SECONDS)).isTrue();
        assertThat(s.get()).isEqualTo(200_000L);
        inst.shutdown();
    }

    @Test
    public void testResponseHandling2() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
                .setTimeout(10)
                .setRetryAttempts(0)
                .setConnectionPoolSize(1)
                .setConnectionMinimumIdleSize(1)
                .setPingConnectionInterval(0);

        RedissonClient redisson = Redisson.create(config);

        RBucket<String> bucket1 = redisson.getBucket("name1");
        RBucket<String> bucket2 = redisson.getBucket("name2");

        bucket1.set("val1");
        bucket2.set("val2");

        ExecutorService executor1 = Executors.newFixedThreadPool(16);
        ExecutorService executor2 = Executors.newFixedThreadPool(16);

        AtomicBoolean hasError = new AtomicBoolean();
        for (int i = 0; i < 100000; i++) {
            executor1.submit(() -> {
                String get = bucket1.get();
                if (get.equals("val2")) {
                    hasError.set(true);
                }
            });

            executor2.submit(() -> {
                String get = bucket2.get();
                if (get.equals("val1")) {
                    hasError.set(true);
                }
            });
        }

        executor1.shutdown();
        assertThat(executor1.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        executor2.shutdown();
        assertThat(executor2.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(hasError).isFalse();

        redisson.shutdown();
    }


    @Test
    public void testResponseHandling() throws InterruptedException {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            list.add(i);
        }

        RList<Integer> l = redisson.getList("test");
        l.addAll(list);
        ExecutorService e = Executors.newFixedThreadPool(8);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            e.submit(() -> {
                for (int k = 0; k < list.size(); k++) {
                    assertThat(l.get(k)).isEqualTo(k);
                    counter.incrementAndGet();
                }
            });
        }
        e.shutdown();
        assertThat(e.awaitTermination(12, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(list.size() * 100);
    }
    
    @Test
    public void testDecoderError() {
        redisson.getBucket("testbucket", new StringCodec()).set("{INVALID JSON!}");

        for (int i = 0; i < 256; i++) {
          try {
              redisson.getBucket("testbucket", new JsonJacksonCodec()).get();
              Assertions.fail();
          } catch (Exception e) {
              // skip
          }
        }

        redisson.getBucket("testbucket2").set("should work");
    }
    
    @Test
    public void testSmallPool() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(3)
                .setConnectionPoolSize(3);

        RedissonClient localRedisson = Redisson.create(config);
        
        RMap<String, String> map = localRedisson.getMap("test");
        
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2);
        long start = System.currentTimeMillis();
        int iterations = 500_000;
        for (int i = 0; i < iterations; i++) {
            final int j = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    map.put("" + j, "" + j);
                }
            });
        }
        
        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.MINUTES));
        
        assertThat(map.size()).isEqualTo(iterations);
        
        localRedisson.shutdown();
    }
    
    public static class Dummy {
        private String field;
    }
    
    @Test
    public void testNextResponseAfterDecoderError() {
        Config config = createConfig();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        
        setJSONValue(redisson, "test1", "test1");
        setStringValue(redisson, "test2", "test2");
        setJSONValue(redisson, "test3", "test3");
        try {
            RBuckets buckets = redisson.getBuckets(new JsonJacksonCodec());
            buckets.get("test2", "test1");
        } catch (Exception e) {
            // skip
        }
        assertThat(getStringValue(redisson, "test3")).isEqualTo("\"test3\"");
        
        redisson.shutdown();
    }

    public void setJSONValue(RedissonClient redisson, String key, Object t) {
        RBucket<Object> test1 = redisson.getBucket(key, new JsonJacksonCodec());
        test1.set(t);
    }

    public void setStringValue(RedissonClient redisson, String key, Object t) {
        RBucket<Object> test1 = redisson.getBucket(key, new StringCodec());
        test1.set(t);
    }


    public Object getStringValue(RedissonClient redisson, String key) {
        RBucket<Object> test1 = redisson.getBucket(key, new StringCodec());
        return test1.get();
    }

    @Test
    public void testSer() {
        Config config = createConfig();
        config.setCodec(new SerializationCodec());

        RedissonClient r = Redisson.create(config);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            r.getMap("test").put("1", new Dummy());
        });
        r.shutdown();
    }

    @Test
    public void testMemoryScript() {
        testWithParams(redissonClient -> {
            Config c = redissonClient.getConfig();
            c.useSingleServer().setTimeout(100000);

            Assertions.assertThrows(RedisOutOfMemoryException.class, () -> {
                RedissonClient r = null;
                try {
                    r = Redisson.create(c);
                    for (int i = 0; i < 10000; i++) {
                        r.getMap("test").put("" + i, "" + i);
                    }
                } finally {
                    r.shutdown();
                }
            });
        }, "--maxmemory", "1mb");

    }

    @Test
    public void testMemoryCommand() {
        testWithParams(redissonClient -> {
            Config c = redissonClient.getConfig();
            c.useSingleServer().setTimeout(100000);

            Assertions.assertThrows(RedisOutOfMemoryException.class, () -> {
                RedissonClient r = null;
                try {
                    r = Redisson.create(c);
                    for (int i = 0; i < 10000; i++) {
                        r.getMap("test").fastPut("" + i, "" + i);
                    }
                } finally {
                    r.shutdown();
                }
            });
        }, "--maxmemory", "1mb");
    }

    @Test
    public void testConfigValidation() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Config redissonConfig = createConfig();
            redissonConfig.useSingleServer()
                    .setConnectionPoolSize(2);
            Redisson.create(redissonConfig);
        });
    }
    
    @Test
    public void testConnectionListener() {
        GenericContainer<?> redis = createRedis();
        redis.start();

        final AtomicInteger connectCounter = new AtomicInteger();
        final AtomicInteger disconnectCounter = new AtomicInteger();

        Config config = createConfig(redis);
        config.setConnectionListener(new ConnectionListener() {

            @Override
            public void onDisconnect(InetSocketAddress addr) {
            }

            @Override
            public void onDisconnect(InetSocketAddress addr, NodeType nodeType) {
                assertThat(addr).isEqualTo(new InetSocketAddress(redis.getHost(), redis.getFirstMappedPort()));
                assertThat(nodeType).isEqualTo(NodeType.MASTER);
                disconnectCounter.incrementAndGet();
            }

            @Override
            public void onConnect(InetSocketAddress addr) {
            }

            @Override
            public void onConnect(InetSocketAddress addr, NodeType nodeType) {
                assertThat(addr).isEqualTo(new InetSocketAddress(redis.getHost(), redis.getFirstMappedPort()));
                assertThat(nodeType).isEqualTo(NodeType.MASTER);
                connectCounter.incrementAndGet();
            }
        });

        RedissonClient r = Redisson.create(config);

        r.getBucket("1").get();
        redis.setPortBindings(Arrays.asList(redis.getFirstMappedPort() + ":6379"));
        redis.stop();

        await().atMost(2, TimeUnit.SECONDS).until(() -> disconnectCounter.get() == 1);

        try {
            r.getBucket("1").get();
        } catch (Exception e) {
            // skip
        }

        assertThat(connectCounter.get()).isEqualTo(1);
        assertThat(disconnectCounter.get()).isEqualTo(1);

        redis.start();

        r.getBucket("1").get();

        assertThat(connectCounter.get()).isEqualTo(2);
        assertThat(disconnectCounter.get()).isEqualTo(1);

        r.shutdown();
        redis.stop();
    }

    public static class SlowCodec extends BaseCodec {

        private final Encoder encoder = new Encoder() {
            @Override
            public ByteBuf encode(Object in) throws IOException {
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                out.writeCharSequence(in.toString(), CharsetUtil.UTF_8);
                return out;
            }
        };

        public final Decoder<Object> decoder = new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                String str = buf.toString(CharsetUtil.UTF_8);
                buf.readerIndex(buf.readableBytes());
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return str;
            }
        };

        public SlowCodec() {
        }
        
        public SlowCodec(ClassLoader classLoader) {
            this();
        }


        @Override
        public Decoder<Object> getValueDecoder() {
            return decoder;
        }

        @Override
        public Encoder getValueEncoder() {
            return encoder;
        }

    }

    @Test
    public void testReconnection() {
        Config config = redisson.getConfig();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(20)
                .setConnectionPoolSize(20)
                .setSubscriptionConnectionMinimumIdleSize(20)
                .setSubscriptionConnectionPoolSize(20);

        RedissonClient r = Redisson.create(config);
        
        r.getBucket("myBucket").set(1);
        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);

        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

        AtomicBoolean hasError = new AtomicBoolean();
        try {
            r.getBucket("myBucket").get();
        } catch (Exception e) {
            // skip error
            hasError.set(true);
        }

        assertThat(hasError.get()).isTrue();

        REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();

        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);

        r.shutdown();
    }


    @Test
    public void testSave() throws InterruptedException {
        Instant s2 = Instant.now();
        Thread.sleep(1000);
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        RedisMaster node = nodes.getInstance();

        Instant time1 = node.getLastSaveTime();
        assertThat(time1).isNotNull();
        node.save();
        Instant time2 = node.getLastSaveTime();
        assertThat(time2.isAfter(s2)).isTrue();
        node.bgSave();
        node.bgRewriteAOF();
    }

    @Test
    public void testShutdown() {
        RedissonClient r = createInstance();
        Assertions.assertFalse(r.isShuttingDown());
        Assertions.assertFalse(r.isShutdown());
        r.shutdown();
        Assertions.assertTrue(r.isShuttingDown());
        Assertions.assertTrue(r.isShutdown());
    }

    @Test
    public void testCredentials() {
        withRedisParams(config -> {
            config.useSingleServer()
                    .setCredentialsResolver(new CredentialsResolver() {
                        @Override
                        public CompletionStage<Credentials> resolve(InetSocketAddress address) {
                            return CompletableFuture.completedFuture(new Credentials(null, "1234"));
                        }
                    });

            RedissonClient redisson = Redisson.create(config);
            RBucket<String> b = redisson.getBucket("test");
            b.set("123");

            redisson.shutdown();
        }, "--requirepass", "1234");
    }

    @Test
    public void testCommandMapper() {
        Config c = createConfig();
        c.useSingleServer().setCommandMapper(n -> {
            if (n.equals("EVAL")) {
                return "EVAL_111";
            }
            return n;
        });
        RedissonClient redisson = Redisson.create(c);
        RBucket<String> b = redisson.getBucket("test");
        RedisException e = Assertions.assertThrows(RedisException.class, () -> {
            b.compareAndSet("test", "v1");
        });
        assertThat(e.getMessage()).startsWith("ERR unknown command 'EVAL_111'");

        redisson.shutdown();
    }

    @Test
    public void testURIPassword() {
        withRedisParams(config -> {
            RedisURI ur = new RedisURI(config.useSingleServer().getAddress());
            config.useSingleServer()
                    .setAddress("redis://:1234@" + ur.getHost() + ":" + ur.getPort());
            RedissonClient redisson = Redisson.create(config);
            RBucket<String> b = redisson.getBucket("test");
            b.set("123");

            redisson.shutdown();
        }, "--requirepass", "1234");
    }

    @Test
    public void testSentinelStartupWithPassword() throws Exception {
        withSentinel((nodes, config) -> {
            long t = System.currentTimeMillis();
            RedissonClient redisson = Redisson.create(config);
            assertThat(System.currentTimeMillis() - t).isLessThan(2000L);
            redisson.shutdown();
        }, 2, "123");
    }

    @Test
    public void testSentinelStartup() throws Exception {
        withSentinel((nodes, config) -> {
            long t = System.currentTimeMillis();
            RedissonClient redisson = Redisson.create(config);
            assertThat(System.currentTimeMillis() - t).isLessThan(2000L);
            redisson.shutdown();
        }, 2);
    }
    
    @Test
    public void testSingleConfigYAML() throws IOException {
        RedissonClient r = createInstance();
        String t = r.getConfig().toYAML();
        Config c = Config.fromYAML(t);
        assertThat(c.toYAML()).isEqualTo(t);
    }

    @Test
    public void testSentinelYAML() throws IOException {
        Config c2 = new Config();
        c2.useSentinelServers().addSentinelAddress("redis://123.1.1.1:1231").setMasterName("mymaster");
        String t = c2.toYAML();
        System.out.println(t);
        Config c = Config.fromYAML(t);
        assertThat(c.toYAML()).isEqualTo(t);
    }

    @Test
    public void testMasterSlaveConfigYAML() throws IOException {
        Config c2 = new Config();
        c2.useMasterSlaveServers().setMasterAddress("redis://123.1.1.1:1231").addSlaveAddress("redis://82.12.47.12:1028", "redis://82.12.47.14:1028");
        String t = c2.toYAML();
        Config c = Config.fromYAML(t);
        assertThat(c.toYAML()).isEqualTo(t);
    }

    @Test
    public void testEvalCache() {
        testInCluster(redissonClient -> {
            Config config = redissonClient.getConfig();
            config.setUseScriptCache(true);

            RedissonClient redisson = Redisson.create(config);

            RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
            t.add(4, "40");
            t.add(2, "20");
            t.add(1, "10", 1, TimeUnit.SECONDS);

            t.size();

            redisson.shutdown();
        });
    }

    @Test
    public void testMovedRedirectInCluster() throws Exception {
        withNewCluster(redissonClient -> {
            Config config = redissonClient.getConfig();
            config.useClusterServers()
                    .setScanInterval(100000);

            RedissonClient redisson = Redisson.create(config);

            Collection<RedisClusterMaster> ms = redisson.getRedisNodes(RedisNodes.CLUSTER).getMasters();
            RedisClusterMaster m = ms.iterator().next();
            RedisURI a = config.useClusterServers().getNatMapper().map(
                    new RedisURI("redis://" + m.getAddr().getHostString() + ":" + m.getAddr().getPort()));
            RedisClientConfig cfg = new RedisClientConfig();
            cfg.setAddress(a);
            RedisClient c = RedisClient.create(cfg);
            RedisConnection cc = c.connect();
            List<ClusterNodeInfo> cn = cc.sync(RedisCommands.CLUSTER_NODES);
            c.shutdownAsync();
            cn = cn.stream().filter(i -> i.containsFlag(Flag.MASTER)).collect(Collectors.toList());
            Iterator<ClusterNodeInfo> nodesIter = cn.iterator();


            ClusterNodeInfo source = nodesIter.next();
            ClusterNodeInfo destination = nodesIter.next();

            RedisClientConfig sourceCfg = new RedisClientConfig();
            sourceCfg.setAddress(config.useClusterServers().getNatMapper().map(source.getAddress()));
            RedisClient sourceClient = RedisClient.create(sourceCfg);
            RedisConnection sourceConnection = sourceClient.connect();

            RedisClientConfig destinationCfg = new RedisClientConfig();
            destinationCfg.setAddress(config.useClusterServers().getNatMapper().map(destination.getAddress()));
            RedisClient destinationClient = RedisClient.create(destinationCfg);
            RedisConnection destinationConnection = destinationClient.connect();

            String key = null;
            int slot = 0;
            for (int i = 0; i < 100000; i++) {
                key = "" + i;
                slot = CRC16.crc16(key.getBytes()) % MasterSlaveConnectionManager.MAX_SLOT;
                if (source.getSlotRanges().iterator().next().getStartSlot() == slot) {
                    break;
                }
            }

            redisson.getBucket(key).set("123");

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

            for (ClusterNodeInfo node : cn) {
                RedisClientConfig cc1 = new RedisClientConfig();
                cc1.setAddress(config.useClusterServers().getNatMapper().map(node.getAddress()));
                RedisClient ccc = RedisClient.create(cc1);
                RedisConnection connection = ccc.connect();
                connection.sync(RedisCommands.CLUSTER_SETSLOT, slot, "NODE", destination.getNodeId());
                ccc.shutdownAsync();
            }

            redisson.getBucket(key).set("123");
            redisson.getBucket(key).get();

            sourceClient.shutdown();
            destinationClient.shutdown();
            redisson.shutdown();
        });
    }


    @Test
    public void testSingleConnectionFail() {
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://127.99.0.1:1111");
            Redisson.create(config);

            Thread.sleep(1500);
        });
    }

    @Test
    public void testClusterConnectionFail() {
        Awaitility.await().atLeast(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Assertions.assertThrows(RedisConnectionException.class, () -> {
                Config config = new Config();
                config.useClusterServers().addNodeAddress("redis://127.99.0.1:1111");
                Redisson.create(config);
            });
        });
    }

    @Test
    public void testReplicatedConnectionFail() {
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Config config = new Config();
            config.useReplicatedServers().addNodeAddress("redis://127.99.0.1:1111");
            Redisson.create(config);

            Thread.sleep(1500);
        });
    }

    @Test
    public void testMasterSlaveConnectionFail() {
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Config config = new Config();
            config.useMasterSlaveServers()
                    .setMasterAddress("redis://127.99.0.1:1111")
                    .addSlaveAddress("redis://127.99.0.2:1111");
            Redisson.create(config);

            Thread.sleep(1500);
        });
    }

    @Test
    public void testMasterSlaveConnectionFail2() {
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Config config = new Config();
            config.useMasterSlaveServers()
                    .setMasterAddress("redis://gadfgdfgdsfg:1111")
                    .addSlaveAddress("redis://asdfasdfsdfaasdf:1111");
            Redisson.create(config);

            Thread.sleep(1500);
        });
    }

    @Test
    public void testSentinelConnectionFail() {
        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Config config = new Config();
            config.useSentinelServers().addSentinelAddress("redis://127.99.0.1:1111").setMasterName("test");
            Redisson.create(config);

            Thread.sleep(1500);
        });
    }

    @Test
    public void testManyConnections() {
        Config redisConfig = createConfig();
        redisConfig.useSingleServer()
                    .setConnectionMinimumIdleSize(5000)
                    .setConnectionPoolSize(5000);

        RedissonClient r = Redisson.create(redisConfig);
        r.shutdown();
    }

}
