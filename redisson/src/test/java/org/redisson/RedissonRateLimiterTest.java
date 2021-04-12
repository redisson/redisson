package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;

public class RedissonRateLimiterTest extends BaseTest {

    @Test
    public void testExpire() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("limiter");
        rr.trySetRate(RateType.OVERALL, 2, 5, RateIntervalUnit.SECONDS);
        rr.tryAcquire();

        rr.expire(1, TimeUnit.SECONDS);
        Thread.sleep(1100);
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testAcquisitionInterval() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("acquire");
        rr.trySetRate(RateType.OVERALL, 2, 5, RateIntervalUnit.SECONDS);

        assertThat(rr.tryAcquire()).isTrue();

        Thread.sleep(4000);

        assertThat(rr.tryAcquire()).isTrue();

        Thread.sleep(1050);

        assertThat(rr.tryAcquire()).isTrue();
        assertThat(rr.tryAcquire()).isFalse();
    }

    @Test
    public void testRateConfig() {
        RRateLimiter rr = redisson.getRateLimiter("acquire");
        assertThat(rr.trySetRate(RateType.OVERALL, 1, 5, RateIntervalUnit.SECONDS)).isTrue();
        
        assertThat(rr.getConfig().getRate()).isEqualTo(1);
        assertThat(rr.getConfig().getRateInterval()).isEqualTo(5000);
        assertThat(rr.getConfig().getRateType()).isEqualTo(RateType.OVERALL);
    }

    @Test
    public void testAvailablePermits() throws InterruptedException {
        RRateLimiter rt = redisson.getRateLimiter("rt2");
        rt.trySetRate(RateType.OVERALL, 10, 5, RateIntervalUnit.SECONDS);

        assertThat(rt.availablePermits()).isEqualTo(10);
        rt.acquire(1);

        Thread.sleep(6000);

        assertThat(rt.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testUpdateRateConfig() {
        RRateLimiter rr = redisson.getRateLimiter("acquire");
        assertThat(rr.trySetRate(RateType.OVERALL, 1, 5, RateIntervalUnit.SECONDS)).isTrue();
        rr.setRate(RateType.OVERALL, 2, 5, RateIntervalUnit.SECONDS);

        assertThat(rr.getConfig().getRate()).isEqualTo(2);
        assertThat(rr.getConfig().getRateInterval()).isEqualTo(5000);
        assertThat(rr.getConfig().getRateType()).isEqualTo(RateType.OVERALL);
    }
    
    @Test
    public void testPermitsExceeding() throws InterruptedException {
        RRateLimiter limiter = redisson.getRateLimiter("myLimiter");
        limiter.trySetRate(RateType.PER_CLIENT, 1, 1, RateIntervalUnit.SECONDS);
        
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.tryAcquire(20))
                    .hasMessageContaining("Requested permits amount could not exceed defined rate");
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    public void testZeroTimeout() throws InterruptedException {
        RRateLimiter limiter = redisson.getRateLimiter("myLimiter");
        limiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);
        assertThat(limiter.availablePermits()).isEqualTo(5);

        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(3);
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.availablePermits()).isEqualTo(1);
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();

        assertThat(limiter.availablePermits()).isEqualTo(0);

        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        
        Thread.sleep(1000);
        
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isTrue();
        
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
        assertThat(limiter.tryAcquire(1, 0, TimeUnit.SECONDS)).isFalse();
    }
    
    
    @Test
    public void testTryAcquire() {
        Assertions.assertTimeout(Duration.ofMillis(1500), () -> {
            RRateLimiter rr = redisson.getRateLimiter("acquire");
            assertThat(rr.trySetRate(RateType.OVERALL, 1, 5, RateIntervalUnit.SECONDS)).isTrue();

            assertThat(rr.tryAcquire(1, 1, TimeUnit.SECONDS)).isTrue();
            assertThat(rr.tryAcquire(1, 1, TimeUnit.SECONDS)).isFalse();
            assertThat(rr.tryAcquire()).isFalse();
        });
    }
    
    @Test
    public void testAcquire() {
        RRateLimiter rr = redisson.getRateLimiter("acquire");
        assertThat(rr.trySetRate(RateType.OVERALL, 1, 5, RateIntervalUnit.SECONDS)).isTrue();
        for (int i = 0; i < 10; i++) {
            rr.acquire(1);
        }
        assertThat(rr.tryAcquire()).isFalse();
    }
    
    @Test
    public void test() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("test");
        assertThat(rr.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS)).isTrue();
        assertThat(rr.trySetRate(RateType.OVERALL, 20, 1, RateIntervalUnit.SECONDS)).isFalse();
        
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 10; i++) {
                assertThat(rr.tryAcquire()).isTrue();
            }
            for (int i = 0; i < 10; i++) {
                assertThat(rr.tryAcquire()).isFalse();
            }
            Thread.sleep(1050);
        }
    }

    @Test
    public void testRemove() {
        RRateLimiter rateLimiter = redisson.getRateLimiter("test");
        assertThat(rateLimiter.delete()).isFalse();

        rateLimiter.trySetRate(RateType.OVERALL, 5L, 5L, RateIntervalUnit.MINUTES);
        assertThat(redisson.getKeys().count()).isEqualTo(1);

        rateLimiter.tryAcquire();

        boolean deleted = rateLimiter.delete();
        assertThat(redisson.getKeys().count()).isEqualTo(0);
        assertThat(deleted).isTrue();
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("test");
        assertThat(rr.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS)).isTrue();
        assertThat(rr.trySetRate(RateType.OVERALL, 20, 1, RateIntervalUnit.SECONDS)).isFalse();
        
        Queue<Long> queue = new ConcurrentLinkedQueue<Long>();
        AtomicLong counter = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (rr.tryAcquire()) {
                            if (counter.incrementAndGet() > 500) {
                                break;
                            }
                            queue.add(System.currentTimeMillis());
                        }
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        
                    }
                }
            });
        }
        
        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
        
        int count = 0;
        long start = 0;
        for (Long value : queue) {
            if (count % 10 == 0) {
                if (start > 0) {
                    assertThat(value - start).isGreaterThan(980);
                }
                start = value;
            }
            count++;
        }
    }
    
}
