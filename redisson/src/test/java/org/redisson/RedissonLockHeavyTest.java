package org.redisson;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RedissonLockHeavyTest extends RedisDockerTest {

    public static Collection<Arguments> data() {
        return Arrays.asList(Arguments.of(2, 5000),
                Arguments.of(2, 50000), Arguments.of(5, 50000),
                Arguments.of(10, 50000),Arguments.of( 20, 50000));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void lockUnlockRLock(int threads, int loops) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
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

    @ParameterizedTest
    @MethodSource("data")
    public void tryLockUnlockRLock(int threads, int loops) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {

            Runnable worker = new Runnable() {

                @Override
                public void run() {
                    for (int j = 0; j < loops; j++) {
                        RLock lock = redisson.getLock("RLOCK_" + j);
                        try {
                            if (lock.tryLock(ThreadLocalRandom.current().nextInt(10), TimeUnit.MILLISECONDS)) {
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
                                } finally {
                                    lock.unlock();
                                }
                            }
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
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