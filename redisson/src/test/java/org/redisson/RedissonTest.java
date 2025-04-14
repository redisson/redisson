package org.redisson;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.CharsetUtil;
import net.bytebuddy.utility.RandomString;
import nl.altindag.log.LogCaptor;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.redisnode.RedisMaster;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.api.redisnode.RedisSingle;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.*;
import org.redisson.connection.CRC16;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.SequentialDnsAddressResolverFactory;
import org.redisson.connection.pool.SlaveConnectionPool;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTest extends RedisDockerTest {

    @Test
    public void testZeroMinimumIdleSize() {
        Config c = redisson.getConfig();
        c.useSingleServer().setConnectionMinimumIdleSize(0);

        RedissonClient r = Redisson.create(c);
        r.shutdown();
    }

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
        String key = RandomString.make(120);
        for (int i = 0; i < 500; i++) {
            RMapCache<String, String> cache = redisson.getMapCache("mycache");
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
    public void testLazyInitializationCluster() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.setLazyInitialization(true);

            RedissonClient redisson = Redisson.create(config);
            redisson.getBucket("test").set(1);
            redisson.shutdown();
        });
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

//    @Test
    public void testNettyThreadsAmount() throws Exception {
        Config config = redisson.getConfig();
        config.setCodec(new SlowCodec());
        config.setReferenceEnabled(false);
        config.setThreads(32);
        config.setNettyThreads(20);
        RedissonClient redisson = Redisson.create(config);

        CountDownLatch latch = new CountDownLatch(16);
        AtomicBoolean hasErrors = new AtomicBoolean();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread() {
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            redisson.getBucket("123").set("1");
                            redisson.getBucket("123").get();
                            if (hasErrors.get()) {
                                latch.countDown();
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            hasErrors.set(true);
                        }

                    }
                    latch.countDown();
                };
            };
            t.start();
        }

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        assertThat(hasErrors).isFalse();

        redisson.shutdown();
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
        GenericContainer<?> redis = createRedis("--requirepass", "1234");
        redis.start();

        Config config = createConfig(redis);
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
        redis.stop();
    }

    @Test
    public void testCredentialsReapplyInterval() throws InterruptedException {
        GenericContainer<?> redis = createRedis("--requirepass", "1234");
        redis.start();

        CountDownLatch latch = new CountDownLatch(8);

        Config config = createConfig(redis);
        config.useSingleServer()
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(1)
                .setCredentialsReapplyInterval(5000)
                .setCredentialsResolver(new CredentialsResolver() {
                    @Override
                    public CompletionStage<Credentials> resolve(InetSocketAddress address) {
                        latch.countDown();
                        return CompletableFuture.completedFuture(new Credentials(null, "1234"));
                    }
                });

        RedissonClient rc = Redisson.create(config);
        RBucket<String> b = rc.getBucket("test");
        b.set("123");

        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();

        rc.shutdown();
        redis.stop();
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
        GenericContainer<?> redis = createRedis("--requirepass", "1234");
        redis.start();

        Config config = createConfig(redis);
        RedisURI ur = new RedisURI(config.useSingleServer().getAddress());
        config.useSingleServer().setAddress("redis://:1234@" + ur.getHost() + ":" + ur.getPort());
        RedissonClient redisson = Redisson.create(config);

        RBucket<String> b = redisson.getBucket("test");
        b.set("123");

        redisson.shutdown();
        redis.stop();
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
    public void testMasterSlave() throws InterruptedException {
        SimpleDnsServer s = new SimpleDnsServer();

        Config c2 = new Config();
        c2.setAddressResolverGroupFactory(new SequentialDnsAddressResolverFactory() {
            @Override
            public AddressResolverGroup<InetSocketAddress> create(Class<? extends DatagramChannel> channelType, Class<? extends SocketChannel> socketChannelType, DnsServerAddressStreamProvider nameServerProvider) {
                return super.create(channelType, socketChannelType, hostname ->
                        DnsServerAddresses.singleton(s.getAddr()).stream());
            }
        });
        c2.useMasterSlaveServers()
                .setMasterAddress("redis://masterhost:" + REDIS.getFirstMappedPort())
                .addSlaveAddress("redis://slavehost1:" + REDIS.getFirstMappedPort(),
                                            "redis://slavehost2:" + REDIS.getFirstMappedPort());

        RedissonClient cc = Redisson.create(c2);
        RBucket<String> b = cc.getBucket("test");
        b.set("1");
        assertThat(b.get()).isEqualTo("1");

        cc.shutdown();
        s.stop();
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
    public void testReadMasterSlave() {
        testInCluster(rc -> {
            Config c = rc.getConfig();
            c.useClusterServers().setReadMode(ReadMode.MASTER_SLAVE);

            LogCaptor logCaptor = LogCaptor.forClass(SlaveConnectionPool.class);
            logCaptor.setLogLevelToDebug();

            RedissonClient rr = Redisson.create(c);

            for (int i = 0; i < 100; i++) {
                rr.getBucket("" + i).get();
            }

            long slaveConnections = logCaptor.getDebugLogs().stream().filter(s -> s.contains("nodeType=SLAVE")).count();
            long masterConnections = logCaptor.getDebugLogs().stream().filter(s -> s.contains("nodeType=MASTER")).count();

            assertThat(masterConnections).isEqualTo(50);
            assertThat(slaveConnections).isEqualTo(50);

            logCaptor.close();
            rr.shutdown();
        });
    }

    @Test
    public void testMovedRedirectInCluster() {
        withNewCluster((nodes, redissonClient) -> {
            Config config = redissonClient.getConfig();
            config.useClusterServers()
                    .setScanInterval(100000);

            RedissonClient redisson = Redisson.create(config);

            List<ContainerState> masters = getMasterNodes(nodes);
            ContainerState source = masters.get(0);
            ContainerState destination = masters.get(0);

            String key = null;
            int slot = 0;
            String res = execute(source, "redis-cli", "cluster", "slots");
            int sourceSlot = Integer.valueOf(res.split("\\n")[1]);
            for (int i = 0; i < 100000; i++) {
                key = "" + i;
                slot = CRC16.crc16(key.getBytes()) % MasterSlaveConnectionManager.MAX_SLOT;
                if (sourceSlot == slot) {
                    break;
                }
            }

            redisson.getBucket(key).set("123");

            String ss = execute(source, "redis-cli", "exists", key);
            assertThat(ss).contains("1");

            String sourceId = execute(source, "redis-cli", "cluster", "myid");
            String destinationId = execute(destination, "redis-cli", "cluster", "myid");
            execute(destination, "redis-cli", "cluster", "setslot", String.valueOf(sourceSlot), "IMPORTING", sourceId);
            execute(source, "redis-cli", "cluster", "setslot", String.valueOf(sourceSlot), "MIGRATING", destinationId);

            String keysr = execute(source, "redis-cli", "cluster", "getkeysinslot", String.valueOf(sourceSlot), "100");
            String[] keys = keysr.split("\n");

            List<String> params = new ArrayList<>();
            params.add("redis-cli");
            params.add("migrate");
            String destinationIP = destination.getContainerInfo().getNetworkSettings().getNetworks()
                                            .values().iterator().next().getIpAddress();
            params.add(destinationIP);
            params.add(destination.getExposedPorts().get(0).toString());
            params.add("");
            params.add("0");
            params.add("2000");
            params.add("KEYS");
            params.addAll(Arrays.asList(keys));
            execute(source, params.toArray(new String[0]));

            for (ContainerState master : masters) {
                execute(master, "redis-cli", "cluster", "setslot", String.valueOf(slot), "NODE", destinationId);
            }

            String ss2 = execute(destination, "redis-cli", "exists", key);
            assertThat(ss2).contains("1");

            redisson.getBucket(key).set("123");
            assertThat(redisson.getBucket(key).get()).isEqualTo("123");

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
            Awaitility.await().atLeast(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(7)).untilAsserted(() -> {
            Assertions.assertThrows(RedisConnectionException.class, () -> {
                Config config = new Config();
                config.useClusterServers()
                        .addNodeAddress("redis://127.99.0.1:1111");
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

    @Test
    public void testShutdownQuietPeriod() {
        // On a very slow system this may give false positives,
        // but at the same time a longer timeout would make the test suite slower
        long quietPeriod = TimeUnit.MILLISECONDS.toMillis(50);
        long timeOut = quietPeriod + TimeUnit.SECONDS.toMillis(2);
        RedissonClient r = createInstance();
        RBucket<Integer> b = r.getBucket("test1");
        for (int i = 0; i < 10; i++) {
            b.get();
        }

        List<RFuture<Integer>> futures = new ArrayList<>();
        RBlockingQueue<Integer> bb = r.getBlockingQueue("test2");
        for (int i = 0; i < 10; i++) {
            RFuture<Integer> s = bb.takeAsync();
            futures.add(s);
        }

        long startTime = System.currentTimeMillis();
        r.shutdown(quietPeriod, timeOut, TimeUnit.MILLISECONDS);
        long shutdownTime = System.currentTimeMillis() - startTime;

        Assertions.assertTrue(shutdownTime > quietPeriod);

        assertThat(futures).hasSize(10);
        for (RFuture<Integer> future : futures) {
            assertThat(future.exceptionNow().getMessage()).isEqualTo("Redisson is shutdown");
        }
    }

}
