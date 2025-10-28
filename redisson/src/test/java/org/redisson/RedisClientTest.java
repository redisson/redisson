package org.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisClientTest  {

    private static RedisClient redisClient;

    @BeforeAll
    public static void beforeAll() {
        RedisClientConfig config = new RedisClientConfig();
        config.setProtocol(Protocol.RESP3);
        config.setAddress("redis://127.0.0.1:" + RedisDockerTest.REDIS.getFirstMappedPort());
        redisClient = RedisClient.create(config);
    }

    @AfterAll
    public static void afterAll() {
        redisClient.shutdown();
    }

    @Test
    public void testUsername() {
        RedisClientConfig cc = redisClient.getConfig();

        RedisConnection c = redisClient.connect();
        c.sync(new RedisStrictCommand<Void>("ACL"), "SETUSER", "testuser", "on", ">123456", "~*", "allcommands");
        c.close();

        cc.setUsername("testuser");
        cc.setPassword("123456");
        RedisClient client = RedisClient.create(cc);
        RedisConnection c2 = client.connect();
        assertThat(c2.sync(RedisCommands.PING)).isEqualTo("PONG");
        client.shutdown();
    }

    @Test
    public void testUsername2() {
        RedisConnection c = redisClient.connect();
        c.sync(new RedisStrictCommand<Void>("ACL"), "SETUSER", "testuser", "on", ">123456", "~*", "allcommands");
        c.close();

        Config config = RedisDockerTest.createConfig(RedisDockerTest.REDIS);
        config.useSingleServer()
                .setUsername("testuser")
                .setPassword("123456");

        RedissonClient redisson = Redisson.create(config);
        RBucket<String> b = redisson.getBucket("test");
        b.set("123");

        config.setUsername("user");

        Assertions.assertThrows(RedisConnectionException.class, () -> {
            Redisson.create(config);
        });

        config.setUsername("testuser");
        RedissonClient r = Redisson.create(config);
        b = r.getBucket("test");
        b.set("123");

        r.shutdown();
    }

    @Test
    public void testClientTrackingBroadcast() throws InterruptedException, ExecutionException {
        RedisPubSubConnection c = redisClient.connectPubSub();
        c.sync((RedisCommands.CLIENT_TRACKING), "ON", "BCAST");
        AtomicInteger counter = new AtomicInteger();
        ChannelName cn = new ChannelName("__redis__:invalidate");
        c.addListener(cn, new RedisPubSubListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                counter.incrementAndGet();
            }
        });
        c.subscribe(StringCodec.INSTANCE, cn).get();
        c.sync(RedisCommands.GET, "test");

        RedisPubSubConnection c4 = redisClient.connectPubSub();
        c4.sync((RedisCommands.CLIENT_TRACKING), "ON", "BCAST");
        c4.addListener(cn, new RedisPubSubListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                counter.incrementAndGet();
            }
        });
        c4.subscribe(StringCodec.INSTANCE, cn).get();
        c4.sync(RedisCommands.GET, "test");

        RedisConnection c3 = redisClient.connect();
        c3.sync(RedisCommands.SET, "test", "1234");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(2);

        counter.set(0);
        c3.sync(RedisCommands.SET, "test", "1235");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(2);

        counter.set(0);
        c4.sync(RedisCommands.SET, "test", "1236");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    public void testClientTracking() throws InterruptedException, ExecutionException {
        RedisPubSubConnection c = redisClient.connectPubSub();
        c.sync((RedisCommands.CLIENT_TRACKING), "ON");
        AtomicInteger counter = new AtomicInteger();
        ChannelName cn = new ChannelName("__redis__:invalidate");
        c.addListener(cn, new RedisPubSubListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                counter.incrementAndGet();
            }
        });
        c.subscribe(StringCodec.INSTANCE, cn).get();
        c.sync(RedisCommands.GET, "test");

        RedisPubSubConnection c4 = redisClient.connectPubSub();
        c4.sync((RedisCommands.CLIENT_TRACKING), "ON");
        c4.addListener(cn, new RedisPubSubListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                counter.incrementAndGet();
            }
        });
        c4.subscribe(StringCodec.INSTANCE, cn).get();
        c4.sync(RedisCommands.GET, "test");

        RedisConnection c3 = redisClient.connect();
        c3.sync(RedisCommands.SET, "test", "1234");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(2);

        counter.set(0);
        c.sync(RedisCommands.GET, "test");
        c3.sync(RedisCommands.SET, "test", "1235");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(1);

        counter.set(0);
        c4.sync(RedisCommands.GET, "test");
        c4.sync(RedisCommands.SET, "test", "1235");
        Thread.sleep(500);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    public void testConnectAsync() throws InterruptedException, ExecutionException {
        CompletionStage<RedisConnection> f = redisClient.connectAsync();
        CountDownLatch l = new CountDownLatch(2);
        f.whenComplete((conn, e) -> {
            assertThat(conn.sync(RedisCommands.PING)).isEqualTo("PONG");
            l.countDown();
        });
        f.handle((conn, ex) -> {
            assertThat(conn.sync(RedisCommands.PING)).isEqualTo("PONG");
            l.countDown();
            return null;
        });
        assertThat(l.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testSubscribe() throws InterruptedException {
        RedisPubSubConnection pubSubConnection = redisClient.connectPubSub();
        final CountDownLatch latch = new CountDownLatch(2);
        pubSubConnection.addListener(new ChannelName("test1"), new RedisPubSubListener<Object>() {

            @Override
            public void onStatus(PubSubType type, CharSequence channel) {
                assertThat(type).isEqualTo(PubSubType.SUBSCRIBE);
                assertThat(Arrays.asList("test1", "test2").contains(channel.toString())).isTrue();
                latch.countDown();
            }

            @Override
            public void onMessage(CharSequence channel, Object message) {
            }

            @Override
            public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
            }
        });
        pubSubConnection.addListener(new ChannelName("test2"), new RedisPubSubListener<Object>() {

            @Override
            public void onStatus(PubSubType type, CharSequence channel) {
                assertThat(type).isEqualTo(PubSubType.SUBSCRIBE);
                assertThat(Arrays.asList("test1", "test2").contains(channel.toString())).isTrue();
                latch.countDown();
            }

            @Override
            public void onMessage(CharSequence channel, Object message) {
            }

            @Override
            public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
            }
        });
        pubSubConnection.subscribe(StringCodec.INSTANCE, new ChannelName("test1"), new ChannelName("test2"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void test() throws InterruptedException {
        final RedisConnection conn = redisClient.connect();

        conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        for (int i = 0; i < 100000; i++) {
            pool.execute(() -> {
                conn.async(StringCodec.INSTANCE, RedisCommands.INCR, "test");
            });
        }

        pool.shutdown();

        assertThat(pool.awaitTermination(1, TimeUnit.HOURS)).isTrue();

        assertThat((Long) conn.sync(LongCodec.INSTANCE, RedisCommands.GET, "test")).isEqualTo(100000);

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipeline() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);

        List<CommandData<?, ?>> commands = new ArrayList<CommandData<?, ?>>();
        CommandData<String, String> cmd1 = conn.create(null, RedisCommands.PING);
        commands.add(cmd1);
        CommandData<Long, Long> cmd2 = conn.create(null, RedisCommands.INCR, "test");
        commands.add(cmd2);
        CommandData<Long, Long> cmd3 = conn.create(null, RedisCommands.INCR, "test");
        commands.add(cmd3);
        CommandData<String, String> cmd4 = conn.create(null, RedisCommands.PING);
        commands.add(cmd4);

        CompletableFuture<Void> p = new CompletableFuture<Void>();
        conn.send(new CommandsData(p, commands, false, false));

        assertThat(cmd1.getPromise().get()).isEqualTo("PONG");
        assertThat(cmd2.getPromise().get()).isEqualTo(1);
        assertThat(cmd3.getPromise().get()).isEqualTo(2);
        assertThat(cmd4.getPromise().get()).isEqualTo("PONG");

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testBigRequest() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        for (int i = 0; i < 50; i++) {
            conn.sync(StringCodec.INSTANCE, RedisCommands.HSET, "testmap", i, "2");
        }

        Map<Object, Object> res = conn.sync(StringCodec.INSTANCE, RedisCommands.HGETALL, "testmap");
        assertThat(res.size()).isEqualTo(50);

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipelineBigResponse() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        List<CommandData<?, ?>> commands = new ArrayList<CommandData<?, ?>>();
        for (int i = 0; i < 1000; i++) {
            CommandData<String, String> cmd1 = conn.create(null, RedisCommands.PING);
            commands.add(cmd1);
        }

        CompletableFuture<Void> p = new CompletableFuture<Void>();
        conn.send(new CommandsData(p, commands, false, false));

        for (CommandData<?, ?> commandData : commands) {
            commandData.getPromise().get();
        }

        conn.sync(RedisCommands.FLUSHDB);
    }

}
