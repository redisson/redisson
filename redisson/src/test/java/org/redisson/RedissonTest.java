package org.redisson;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import net.bytebuddy.utility.RandomString;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.RedisProcess;
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
import org.redisson.config.*;
import org.redisson.connection.CRC16;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.balancer.RandomLoadBalancer;

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

public class RedissonTest extends BaseTest {

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
    public void testLazyInitialization() throws IOException, InterruptedException {
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

        RedisProcess pp = new RedisRunner()
                .nosave()
                .port(4431)
                .randomDir()
                .run();

        redisson.getStream("test").createGroup(StreamCreateGroupArgs.name("test").makeStream());

        redisson.shutdown();
        pp.stop();
    }

    @Test
    public void testPerformance() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer().setConnectionPoolSize(1).setConnectionMinimumIdleSize(1);
        RedissonClient inst = Redisson.create(config);
        RAtomicLong s = inst.getAtomicLong("counter");

        ExecutorService ex = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 500_000; i++) {
            ex.execute(() -> {
                long t = s.incrementAndGet();
            });
        }

        ex.shutdown();
        assertThat(ex.awaitTermination(8, TimeUnit.SECONDS)).isTrue();
        inst.shutdown();
    }

    @Test
    public void testResponseHandling2() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer()
                .setTimeout(10)
                .setRetryAttempts(0)
                .setConnectionPoolSize(1)
                .setConnectionMinimumIdleSize(1)
                .setPingConnectionInterval(0)
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

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
        assertThat(executor1.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        executor2.shutdown();
        assertThat(executor2.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(hasError).isFalse();

        redisson.shutdown();
    }


    @Test
    public void testResponseHandling() throws InterruptedException {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add(i);
        }

        RList<Integer> l = redisson.getList("test");
        l.addAll(list);
        ExecutorService e = Executors.newFixedThreadPool(8);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            e.submit(() -> {
                for (int k = 0; k < 10000; k++) {
                    assertThat(l.get(k)).isEqualTo(k);
                    counter.incrementAndGet();
                }
            });
        }
        e.shutdown();
        assertThat(e.awaitTermination(40, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(10000 * 100);
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
        Config config = new Config();
        config.useSingleServer()
              .setConnectionMinimumIdleSize(3)
              .setConnectionPoolSize(3)
              .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

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
    public void testNextResponseAfterDecoderError() throws Exception {
        Config config = new Config();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(1)
              .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient redisson = Redisson.create(config);
        
        setJSONValue(redisson, "test1", "test1");
        setStringValue(redisson, "test2", "test2");
        setJSONValue(redisson, "test3", "test3");
        try {
            RBuckets buckets = redisson.getBuckets(new JsonJacksonCodec());
            buckets.get("test2", "test1");
        } catch (Exception e) {
            e.printStackTrace();
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
        Config config = new Config();
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        config.setCodec(new SerializationCodec());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RedissonClient r = Redisson.create(config);
            r.getMap("test").put("1", new Dummy());
        });
    }

    @Test
    public void testMemoryScript() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort()).setTimeout(100000);

        Assertions.assertThrows(RedisOutOfMemoryException.class, () -> {
            RedissonClient r = null;
            try {
                r = Redisson.create(config);
                r.getKeys().flushall();
                for (int i = 0; i < 10000; i++) {
                    r.getMap("test").put("" + i, "" + i);
                }
            } finally {
                r.shutdown();
                p.stop();
            }
        });
    }

    @Test
    public void testMemoryCommand() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort()).setTimeout(100000);

        Assertions.assertThrows(RedisOutOfMemoryException.class, () -> {
            RedissonClient r = null;
            try {
                r = Redisson.create(config);
                r.getKeys().flushall();
                for (int i = 0; i < 10000; i++) {
                    r.getMap("test").fastPut("" + i, "" + i);
                }
            } finally {
                r.shutdown();
                p.stop();
            }
        });
    }

    @Test
    public void testConfigValidation() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Config redissonConfig = new Config();
            redissonConfig.useSingleServer()
                    .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort())
                    .setConnectionPoolSize(2);
            Redisson.create(redissonConfig);
        });
    }
    
    @Test
    public void testConnectionListener() throws IOException, InterruptedException, TimeoutException {

        final RedisProcess p = redisTestConnection();

        final AtomicInteger connectCounter = new AtomicInteger();
        final AtomicInteger disconnectCounter = new AtomicInteger();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort());
        config.setConnectionListener(new ConnectionListener() {

            @Override
            public void onDisconnect(InetSocketAddress addr) {
            }

            @Override
            public void onDisconnect(InetSocketAddress addr, NodeType nodeType) {
                assertThat(addr).isEqualTo(new InetSocketAddress(p.getRedisServerBindAddress(), p.getRedisServerPort()));
                assertThat(nodeType).isEqualTo(NodeType.MASTER);
                disconnectCounter.incrementAndGet();
            }

            @Override
            public void onConnect(InetSocketAddress addr) {
            }

            @Override
            public void onConnect(InetSocketAddress addr, NodeType nodeType) {
                assertThat(addr).isEqualTo(new InetSocketAddress(p.getRedisServerBindAddress(), p.getRedisServerPort()));
                assertThat(nodeType).isEqualTo(NodeType.MASTER);
                connectCounter.incrementAndGet();
            }
        });

        RedissonClient r = Redisson.create(config);

        r.getBucket("1").get();
        Assertions.assertEquals(0, p.stop());

        await().atMost(2, TimeUnit.SECONDS).until(() -> disconnectCounter.get() == 1);
        
        try {
            r.getBucket("1").get();
        } catch (Exception e) {
        }
        
        assertThat(connectCounter.get()).isEqualTo(1);
        assertThat(disconnectCounter.get()).isEqualTo(1);

        RedisProcess pp = new RedisRunner()
                .nosave()
                .port(p.getRedisServerPort())
                .randomDir()
                .run();

        r.getBucket("1").get();

        assertThat(connectCounter.get()).isEqualTo(2);
        assertThat(disconnectCounter.get()).isEqualTo(1);

        r.shutdown();
        Assertions.assertEquals(0, pp.stop());
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
    public void testReconnection() throws IOException, InterruptedException, TimeoutException {
        RedisProcess runner = new RedisRunner()
                .appendonly(true)
                .randomDir()
                .randomPort()
                .run();

        Config config = new Config();
        config.useSingleServer()
                .setConnectionMinimumIdleSize(20)
                .setConnectionPoolSize(20)
                .setSubscriptionConnectionMinimumIdleSize(20)
                .setSubscriptionConnectionPoolSize(20)
                .setAddress(runner.getRedisServerAddressAndPort());

        RedissonClient r = Redisson.create(config);
        
        r.getBucket("myBucket").set(1);
        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);
        
        Assertions.assertEquals(0, runner.stop());
        
        AtomicBoolean hasError = new AtomicBoolean();
        try {
            r.getBucket("myBucket").get();
        } catch (Exception e) {
            // skip error
            hasError.set(true);
        }

        assertThat(hasError.get()).isTrue();
        
        RedisProcess pp = new RedisRunner()
                .appendonly(true)
                .port(runner.getRedisServerPort())
                .dir(runner.getDefaultDir())
                .run();

        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);

        r.shutdown();

        Assertions.assertEquals(0, pp.stop());
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
        Config config = new Config();
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient r = Redisson.create(config);
        Assertions.assertFalse(r.isShuttingDown());
        Assertions.assertFalse(r.isShutdown());
        r.shutdown();
        Assertions.assertTrue(r.isShuttingDown());
        Assertions.assertTrue(r.isShutdown());
    }

    @Test
    public void testCredentials() throws IOException, InterruptedException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .requirepass("1234")
                .run();

        Config config = new Config();
        config.useSingleServer()
                .setCredentialsResolver(new CredentialsResolver() {
                    @Override
                    public CompletionStage<Credentials> resolve(InetSocketAddress address) {
                        return CompletableFuture.completedFuture(new Credentials(null, "1234"));
                    }
                })
                .setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        RBucket<String> b = redisson.getBucket("test");
        b.set("123");

        redisson.shutdown();
        runner.stop();

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
        assertThat(e.getMessage()).startsWith("ERR unknown command `EVAL_111`");

        redisson.shutdown();
    }

    @Test
    public void testURIPassword() throws InterruptedException, IOException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .requirepass("1234")
                .run();

        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://:1234@" + runner.getRedisServerBindAddress() + ":" + runner.getRedisServerPort());
        RedissonClient redisson = Redisson.create(config);
        RBucket<String> b = redisson.getBucket("test");
        b.set("123");

        redisson.shutdown();
        runner.stop();
    }

    @Test
    public void testSentinelStartupWithPassword() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .requirepass("123")
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .requirepass("123")
                .masterauth("123")
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .requirepass("123")
                .masterauth("123")
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "123")
                .requirepass("123")
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "123")
                .requirepass("123")
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "123")
                .requirepass("123")
                .run();

        Thread.sleep(5000);

        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .setPassword("123")
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");

        long t = System.currentTimeMillis();
        RedissonClient redisson = Redisson.create(config);
        assertThat(System.currentTimeMillis() - t).isLessThan(2000L);
        redisson.shutdown();

        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    @Test
    public void testSentinelStartup() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        
        Thread.sleep(5000); 
        
        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");
        
        long t = System.currentTimeMillis();
        RedissonClient redisson = Redisson.create(config);
        assertThat(System.currentTimeMillis() - t).isLessThan(2000L);
        redisson.shutdown();
        
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }
    
    @Test
    public void testSingleConfigYAML() throws IOException {
        RedissonClient r = BaseTest.createInstance();
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
    public void testEvalCache() throws InterruptedException, IOException {
        RedisRunner master1 = new RedisRunner().port(6896).randomDir().nosave();
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

        Thread.sleep(5000);

        Config config = new Config();
        config.setUseScriptCache(true);
        config.useClusterServers()
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(4, "40");
        t.add(2, "20");
        t.add(1, "10", 1, TimeUnit.SECONDS);

        t.size();
    }

    @Test
    public void testMovedRedirectInCluster() throws Exception {
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
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setScanInterval(100000)
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        RedisClientConfig cfg = new RedisClientConfig();
        cfg.setAddress(process.getNodes().iterator().next().getRedisServerAddressAndPort());
        RedisClient c = RedisClient.create(cfg);
        RedisConnection cc = c.connect();
        List<ClusterNodeInfo> cn = cc.sync(RedisCommands.CLUSTER_NODES);
        c.shutdownAsync();
        cn = cn.stream().filter(i -> i.containsFlag(Flag.MASTER)).collect(Collectors.toList());
        Iterator<ClusterNodeInfo> nodesIter = cn.iterator();
        
        
        ClusterNodeInfo source = nodesIter.next();
        ClusterNodeInfo destination = nodesIter.next();

        RedisClientConfig sourceCfg = new RedisClientConfig();
        sourceCfg.setAddress(source.getAddress());
        RedisClient sourceClient = RedisClient.create(sourceCfg);
        RedisConnection sourceConnection = sourceClient.connect();

        RedisClientConfig destinationCfg = new RedisClientConfig();
        destinationCfg.setAddress(destination.getAddress());
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
            cc1.setAddress(node.getAddress());
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
        process.shutdown();
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
        Assumptions.assumeFalse(RedissonRuntimeEnvironment.isTravis);
        Config redisConfig = new Config();
        redisConfig.useSingleServer()
        .setConnectionMinimumIdleSize(5000)
        .setConnectionPoolSize(5000)
        .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        RedissonClient r = Redisson.create(redisConfig);
        r.shutdown();
    }

    private RedisProcess redisTestSmallMemory() throws IOException, InterruptedException {
        return new RedisRunner()
                .maxmemory("1mb")
                .nosave()
                .randomDir()
                .randomPort()
                .run();
    }

    private RedisProcess redisTestConnection() throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
    }
    
}
