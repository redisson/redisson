package org.redisson;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RLock;
import org.redisson.core.RReadWriteLock;
import static org.assertj.core.api.Assertions.*;

public class RedissonReadWriteLockTest extends BaseConcurrentTest {

    @Test
    public void testWriteLock() throws InterruptedException {
        final RReadWriteLock lock = redisson.getReadWriteLock("lock");

        final RLock writeLock = lock.writeLock();
        writeLock.lock();

        Assert.assertTrue(lock.writeLock().tryLock());

        Thread t = new Thread() {
            public void run() {
                 Assert.assertFalse(writeLock.isHeldByCurrentThread());
                 Assert.assertTrue(writeLock.isLocked());
                 Assert.assertFalse(lock.readLock().tryLock());
                 Assert.assertFalse(redisson.getReadWriteLock("lock").readLock().tryLock());

                 try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                 Assert.assertTrue(lock.readLock().tryLock());
                 Assert.assertTrue(redisson.getReadWriteLock("lock").readLock().tryLock());
            };
        };

        t.start();
        t.join(50);

        writeLock.unlock();
        Assert.assertFalse(lock.readLock().tryLock());
        Assert.assertTrue(writeLock.isHeldByCurrentThread());
        writeLock.unlock();
        Thread.sleep(1000);

        Assert.assertFalse(lock.writeLock().tryLock());
        Assert.assertFalse(lock.writeLock().isLocked());
        Assert.assertFalse(lock.writeLock().isHeldByCurrentThread());
        lock.delete();
    }

    @Test
    public void testMultiRead() throws InterruptedException {
        final RReadWriteLock lock = redisson.getReadWriteLock("lock");
        Assert.assertFalse(lock.delete());

        final RLock readLock1 = lock.readLock();
        readLock1.lock();

        Assert.assertFalse(lock.writeLock().tryLock());

        final AtomicReference<RLock> readLock2 = new AtomicReference<RLock>();
        Thread t = new Thread() {
            public void run() {
                 RLock r = lock.readLock();
                 Assert.assertFalse(readLock1.isHeldByCurrentThread());
                 Assert.assertTrue(readLock1.isLocked());
                 r.lock();
                 readLock2.set(r);

                 try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                r.unlock();
            };
        };

        t.start();
        t.join(50);

        Assert.assertTrue(readLock2.get().isLocked());

        readLock1.unlock();
        Assert.assertFalse(lock.writeLock().tryLock());
        Assert.assertFalse(readLock1.isHeldByCurrentThread());
        Thread.sleep(1000);

        Assert.assertFalse(readLock2.get().isLocked());
        Assert.assertTrue(lock.writeLock().tryLock());
        Assert.assertTrue(lock.writeLock().isLocked());
        Assert.assertTrue(lock.writeLock().isHeldByCurrentThread());
        lock.writeLock().unlock();

        Assert.assertFalse(lock.writeLock().isLocked());
        Assert.assertFalse(lock.writeLock().isHeldByCurrentThread());
        Assert.assertTrue(lock.writeLock().tryLock());
        lock.delete();
    }

    @Test
    public void testDelete() {
        RReadWriteLock lock = redisson.getReadWriteLock("lock");
        Assert.assertFalse(lock.delete());

        lock.readLock().lock();
        Assert.assertTrue(lock.delete());
    }

    @Test
    public void testForceUnlock() {
        RReadWriteLock lock = redisson.getReadWriteLock("lock");

        RLock readLock = lock.readLock();
        readLock.lock();
        assertThat(readLock.isLocked()).isTrue();
        lock.writeLock().forceUnlock();
        assertThat(readLock.isLocked()).isTrue();
        lock.readLock().forceUnlock();
        assertThat(readLock.isLocked()).isFalse();

        RLock writeLock = lock.writeLock();
        writeLock.lock();
        assertThat(writeLock.isLocked()).isTrue();
        lock.readLock().forceUnlock();
        assertThat(writeLock.isLocked()).isTrue();
        lock.writeLock().forceUnlock();
        assertThat(writeLock.isLocked()).isFalse();

        lock = redisson.getReadWriteLock("lock");
        assertThat(lock.readLock().isLocked()).isFalse();
        assertThat(lock.writeLock().isLocked()).isFalse();
    }

