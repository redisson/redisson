package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonMultiLockTest extends RedisDockerTest {

    @Test
    void testLockUnlock() {
        RLock lock1 = redisson.getLock("lock1");
        RLock lock2 = redisson.getLock("lock2");
        RLock lock3 = redisson.getLock("lock3");

        RLock lock = redisson.getMultiLock(lock1, lock2, lock3);
        try {
            lock.lock(10, TimeUnit.SECONDS);
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            assertThat(lock.isHeldByThread(Thread.currentThread().threadId())).isTrue();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        assertThat(lock.isHeldByCurrentThread()).isFalse();
        assertThat(lock.isHeldByThread(Thread.currentThread().threadId())).isFalse();
    }

    @Test
    public void testWaitAndLeaseTimeouts() throws InterruptedException {
        RLock lock1 = redisson.getLock("lock1");
        RLock lock2 = redisson.getLock("lock2");
        RLock lock3 = redisson.getLock("lock3");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
                try {
                    boolean res = lock.tryLock(-1, 10, TimeUnit.SECONDS);
                    if (res) {
                        counter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(1);
    }
    
    @Test
    public void testMultiThreads() throws InterruptedException {
        RLock lock1 = redisson.getLock("lock1");
        RLock lock2 = redisson.getLock("lock2");
        RLock lock3 = redisson.getLock("lock3");
        
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
    }
    
    @Test
    public void test() throws IOException, InterruptedException {
        GenericContainer<?> redis1 = createRedis();
        GenericContainer<?> redis2 = createRedis();
        GenericContainer<?> redis3 = createRedis();

        redis1.start();
        redis2.start();
        redis3.start();

        RedissonClient client1 = createClient(redis1);
        RedissonClient client2 = createClient(redis2);
        RedissonClient client3 = createClient(redis3);

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

        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);

        lock.unlock();

        client1.shutdown();
        client2.shutdown();
        client3.shutdown();
        
        redis1.stop();
        redis2.stop();
        redis3.stop();
    }

    private RedissonClient createClient(GenericContainer<?> redis) {
        Config config = createConfig(redis);
        return Redisson.create(config);
    }
    
}
