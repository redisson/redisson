package org.redisson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RLock;
import org.redisson.core.RedissonMultiLock;
import org.redisson.core.RedissonRedLock;

import io.netty.channel.nio.NioEventLoopGroup;

import org.redisson.RedisRunner.RedisProcess;
import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

public class RedissonRedLockTest {

    @Test
    public void testLockFailed() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance(6320);
        RedisProcess redis2 = redisTestMultilockInstance(6321);
        
        RedissonClient client1 = createClient("127.0.0.1:6320");
        RedissonClient client2 = createClient("127.0.0.1:6321");
        
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
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }

    
    @Test
    public void testLockSuccess() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance(6320);
        RedisProcess redis2 = redisTestMultilockInstance(6321);
        
        RedissonClient client1 = createClient("127.0.0.1:6320");
        RedissonClient client2 = createClient("127.0.0.1:6321");
        
        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client1.getLock("lock2");
        RLock lock3 = client2.getLock("lock3");
        
        Thread t1 = new Thread() {
            public void run() {
                lock3.lock();
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

        lock3.delete();
        
        RedissonMultiLock lock = new RedissonRedLock(lock1, lock2, lock3);
        lock.lock();
        lock.unlock();
        
        assertThat(redis1.stop()).isEqualTo(0);
        assertThat(redis2.stop()).isEqualTo(0);
    }

    
    @Test
    public void testConnectionFailed() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance(6320);
        RedisProcess redis2 = redisTestMultilockInstance(6321);
        
        RedissonClient client1 = createClient("127.0.0.1:6320");
        RedissonClient client2 = createClient("127.0.0.1:6321");
        
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
        
        assertThat(redis1.stop()).isEqualTo(0);
    }

    
//    @Test
    public void testMultiThreads() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance(6320);
        
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
    
//    @Test
    public void test() throws IOException, InterruptedException {
        RedisProcess redis1 = redisTestMultilockInstance(6320);
        RedisProcess redis2 = redisTestMultilockInstance(6321);
        RedisProcess redis3 = redisTestMultilockInstance(6322);

        NioEventLoopGroup group = new NioEventLoopGroup();
        
        RedissonClient client1 = createClient(group, "127.0.0.1:6320");
        RedissonClient client2 = createClient(group, "127.0.0.1:6321");
        RedissonClient client3 = createClient(group, "127.0.0.1:6322");

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
    
    private RedisProcess redisTestMultilockInstance(int port) throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .port(port)
                .run();
    }
    
}
