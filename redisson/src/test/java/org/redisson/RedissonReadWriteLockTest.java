package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class RedissonReadWriteLockTest extends BaseConcurrentTest {

    @Test
    public void testReadLockExpiration() throws Exception{
        Thread thread1 = new Thread(() -> {
            RReadWriteLock rReadWriteLock = redisson.getReadWriteLock("test");
            RLock readLock = rReadWriteLock.readLock();
            readLock.lock(10, TimeUnit.SECONDS);
            try {
                Thread.sleep(9100);
            } catch (Exception e){}
            readLock.unlock();
        });


        Thread thread2 = new Thread(() -> {
            RReadWriteLock rReadWriteLock = redisson.getReadWriteLock("test");
            RLock readLock = rReadWriteLock.readLock();
            readLock.lock(3, TimeUnit.SECONDS);
            try {
                Thread.sleep(2800);
            } catch (Exception e){}
            readLock.unlock();
        });

        AtomicBoolean flag = new AtomicBoolean();
        Thread thread3 = new Thread(() -> {
            RReadWriteLock rReadWriteLock = redisson.getReadWriteLock("test");
            RLock writeLock = rReadWriteLock.writeLock();
            writeLock.lock(10, TimeUnit.SECONDS);
            flag.set(true);
            writeLock.unlock();
        });

        thread1.start();
        thread1.join(300);
        thread2.start();
        thread2.join(300);
        thread3.start();
        thread3.join(300);

        Awaitility.await().between(8, TimeUnit.SECONDS, 10, TimeUnit.SECONDS).untilTrue(flag);
    }

    @Test
    public void testReadLockIsLocked() throws InterruptedException {
        RReadWriteLock readWriteLock = redisson.getReadWriteLock("TEST");
        RLock writeLock = readWriteLock.writeLock();
        RLock readLock = readWriteLock.readLock();

        writeLock.lock();
        assertThat(readLock.isLocked()).isFalse();
        assertThat(readLock.tryLock(10, TimeUnit.SECONDS)).isTrue();
        assertThat(readLock.isLocked()).isTrue();
    }

    @Test
    public void testReadLockExpirationRenewal() throws InterruptedException {
        int threadCount = 50;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount/5);

        AtomicInteger exceptions = new AtomicInteger();
        for (int i=0; i<threadCount; i++) {
            executorService.submit(()-> {
                try {
                    RReadWriteLock rw1 = redisson.getReadWriteLock("mytestlock");
                    RLock readLock = rw1.readLock();
                    readLock.lock();
                    try {
                        Thread.sleep(redisson.getConfig().getLockWatchdogTimeout() + 5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    readLock.unlock();
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        executorService.shutdown();
        assertThat(executorService.awaitTermination(180, TimeUnit.SECONDS)).isTrue();
        assertThat(exceptions.get()).isZero();
    }

    @Test
    public void testName() throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService service = Executors.newFixedThreadPool(10);
        RReadWriteLock rwlock = redisson.getReadWriteLock("{test}:abc:key");
        RLock rlock = rwlock.readLock();

        List<Callable<Void>> callables = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
          callables.add(() -> {
            for (int j = 0; j < 10; j++) {
              rlock.lock();
              try {
              } finally {
                rlock.unlock();
              }
            }
            return null;
          });
        }

        List<Future<Void>> futures = service.invokeAll(callables);
        for (Future<Void> future : futures) {
            assertThatCode(future::get).doesNotThrowAnyException();
        }

        service.shutdown();
        assertThat(service.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }


    @Test
    public void testWriteLockExpiration() throws InterruptedException {
        RReadWriteLock rw1 = redisson.getReadWriteLock("test2s3");

        RLock l1 = rw1.writeLock();
        assertThat(l1.tryLock(10000, 10000, TimeUnit.MILLISECONDS)).isTrue();
        RLock l2 = rw1.writeLock();
        assertThat(l2.tryLock(1000, 1000, TimeUnit.MILLISECONDS)).isTrue();

        await().atMost(Duration.ofSeconds(10)).until(() -> {
            RReadWriteLock rw2 = redisson.getReadWriteLock("test2s3");
            try {
                return !rw2.writeLock().tryLock(3000, 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    @Test
    public void testInCluster() {
        testInCluster(redisson -> {
            RReadWriteLock s = redisson.getReadWriteLock("1234");
            s.writeLock().lock();
            s.readLock().lock();
            s.readLock().unlock();
            s.writeLock().unlock();
        });
    }

    @Test
    public void testReadLockLeaseTimeoutDiffThreadsWRR() throws InterruptedException {
        RLock writeLock = redisson.getReadWriteLock("my_read_write_lock").writeLock();
        Assertions.assertTrue(writeLock.tryLock(1, 10, TimeUnit.SECONDS));

        final AtomicInteger executed = new AtomicInteger();
        Thread t1 = new Thread(() -> {
            RLock readLock = redisson.getReadWriteLock("my_read_write_lock").readLock();
            readLock.lock();
            executed.incrementAndGet();
        });

        Thread t2 = new Thread(() -> {
            RLock readLock = redisson.getReadWriteLock("my_read_write_lock").readLock();
            readLock.lock();
            executed.incrementAndGet();
        });

        t1.start();
        t2.start();

        await().atMost(11, TimeUnit.SECONDS).until(() -> executed.get() == 2);
    }

    @Test
    public void testReadLockLeaseTimeoutDiffThreadsRRW() throws InterruptedException {
        new Thread(() -> {
            RLock readLock = redisson.getReadWriteLock("my_read_write_lock").readLock();
            try {
                Assertions.assertTrue(readLock.tryLock(1, 10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        Thread.sleep(5000);

        new Thread(() -> {
            RLock readLock2 = redisson.getReadWriteLock("my_read_write_lock").readLock();
            try {
                Assertions.assertTrue(readLock2.tryLock(1, 10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            readLock2.unlock();
        }).start();

        final AtomicBoolean executed = new AtomicBoolean();
        new Thread(() -> {
            RLock writeLock = redisson.getReadWriteLock("my_read_write_lock").writeLock();
            try {
                boolean locked = writeLock.tryLock(10, 10, TimeUnit.SECONDS);
                executed.set(locked);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        await().atMost(6, TimeUnit.SECONDS).untilTrue(executed);
    }


    @Test
    public void testReadLockLeaseTimeout() throws InterruptedException {
        RLock readLock = redisson.getReadWriteLock("my_read_write_lock").readLock();
        Assertions.assertTrue(readLock.tryLock(1, 4, TimeUnit.SECONDS));

        Thread.sleep(3000);
        RLock readLock2 = redisson.getReadWriteLock("my_read_write_lock").readLock();
        Assertions.assertTrue(readLock2.tryLock(1, 4, TimeUnit.SECONDS));
        readLock2.unlock();

        Thread.sleep(2000);

        RLock writeLock = redisson.getReadWriteLock("my_read_write_lock").writeLock();
        Assertions.assertTrue(writeLock.tryLock());
    }

    @Test
    public void testWR() throws InterruptedException {
        RReadWriteLock rw = redisson.getReadWriteLock("my_read_write_lock");
        RLock writeLock = rw.writeLock();
        writeLock.lock();

        rw.readLock().lock();
        assertThat(writeLock.isLocked()).isTrue();
        rw.readLock().unlock();

        assertThat(writeLock.isLocked()).isTrue();
        writeLock.unlock();
        assertThat(writeLock.isLocked()).isFalse();
    }

    @Test
    public void testWriteRead() throws InterruptedException {
        RReadWriteLock readWriteLock = redisson.getReadWriteLock("TEST");
        readWriteLock.writeLock().lock();

        int threads = 20;
        CountDownLatch ref = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t1 = new Thread(() -> {
                readWriteLock.readLock().lock();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                }
                readWriteLock.readLock().unlock();
                ref.countDown();
            });
            t1.start();
            t1.join(100);
        }

        readWriteLock.writeLock().unlock();

        assertThat(ref.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testWriteReadReentrancy() throws InterruptedException {
        RReadWriteLock readWriteLock = redisson.getReadWriteLock("TEST");
        readWriteLock.writeLock().lock();

        java.util.concurrent.locks.Lock rLock = readWriteLock.readLock();
        Assertions.assertTrue(rLock.tryLock());

        AtomicBoolean ref = new AtomicBoolean();
        Thread t1 = new Thread(() -> {
            boolean success = readWriteLock.readLock().tryLock();
            ref.set(success);
        });
        t1.start();
        t1.join();

        Assertions.assertFalse(ref.get());

        readWriteLock.writeLock().unlock();
        Assertions.assertFalse(readWriteLock.writeLock().tryLock());
        rLock.unlock();

        Assertions.assertTrue(readWriteLock.writeLock().tryLock());
        readWriteLock.writeLock().unlock();
    }

    @Test
    public void testWriteLock() throws InterruptedException {
        final RReadWriteLock lock = redisson.getReadWriteLock("lock");

        final RLock writeLock = lock.writeLock();
        writeLock.lock();

        Assertions.assertTrue(lock.writeLock().tryLock());

        Thread t = new Thread() {
            public void run() {
                 Assertions.assertFalse(writeLock.isHeldByCurrentThread());
                 Assertions.assertTrue(writeLock.isLocked());
                 Assertions.assertFalse(lock.readLock().tryLock());
                 Assertions.assertFalse(redisson.getReadWriteLock("lock").readLock().tryLock());

                 try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                 Assertions.assertTrue(lock.readLock().tryLock());
                 Assertions.assertTrue(redisson.getReadWriteLock("lock").readLock().tryLock());
            };
        };

        t.start();
        t.join(50);

        writeLock.unlock();
        Assertions.assertTrue(lock.readLock().tryLock());
        Assertions.assertTrue(writeLock.isHeldByCurrentThread());
        writeLock.unlock();
        Thread.sleep(1000);

        Assertions.assertFalse(lock.writeLock().tryLock());
        Assertions.assertFalse(lock.writeLock().isLocked());
        Assertions.assertFalse(lock.writeLock().isHeldByCurrentThread());
        lock.writeLock().forceUnlock();
    }

    @Test
    public void testMultiRead() throws InterruptedException {
        final RReadWriteLock lock = redisson.getReadWriteLock("lock");
        Assertions.assertFalse(lock.readLock().forceUnlock());

        final RLock readLock1 = lock.readLock();
        readLock1.lock();

        Assertions.assertFalse(lock.writeLock().tryLock());

        final AtomicReference<RLock> readLock2 = new AtomicReference<RLock>();
        Thread t = new Thread() {
            public void run() {
                 RLock r = lock.readLock();
                 Assertions.assertFalse(readLock1.isHeldByCurrentThread());
                 Assertions.assertTrue(readLock1.isLocked());
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

        Assertions.assertTrue(readLock2.get().isLocked());

        readLock1.unlock();
        Assertions.assertFalse(lock.writeLock().tryLock());
        Assertions.assertFalse(readLock1.isHeldByCurrentThread());
        Thread.sleep(1000);

        Assertions.assertFalse(readLock2.get().isLocked());
        Assertions.assertTrue(lock.writeLock().tryLock());
        Assertions.assertTrue(lock.writeLock().isLocked());
        Assertions.assertTrue(lock.writeLock().isHeldByCurrentThread());
        lock.writeLock().unlock();

        Assertions.assertFalse(lock.writeLock().isLocked());
        Assertions.assertFalse(lock.writeLock().isHeldByCurrentThread());
        Assertions.assertTrue(lock.writeLock().tryLock());
        lock.writeLock().forceUnlock();
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
    public void testReadWriteTTL() throws InterruptedException {
        RReadWriteLock rwlock = redisson.getReadWriteLock("rwlock");
        rwlock.writeLock().lock();
        rwlock.readLock().lock();

        for (int i = 0; i < 5; i++) {
            assertThat(rwlock.readLock().remainTimeToLive()).isGreaterThan(19000);
            assertThat(rwlock.writeLock().remainTimeToLive()).isGreaterThan(19000);
            TimeUnit.SECONDS.sleep(5);
        }

        rwlock.writeLock().unlock();

        for (int i = 0; i < 5; i++) {
            assertThat(rwlock.readLock().remainTimeToLive()).isGreaterThan(19000);
            TimeUnit.SECONDS.sleep(5);
        }

        rwlock.readLock().unlock();
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
                Assertions.assertTrue(spendTime < 2050);
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
                Assertions.assertTrue(spendTime < 2050);
                lock1.writeLock().unlock();
            };
        };

        t.start();
        t.join();

        assertThatThrownBy(() -> {
            lock.writeLock().unlock();
        }).isInstanceOf(IllegalMonitorStateException.class);
    }

    @Test
    public void testAutoExpire() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RReadWriteLock lock1 = r.getReadWriteLock("lock");
            lock1.writeLock().lock();
        });

        RReadWriteLock lock1 = redisson.getReadWriteLock("lock");
        await().atMost(redisson.getConfig().getLockWatchdogTimeout() + 1000, TimeUnit.MILLISECONDS).until(() -> !lock1.writeLock().isLocked());
    }

    @Test
    public void testHoldCount() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        testHoldCount(rwlock.readLock());
        testHoldCount(rwlock.writeLock());
    }

    private void testHoldCount(RLock lock) {
        Assertions.assertEquals(0, lock.getHoldCount());
        lock.lock();
        Assertions.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(0, lock.getHoldCount());

        lock.lock();
        lock.lock();
        Assertions.assertEquals(2, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(0, lock.getHoldCount());
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

                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();

        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();

                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
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
                Assertions.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();

        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
                RLock lock = rwlock.readLock();
                Assertions.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RReadWriteLock rwlock = redisson.getReadWriteLock("lock");
        RLock lock = rwlock.readLock();
        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testUnlockFail() {
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> {
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
                lock.forceUnlock();
            }
        });
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

        Assertions.assertTrue(lock.tryLock());
        Assertions.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RReadWriteLock rwlock = redisson.getReadWriteLock("lock1");
                RLock lock = rwlock.readLock();
                Assertions.assertTrue(lock.tryLock());
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

        Assertions.assertEquals(iterations, lockedCounter.get());
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

        Assertions.assertEquals(16 * iterations, lockedCounter.get());
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

        Assertions.assertEquals(iterations, lockedCounter.get());
    }

}
