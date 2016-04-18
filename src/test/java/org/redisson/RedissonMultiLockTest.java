package org.redisson;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.redisson.core.RLock;
import org.redisson.core.RedissonMultiLock;

import io.netty.channel.nio.NioEventLoopGroup;
import org.redisson.RedisRunner.RedisProcess;

public class RedissonMultiLockTest {

    @Test
    public void testMultiThreads() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance1();
        
        Config config1 = new Config();
        config1.useSingleServer().setAddress("127.0.0.1:6320");
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
        
        assertThat(redis1.stop()).isEqualTo(0);
    }
    
    @Test
    public void test() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance1();
        RedisProcess redis2 = redisTestMultilockInstance2();
        RedisProcess redis3 = redisTestMultilockInstance3();

        NioEventLoopGroup group = new NioEventLoopGroup();
        Config config1 = new Config();
        config1.useSingleServer().setAddress("127.0.0.1:6320");
        config1.setEventLoopGroup(group);
        RedissonClient client1 = Redisson.create(config1);
        client1.getKeys().flushdb();

        Config config2 = new Config();
        config2.useSingleServer().setAddress("127.0.0.1:6321");
        config2.setEventLoopGroup(group);
        RedissonClient client2 = Redisson.create(config2);
        client2.getKeys().flushdb();

        Config config3 = new Config();
        config3.useSingleServer().setAddress("127.0.0.1:6322");
        config3.setEventLoopGroup(group);
        RedissonClient client3 = Redisson.create(config3);
        client3.getKeys().flushdb();

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

        await().atMost(5, TimeUnit.SECONDS).until(() -> assertThat(executed.get()).isTrue());

        lock.unlock();

        assertThat(redis1.stop()).isEqualTo(0);

        assertThat(redis2.stop()).isEqualTo(0);

        assertThat(redis3.stop()).isEqualTo(0);
    }
    
    private RedisProcess redisTestMultilockInstance1() throws IOException, InterruptedException {
        return new RedisRunner()
                .port(6320)
                .run();
    }
    
    private RedisProcess redisTestMultilockInstance2() throws IOException, InterruptedException {
        return new RedisRunner()
                .port(6321)
                .run();
    }
    
    private RedisProcess redisTestMultilockInstance3() throws IOException, InterruptedException {
        return new RedisRunner()
                .port(6322)
                .run();
    }
}
