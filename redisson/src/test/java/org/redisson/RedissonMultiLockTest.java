package org.redisson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMultiLockTest {

    @Test
    public void testMultiThreads() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        
        Config config1 = new Config();
        config1.useSingleServer().setAddress(redis1.getRedisServerAddressAndPort());
        RedissonClient client = Redisson.create(config1);
        
        RLock lock1 = client.getLock("lock1");
        RLock lock2 = client.getLock("lock2");
        RLock lock3 = client.getLock("lock3");
        
        Thread t = new Thread() {
            public void run() {
                RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
                lock.lock();
                
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
                
                lock.unlock();
            };
        };
        t.start();
        t.join(1000);

        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
        lock.lock();
        lock.unlock();
        
        client.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
    }
    
    @Test
    public void test() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        RedisProcess redis3 = redisTestMultilockInstance();

        NioEventLoopGroup group = new NioEventLoopGroup();
        
        RedissonClient client1 = createClient(group, redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(group, redis2.getRedisServerAddressAndPort());
        RedissonClient client3 = createClient(group, redis3.getRedisServerAddressAndPort());

        final RLock lock1 = client1.getLock("lock1");
        final RLock lock2 = client2.getLock("lock2");
        final RLock lock3 = client3.getLock("lock3");

        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
        lock.lock();

        final AtomicBoolean executed = new AtomicBoolean();

        Thread t = new Thread() {
            @Override
            public void run() {
                RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
                assertThat(lock.tryLock()).isFalse();
                assertThat(lock.tryLock()).isFalse();
                executed.set(true);
            }
        };
        t.start();
        t.join();

        await().atMost(5, TimeUnit.SECONDS).until(() -> executed.get());

        lock.unlock();

        client1.shutdown();
        client2.shutdown();
        client3.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);

        assertThat(redis2.stop()).isEqualTo(0);

        assertThat(redis3.stop()).isEqualTo(0);
    }

    private RedissonClient createClient(NioEventLoopGroup group, String host) {
        Config config1 = new Config();
        config1.useSingleServer().setAddress(host);
        config1.setEventLoopGroup(group);
        RedissonClient client1 = Redisson.create(config1);
        client1.getKeys().flushdb();
        return client1;
    }
    
    private RedisProcess redisTestMultilockInstance() throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
    }
    
}