    @Test
    public void testExpireRead() throws InterruptedException {
        RReadWriteLock lock = redisson.getReadWriteLock("lock");
        lock.readLock().lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RReadWriteLock lock1 = redisson.getReadWriteLock("lock");
                lock1.readLock().lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assert.assertTrue(spendTime < 2050);
                lock1.readLock().unlock();
            };
        };

        t.start();
        t.join();

        lock.readLock().unlock();
    }

    @Test
    public void testExpireWrite() throws InterruptedException {
        RReadWriteLock lock = redisson.getReadWriteLock("lock");
        lock.writeLock().lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RReadWriteLock lock1 = redisson.getReadWriteLock("lock");
                lock1.writeLock().lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assert.assertTrue(spendTime < 2050);
                lock1.writeLock().unlock();
            };
        };

        t.start();
        t.join();

        lock.writeLock().unlock();
    }


    @Test
    public void testAutoExpire() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RReadWriteLock lock1 = r.getReadWriteLock("lock");
            lock1.writeLock().lock();
        });

        RReadWriteLock lock1 = redisson.getReadWriteLock("lock");
        Thread.sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS + 1));
        Assert.assertFalse("Transient lock expired automatically", lock1.writeLock().isLocked());
    }

    @Test
    public void testHoldCount() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        testHoldCount(rwlock.readLock());
        testHoldCount(rwlock.writeLock());
    }

    private void testHoldCount(RLock lock) {
        Assert.assertEquals(0, lock.getHoldCount());
        lock.lock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());

        lock.lock();
        lock.lock();
        Assert.assertEquals(2, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());
    }

    @Test
    public void testIsHeldByCurrentThreadOtherThread() throws InterruptedException {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();

                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();

        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();

                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        Assert.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assert.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assert.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();
                Assert.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();

        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();
                Assert.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        Assert.assertFalse(lock.isLocked());
        lock.lock();
        Assert.assertTrue(lock.isLocked());
        lock.unlock();
        Assert.assertFalse(lock.isLocked());
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockFail() throws InterruptedException {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        Thread t = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                rwlock.readLock().lock();
            };
        };

        t.start();
        t.join();

        RLock lock = rwlock.readLock();
        try {
            lock.unlock();
        } finally {
            // clear scheduler
            lock.delete();
        }
    }

    @Test
    public void testLockUnlock() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    public void testReentrancy() throws InterruptedException {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();

        Assert.assertTrue(lock.tryLock());
        Assert.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock1");
                RLock lock = rwlock.readLock();
                Assert.assertTrue(lock.tryLock());
            }
        };
        thread1.start();
        thread1.join();
        lock.unlock();
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        final Random r = new SecureRandom();
        int iterations = 15;
        testSingleInstanceConcurrency(iterations, rc -> {
            RReadWriteLock rwlock = rc.getReadWriteLock("testConcurrency_SingleInstance");
            RLock lock;
            if (r.nextBoolean()) {
                lock = rwlock.writeLock();
            } else {
                lock = rwlock.readLock();
            }
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        final Random r = new SecureRandom();
        testMultiInstanceConcurrency(16, rc -> {
            for (int i = 0; i < iterations; i++) {
                boolean useWriteLock = r.nextBoolean();
                RReadWriteLock rwlock = rc.getReadWriteLock("testConcurrency_MultiInstance1");
                RLock lock;
                if (useWriteLock) {
                    lock = rwlock.writeLock();
                } else {
                    lock = rwlock.readLock();
                }
                lock.lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                rwlock = rc.getReadWriteLock("testConcurrency_MultiInstance1");
                if (useWriteLock) {
                    lock = rwlock.writeLock();
                } else {
                    lock = rwlock.readLock();
                }
                lock.unlock();
            }
        });

        Assert.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        final Random r = new SecureRandom();
        testMultiInstanceConcurrency(iterations, rc -> {
            RReadWriteLock rwlock = rc.getReadWriteLock("testConcurrency_MultiInstance2");
            RLock lock;
            if (r.nextBoolean()) {
                lock = rwlock.writeLock();
            } else {
                lock = rwlock.readLock();
            }
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

}
