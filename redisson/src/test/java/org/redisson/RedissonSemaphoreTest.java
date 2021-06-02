package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSemaphore;

public class RedissonSemaphoreTest extends BaseConcurrentTest {

    @Test
    public void testZero() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        assertThat(s.tryAcquire(0, 10, TimeUnit.MINUTES)).isTrue();
        s.release(0);
        assertThat(s.availablePermits()).isZero();
    }
    
    @Test
    public void testAcquireWithoutSetPermits() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.release();
        s.release();
        s.acquire(2);
    }
    
    @Test
    public void testTrySetPermits() {
        RSemaphore s = redisson.getSemaphore("test");
        assertThat(s.trySetPermits(10)).isTrue();
        assertThat(s.availablePermits()).isEqualTo(10);
        assertThat(s.trySetPermits(15)).isFalse();
        assertThat(s.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testAddPermits() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(10);

        s.acquire(10);
        assertThat(s.availablePermits()).isEqualTo(0);
        s.addPermits(4);
        assertThat(s.availablePermits()).isEqualTo(4);
        s.release(10);
        assertThat(s.availablePermits()).isEqualTo(14);
        s.acquire(5);
        assertThat(s.availablePermits()).isEqualTo(9);
    }

    @Test
    public void testReducePermits() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(10);
        
        s.acquire(10);
        s.addPermits(-5);
        assertThat(s.availablePermits()).isEqualTo(-5);
        s.release(10);
        assertThat(s.availablePermits()).isEqualTo(5);
        s.acquire(5);
        assertThat(s.availablePermits()).isEqualTo(0);
    }
    
    @Test
    public void testBlockingAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(1);
        s.acquire();

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        t.start();

        assertThat(s.availablePermits()).isEqualTo(0);
        s.acquire();
        assertThat(s.tryAcquire()).isFalse();
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testBlockingNAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(5);
        s.acquire(3);

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        assertThat(s.availablePermits()).isEqualTo(2);
        t.start();

        s.acquire(4);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testTryNAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(5);
        assertThat(s.tryAcquire(3)).isTrue();

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        assertThat(s.tryAcquire(4)).isFalse();

        t.start();
        t.join(1);

        Awaitility.await().between(Duration.ofMillis(900), Duration.ofMillis(1020)).untilAsserted(() -> {
            assertThat(s.tryAcquire(4, 2, TimeUnit.SECONDS)).isTrue();
        });

        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testReleaseWithoutPermits() {
        RSemaphore s = redisson.getSemaphore("test");
        s.release();

        assertThat(s.availablePermits()).isEqualTo(1);
    }

    @Test
    public void testDrainPermits() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        assertThat(s.drainPermits()).isZero();

        s.trySetPermits(10);
        s.acquire(3);

        assertThat(s.drainPermits()).isEqualTo(7);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testReleaseAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(10);
        s.acquire();
        assertThat(s.availablePermits()).isEqualTo(9);
        s.release();
        assertThat(s.availablePermits()).isEqualTo(10);
        s.acquire(5);
        assertThat(s.availablePermits()).isEqualTo(5);
        s.release(5);
        assertThat(s.availablePermits()).isEqualTo(10);
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(1);

        int iterations = 15;
        testSingleInstanceConcurrency(iterations, r -> {
            RSemaphore s1 = r.getSemaphore("test");
            try {
                s1.acquire();
            }catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            int value = lockedCounter.get();
            lockedCounter.set(value + 1);
            s1.release();
        });

        assertThat(lockedCounter.get()).isEqualTo(iterations);
    }

    @Test
    public void testConcurrencyLoopMax_MultiInstance() throws InterruptedException {
        final int iterations = 10;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(Integer.MAX_VALUE);

        testMultiInstanceConcurrency(4, r -> {
            for (int i = 0; i < iterations; i++) {
                int v = Integer.MAX_VALUE;
                if (ThreadLocalRandom.current().nextBoolean()) {
                    v = 1;
                }
                try {
                    r.getSemaphore("test").acquire(v);
                }catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getSemaphore("test").release(v);
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(4 * iterations);
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(1);

        testMultiInstanceConcurrency(16, r -> {
            for (int i = 0; i < iterations; i++) {
                try {
                    r.getSemaphore("test").acquire();
                }catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int value = lockedCounter.get();
                lockedCounter.set(value + 1);
                r.getSemaphore("test").release();
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(16 * iterations);
    }

    @Test
    public void testConcurrency_MultiInstance_1_permits() throws InterruptedException {
        int iterations = 30;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.trySetPermits(1);

        testMultiInstanceConcurrency(iterations, r -> {
            RSemaphore s1 = r.getSemaphore("test");
            try {
                s1.acquire();
            }catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            int value = lockedCounter.get();
            lockedCounter.set(value + 1);
            s1.release();
        });

        assertThat(lockedCounter.get()).isEqualTo(iterations);
    }

}
