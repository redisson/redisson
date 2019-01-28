package org.redisson;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;

import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

public class RedissonRedLockTest {

    @Test
    public void testLockLeasetimeWithMilliSeconds() throws IOException, InterruptedException {
        testLockLeasetime(2000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testLockLeasetimeWithSeconds() throws IOException, InterruptedException {
        testLockLeasetime(2, TimeUnit.SECONDS);
    }

    @Test
    public void testLockLeasetimeWithMinutes() throws IOException, InterruptedException {
        testLockLeasetime(1, TimeUnit.MINUTES);
    }

    private void testLockLeasetime(final long leaseTime, final TimeUnit unit) throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        
        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");
        RLock lock4 = client2.getLock("lock4");
        RLock lock5 = client2.getLock("lock5");
        RLock lock6 = client2.getLock("lock6");
        RLock lock7 = client2.getLock("lock7");


        RedissonRedLock lock = new RedissonRedLock(lock1, lock2, lock3, lock4, lock5, lock6, lock7);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 5; j++) {
                    try {
                        lock.lock(leaseTime, unit);
                        int nextValue = counter.get() + 1;
                        Thread.sleep(1000);
                        counter.set(nextValue);
                        lock.unlock();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        assertThat(counter.get()).isEqualTo(50);
        
        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }
    
    @Test
    public void testTryLockLeasetime() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        
        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");

        RedissonRedLock lock = new RedissonRedLock(lock1, lock2, lock3);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 5; j++) {
                    try {
                        if (lock.tryLock(4, 2, TimeUnit.SECONDS)) {
                            int nextValue = counter.get() + 1;
                            Thread.sleep(1000);
                            counter.set(nextValue);
                            lock.unlock();
                        } else {
                            j--;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        assertThat(counter.get()).isEqualTo(50);
        
        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }

    
    @Test
    public void testLockFailed() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        
        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");
        
        Thread t1 = new Thread() {
            public void run() {
                lock2.lock();
                lock3.lock();
            };
        };
        t1.start();
        t1.join();
        
        Thread t = new Thread() {
            public void run() {
                RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
                lock.lock();
            };
        };
        t.start();
        t.join(1000);

        RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
        Assert.assertFalse(lock.tryLock());
        
        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }

    @Test
    public void testLockSuccess2() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();

        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());

        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");
        
        Thread t1 = new Thread() {
            public void run() {
                lock2.lock();
            };
        };
        t1.start();
        t1.join();
        
        RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);

        assertThat(lock.tryLock(500, 5000, TimeUnit.MILLISECONDS)).isTrue();
        Thread.sleep(3000);
        
        lock.unlock();

        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);        
    }
    
    @Test
    public void testLockSuccess() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        
        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");
        
        testLock(lock1, lock2, lock3, lock1);
        testLock(lock1, lock2, lock3, lock2);
        testLock(lock1, lock2, lock3, lock3);
        
        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }

    protected void testLock(RLock lock1, RLock lock2, RLock lock3, RLock lockFirst) throws InterruptedException {
        Thread t1 = new Thread() {
            public void run() {
                lockFirst.lock();
            };
        };
        t1.start();
        t1.join();
        
        Thread t = new Thread() {
            public void run() {
                RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
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

        lockFirst.delete();
        
        RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
        lock.lock();
        lock.unlock();
    }

    
    @Test
    public void testConnectionFailed() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance();
        RedisProcess redis2 = redisTestMultilockInstance();
        
        RedissonClient client1 = createClient(redis1.getRedisServerAddressAndPort());
        RedissonClient client2 = createClient(redis2.getRedisServerAddressAndPort());
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        assertThat(redis2.stop()).isEqualTo(0);
        RLock lock3 = client2.getLock("lock3");
        
        Thread t = new Thread() {
            public void run() {
                RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
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

        RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
        lock.lock();
        lock.unlock();
        
        client1.shutdown();
        client2.shutdown();
        
        assertThat(redis1.stop()).isEqualTo(0);
    }

    
//    @Test
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
    
//    @Test
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

        assertThat(redis1.stop()).isEqualTo(0);

        assertThat(redis2.stop()).isEqualTo(0);

        assertThat(redis3.stop()).isEqualTo(0);
    }

    private RedissonClient createClient(String host) {
        return createClient(null, host);
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
