package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;

public class RedissonRateLimiterTest extends BaseTest {

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
            Thread.sleep(1000);
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
                            queue.add(System.currentTimeMillis());
                            if (counter.incrementAndGet() > 500) {
                                break;
                            }
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
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        
        int count = 0;
        long start = 0;
        for (Long value : queue) {
            if (count % 10 == 0) {
                if (start > 0) {
                    assertThat(value - start).isGreaterThan(999);
                }
                start = value;
            }
            count++;
        }
    }
    
}
