package org.redisson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.redisson.api.*;
import org.redisson.api.ratelimiter.RateLimiterArgs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class RedissonRateLimiterTest extends RedisDockerTest {

    @Test
    public void testKeepAliveTime() throws InterruptedException {
        RRateLimiter limiter = redisson.getRateLimiter("testKeepAliveTime");
        limiter.delete();
        limiter.trySetRate(RateType.OVERALL, 1, Duration.ofSeconds(1), Duration.ofSeconds(1));
        Thread.sleep(Duration.ofMillis(1100));
        assertThat(limiter.isExists()).isFalse();
        limiter.trySetRate(RateType.OVERALL, 10, Duration.ofSeconds(2), Duration.ofSeconds(2));
        Thread.sleep(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.remainTimeToLive()).isGreaterThan(1500);


    }

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
                    .hasMessageContaining("Requested permits amount cannot exceed defined rate");
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
            pool.execute(() -> {
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
            pool.execute(() -> {
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
        assertThat(rr.availablePermits()).isEqualTo(9);

        //change to 20/s
        rr.setRate(RateType.PER_CLIENT, 20, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(20);
        //check value in Redis
        rr.acquire(1);
        assertThat(rr.availablePermits()).isEqualTo(19);

        /* Test case -- OVERALL */
        rr.setRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(10);
        //check value in Redis
        rr.acquire(1);
        assertThat(rr.availablePermits()).isEqualTo(9);

        rr.setRate(RateType.OVERALL, 20, 1, RateIntervalUnit.SECONDS);
        assertThat(rr.getConfig().getRate()).isEqualTo(20);
        //check value in Redis
        rr.acquire(1);
        assertThat(rr.availablePermits()).isEqualTo(19);
    }

    @Test
    public void testSetStateArgs() throws InterruptedException {
        String name = "testSetStateArgs";
        RRateLimiter rr = redisson.getRateLimiter(name);

        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(1)).keepState(true));
        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);

        Thread.sleep(1100);

        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);
    }

    @Test
    public void testSetStateArgsKeepState() {
        String name = "testSetStateArgsKeepState";
        RRateLimiter rr = redisson.getRateLimiter(name);

        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true));
        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);

        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 20, Duration.ofSeconds(5)).keepState(true));
        assertThat(rr.availablePermits()).isEqualTo(17);
    }

    @Test
    public void testSetStateArgsNotKeepState() {
        String name = "testSetStateArgsNotKeepState";
        RRateLimiter rr = redisson.getRateLimiter(name);

        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true));
        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);

        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 20, Duration.ofSeconds(5)));
        assertThat(rr.availablePermits()).isEqualTo(20);
    }

    @Test
    public void testUpdateRateNonExisting() {
        String name = "testUpdateRateNonExisting";
        RRateLimiter rr = redisson.getRateLimiter(name);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true))).isFalse();
        assertThat(rr.isExists()).isFalse();

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.PER_CLIENT, 10, Duration.ofSeconds(5)).keepState(true))).isFalse();
        assertThat(rr.isExists()).isFalse();
    }

    @Test
    public void testUpdateRateKeepsState() {
        String name = "testUpdateRateKeepsState";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true));

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true))).isTrue();
        assertThat(rr.availablePermits()).isEqualTo(10);

        rr.acquire(2);
        assertThat(rr.availablePermits()).isEqualTo(8);

        assertThat(rr.tryAcquire(10)).isFalse();
        assertThat(rr.tryAcquire(8)).isTrue();
    }

    @Test
    public void testUpdateRateHigherRate() throws InterruptedException {
        String name = "testUpdateRateExistingKeyHigherRate";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(2)).keepState(true));

        Thread.sleep(1000);

        rr.acquire(4); // used=4
        assertThat(rr.availablePermits()).isEqualTo(6);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 20, Duration.ofSeconds(5)).keepState(true))).isTrue();
        // newValue = 20 - used(4) = 16
        assertThat(rr.availablePermits()).isEqualTo(16);

        assertThat(rr.tryAcquire(16)).isTrue();
    }

    @Test
    public void testUpdateRateLowerRateNewValuePositive() throws InterruptedException {
        String name = "testUpdateRateLowerRateNewValuePositive";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(2)).keepState(true));

        Thread.sleep(1000);

        rr.acquire(3); // used=3
        assertThat(rr.availablePermits()).isEqualTo(7);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 5, Duration.ofSeconds(5)).keepState(true))).isTrue();
        // newValue = 5 - used(3) = 2
        assertThat(rr.availablePermits()).isEqualTo(2);

        assertThat(rr.tryAcquire(3)).isFalse();
        assertThat(rr.tryAcquire(2)).isTrue();
    }

    @Test
    public void testUpdateRateLowerRateNewValueNegative() throws InterruptedException {
        String name = "testUpdateRateLowerRateNewValueNegative";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(2)).keepState(true));

        Thread.sleep(1000);

        rr.acquire(8); // used=8
        assertThat(rr.availablePermits()).isEqualTo(2);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 5, Duration.ofSeconds(5)).keepState(true))).isTrue();
        // newValue = 5 - used(8) = -3 => 0
        assertThat(rr.availablePermits()).isEqualTo(0);

        assertThat(rr.tryAcquire(1)).isFalse();
        assertThat(rr.tryAcquire(2)).isFalse();
    }


    @Test
    public void testUpdateRatePermitResetFull() throws InterruptedException {
        String name = "testUpdateRatePermitResetFull";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(1)).keepState(true));

        rr.acquire(8); // used=8
        assertThat(rr.availablePermits()).isEqualTo(2);

        Thread.sleep(2000);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(1)).keepState(true))).isTrue();
        assertThat(rr.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testUpdateRateModeChangeClearsState() {
        String name = "testUpdateRateModeChangeClearsState";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true));

        rr.acquire(4);
        assertThat(rr.availablePermits()).isEqualTo(6);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.PER_CLIENT, 10, Duration.ofSeconds(5)).keepState(true))).isTrue();
        assertThat(rr.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testUpdateRateKeepAliveTimeLessThanIntervalThrows() {
        String name = "testUpdateRateKeepAliveTimeLessThanIntervalThrows";
        RRateLimiter rr = redisson.getRateLimiter(name);

        assertThatThrownBy(() -> rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5))
                .keepState(true)
                .keepAliveTime(Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testUpdateRateRateIntervalChangeDropsExpired() throws InterruptedException {
        String name = "testUpdateRateRateIntervalChangeDropsExpired";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(5)).keepState(true));

        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);

        Thread.sleep(1100);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(1)).keepState(true))).isTrue();
        assertThat(rr.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testUpdateRatePerClientUsesClientState() {
        String name = "testUpdateRatePerClientUsesClientState";
        RRateLimiter rr = redisson.getRateLimiter(name);
        rr.setRate(RateLimiterArgs.of(RateType.PER_CLIENT, 10, Duration.ofSeconds(5)).keepState(true));

        rr.acquire(3);
        assertThat(rr.availablePermits()).isEqualTo(7);

        assertThat(rr.updateRate(RateLimiterArgs.of(RateType.PER_CLIENT, 20, Duration.ofSeconds(5)).keepState(true))).isTrue();
        assertThat(rr.availablePermits()).isEqualTo(17);
    }

    @Test
    public void testRelease() {
        RRateLimiter rateLimiter = redisson.getRateLimiter("test_release");

        // ============================
        // PER_CLIENT
        // ============================
        rateLimiter.setRate(RateType.PER_CLIENT, 10, Duration.ofSeconds(1));
        assertThat(rateLimiter.getConfig().getRate()).isEqualTo(10);

        // acquire 3
        rateLimiter.acquire(3);
        assertThat(rateLimiter.availablePermits()).isEqualTo(7); // 10 - 3 = 7

        // release 3
        rateLimiter.release(3);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // 7 + 3 = 10

        // release 10
        rateLimiter.release(10);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // max 10

        // release 0
        rateLimiter.release(0);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // 10

        // release -1
        assertThatThrownBy(() -> rateLimiter.release(-1)).isInstanceOf(IllegalArgumentException.class);

        // ============================
        // OVERALL
        // ============================
        rateLimiter.setRate(RateType.OVERALL, 10, Duration.ofSeconds(1));
        assertThat(rateLimiter.getConfig().getRate()).isEqualTo(10);

        // acquire 3
        rateLimiter.acquire(3);
        assertThat(rateLimiter.availablePermits()).isEqualTo(7); // 10 - 3 = 7

        // release 3
        rateLimiter.release(3);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // 7 + 3 = 10

        // release 10
        rateLimiter.release(10);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // max 10

        // release 0
        rateLimiter.release(0);
        assertThat(rateLimiter.availablePermits()).isEqualTo(10); // 10

        // release -1
        assertThatThrownBy(() -> rateLimiter.release(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
