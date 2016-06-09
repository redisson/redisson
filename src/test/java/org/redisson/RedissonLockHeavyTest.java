package org.redisson;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.redisson.core.RBucket;
import org.redisson.core.RLock;
import org.redisson.core.RSemaphore;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class RedissonLockHeavyTest extends BaseTest {
    @Parameters
    public static Collection<Object[]> data() {

        return Arrays.asList(new Object[][] { { 2, 5000 }, { 2, 50000 }, { 5, 50000 }, { 10, 50000 }, { 20, 50000 }, });
    }

    private ExecutorService executor;
    private int threads;
    private int loops;

    public RedissonLockHeavyTest(int threads, int loops) {
        this.threads = threads;
        executor = Executors.newFixedThreadPool(threads);
        this.loops = loops;
    }

    @Test
    public void lockUnlockRLock() throws Exception {
        for (int i = 0; i < threads; i++) {

            Runnable worker = new Runnable() {

                @Override
                public void run() {
                    for (int j = 0; j < loops; j++) {
                        RLock lock = redisson.getLock("RLOCK_" + j);
                        lock.lock();
                        try {
                            RBucket<String> bucket = redisson.getBucket("RBUCKET_" + j);
                            bucket.set("TEST", 30, TimeUnit.SECONDS);
                            RSemaphore semaphore = redisson.getSemaphore("SEMAPHORE_" + j);
                            semaphore.release();
                            try {
                                semaphore.acquire();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            semaphore.expire(30, TimeUnit.SECONDS);
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            };
            executor.execute(worker);
        }
        executor.shutdown();
        executor.awaitTermination(threads * loops, TimeUnit.SECONDS);

    }

}