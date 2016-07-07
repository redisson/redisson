package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class RedisClientTest {

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @After
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @Test
    public void testConnectAsync() throws InterruptedException {
        RedisClient c = new RedisClient("localhost", 6379);
        Future<RedisConnection> f = c.connectAsync();
        final CountDownLatch l = new CountDownLatch(1);
        f.addListener((FutureListener<RedisConnection>) future -> {
            RedisConnection conn = future.get();
            assertThat(conn.sync(RedisCommands.PING)).isEqualTo("PONG");
            l.countDown();
        });
        l.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testSubscribe() throws InterruptedException {
        RedisClient c = new RedisClient("localhost", 6379);
        RedisPubSubConnection pubSubConnection = c.connectPubSub();
        final CountDownLatch latch = new CountDownLatch(2);
        pubSubConnection.addListener(new RedisPubSubListener<Object>() {

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                assertThat(type).isEqualTo(PubSubType.SUBSCRIBE);
                assertThat(Arrays.asList("test1", "test2").contains(channel)).isTrue();
                latch.countDown();
                return true;
            }

            @Override
            public void onMessage(String channel, Object message) {
            }

            @Override
            public void onPatternMessage(String pattern, String channel, Object message) {
            }
        });
        pubSubConnection.subscribe(StringCodec.INSTANCE, "test1", "test2");

        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void test() throws InterruptedException {
        RedisClient c = new RedisClient(new NioEventLoopGroup(), NioSocketChannel.class, "localhost", 6379, 3000, 10000);
        final RedisConnection conn = c.connect();

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
        RedisClient c = new RedisClient(new NioEventLoopGroup(), NioSocketChannel.class, "localhost", 6379, 3000, 10000);
        RedisConnection conn = c.connect();

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

        Promise<Void> p = c.getBootstrap().group().next().newPromise();
        conn.send(new CommandsData(p, commands));

        assertThat(cmd1.getPromise().get()).isEqualTo("PONG");
        assertThat(cmd2.getPromise().get()).isEqualTo(1);
        assertThat(cmd3.getPromise().get()).isEqualTo(2);
        assertThat(cmd4.getPromise().get()).isEqualTo("PONG");

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testBigRequest() throws InterruptedException, ExecutionException {
        RedisClient c = new RedisClient("localhost", 6379);
        RedisConnection conn = c.connect();

        for (int i = 0; i < 50; i++) {
            conn.sync(StringCodec.INSTANCE, RedisCommands.HSET, "testmap", i, "2");
        }

        Map<Object, Object> res = conn.sync(StringCodec.INSTANCE, RedisCommands.HGETALL, "testmap");
        assertThat(res.size()).isEqualTo(50);

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipelineBigResponse() throws InterruptedException, ExecutionException {
        RedisClient c = new RedisClient("localhost", 6379);
        RedisConnection conn = c.connect();

        List<CommandData<?, ?>> commands = new ArrayList<CommandData<?, ?>>();
        for (int i = 0; i < 1000; i++) {
            CommandData<String, String> cmd1 = conn.create(null, RedisCommands.PING);
            commands.add(cmd1);
        }

        Promise<Void> p = c.getBootstrap().group().next().newPromise();
        conn.send(new CommandsData(p, commands));

        for (CommandData<?, ?> commandData : commands) {
            commandData.getPromise().get();
        }

        conn.sync(RedisCommands.FLUSHDB);
    }

}
