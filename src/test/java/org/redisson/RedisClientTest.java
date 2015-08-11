package org.redisson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class RedisClientTest {

    @Test
    public void testConnectAsync() throws InterruptedException {
        RedisClient c = new RedisClient("localhost", 6379);
        Future<RedisConnection> f = c.connectAsync();
        final CountDownLatch l = new CountDownLatch(1);
        f.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                RedisConnection conn = future.get();
                Assert.assertEquals("PONG", conn.sync(RedisCommands.PING));
                l.countDown();
            }
        });
        l.await();
    }

    @Test
    public void testSubscribe() throws InterruptedException {
        RedisClient c = new RedisClient("localhost", 6379);
        RedisPubSubConnection pubSubConnection = c.connectPubSub();
        final CountDownLatch latch = new CountDownLatch(2);
        pubSubConnection.addListener(new RedisPubSubListener<Object>() {

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                Assert.assertEquals(PubSubType.SUBSCRIBE, type);
                Assert.assertTrue(Arrays.asList("test1", "test2").contains(channel));
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

        latch.await();
    }

    @Test
    public void test() throws InterruptedException {
        RedisClient c = new RedisClient("localhost", 6379);
        final RedisConnection conn = c.connect();

        conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2);
        for (int i = 0; i < 100000; i++) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    conn.async(StringCodec.INSTANCE, RedisCommands.INCR, "test");
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);

        Assert.assertEquals(100000L, conn.sync(LongCodec.INSTANCE, RedisCommands.GET, "test"));

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipeline() throws InterruptedException, ExecutionException {
        RedisClient c = new RedisClient("localhost", 6379);
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

        Assert.assertEquals("PONG", cmd1.getPromise().get());
        Assert.assertEquals(1, (long)cmd2.getPromise().get());
        Assert.assertEquals(2, (long)cmd3.getPromise().get());
        Assert.assertEquals("PONG", cmd4.getPromise().get());

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
        Assert.assertEquals(50, res.size());

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
