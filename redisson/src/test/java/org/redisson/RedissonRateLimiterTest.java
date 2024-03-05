package org.redisson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonRateLimiterTest extends RedisDockerTest {

    @Test
    public void testExpire2() throws InterruptedException {
        RRateLimiter rateLimiter = redisson.getRateLimiter("test1");
        rateLimiter.trySetRate(RateType.OVERALL, 5, 5, RateIntervalUnit.SECONDS);
        rateLimiter.expire(Duration.ofSeconds(10));
        rateLimiter.acquire();
        Thread.sleep(12000);
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testRateValue() throws InterruptedException {
        RRateLimiter rateLimiter = redisson.getRateLimiter("test1");
        int rate = 10_000;
        rateLimiter.setRate(RateType.OVERALL, rate, 10_000, RateIntervalUnit.MILLISECONDS);

        ExecutorService e = Executors.newFixedThreadPool(200);
        for (int i = 0; i < 200; i++) {
            e.execute(() -> {
                while (true) {
                    rateLimiter.acquire();
                }
            });
        }

        RScoredSortedSet<Object> sortedSet = redisson.getScoredSortedSet("{test1}:permits");
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sizes.add(sortedSet.size());
            Thread.sleep(1000);
        }

        assertThat(sizes.stream().filter(s -> s == rate).count()).isGreaterThanOrEqualTo(16);
        e.shutdownNow();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("limiter");
        rr.trySetRate(RateType.OVERALL, 2, 5, RateIntervalUnit.SECONDS);
        rr.tryAcquire();

        rr.expire(Duration.ofSeconds(1));
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
    @Timeout(2)
    public void testTryAcquire() {
        RRateLimiter rr = redisson.getRateLimiter("acquire");
        assertThat(rr.trySetRate(RateType.OVERALL, 1, 5, RateIntervalUnit.SECONDS)).isTrue();

        assertThat(rr.tryAcquire(1, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(rr.tryAcquire(1, 1, TimeUnit.SECONDS)).isFalse();
        assertThat(rr.tryAcquire()).isFalse();
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
    public void testConcurrency2() throws InterruptedException {
        RRateLimiter rr = redisson.getRateLimiter("test");
        rr.trySetRate(RateType.OVERALL, 18, 1, RateIntervalUnit.SECONDS);

        Queue<Long> queue = new ConcurrentLinkedQueue<Long>();
        AtomicLong counter = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            rr.acquire();
                            queue.add(System.currentTimeMillis());
                            if (counter.incrementAndGet() > 1000) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();

        int count = 0;
        long start = 0;
        boolean skip = true;
        for (Long value : queue) {
            if (start == 0) {
                start = value;
            }
            count++;
            if (value - start >= 1000) {
                if (!skip) {
                    assertThat(count).isLessThanOrEqualTo(18);
                } else {
                    skip = false;
                }
                start = 0;
                count = 0;
            }
        }
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
                    assertThat(value - start).isGreaterThan(940);
                }
                start = value;
            }
            count++;
        }
    }

    @Test
    public void testChangeRate() {
        /* Test case -- PRE_CLIENT */
        RRateLimiter rr = redisson.getRateLimiter("test_change_rate");
        rr.setRate(RateType.PER_CLIENT, 10, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(10);
        //check value in Redis
        rr.acquire(1);
        String valueKey = redisson.getKeys().getKeysStream().filter(k -> k.contains("value:")).findAny().get();
        Long value = redisson.getAtomicLong(valueKey).get();
        assertThat(value).isEqualTo(9);

        //change to 20/s
        rr.setRate(RateType.PER_CLIENT, 20, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(20);
        //check value in Redis
        rr.acquire(1);
        value = redisson.getAtomicLong(valueKey).get();
        assertThat(value).isEqualTo(19);

        /* Test case -- OVERALL */
        rr.setRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(10);
        //check value in Redis
        rr.acquire(1);
        valueKey = redisson.getKeys().getKeysStream().filter(k -> k.endsWith("value")).findAny().get();
        value = redisson.getAtomicLong(valueKey).get();
        assertThat(value).isEqualTo(9);

        rr.setRate(RateType.OVERALL, 20, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(20);
        //check value in Redis
        rr.acquire(1);
        value = redisson.getAtomicLong(valueKey).get();
        assertThat(value).isEqualTo(19);

        //clean all keys in test
        redisson.getKeys().deleteByPattern("*test_change_rate*");
    }
    
}
