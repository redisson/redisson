package org.redisson;

import org.joor.Reflect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.config.NameMapper;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonFairLockTest extends BaseConcurrentTest {

    private final Logger log = LoggerFactory.getLogger(RedissonFairLockTest.class);

    @Test
    public void testLeaseTimeout() throws InterruptedException {
        ExecutorService ee = Executors.newFixedThreadPool(8);
        List<Integer> list = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Thread.sleep(100);
            int i = index;
            ee.submit(() -> {
                RLock fairLock = redisson.getFairLock("lock");
                try {
                    boolean acquired = fairLock.tryLock(10, 1, TimeUnit.SECONDS);
                    if (acquired) {
                        list.add(i);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            });
        }

        ee.shutdown();
        assertThat(ee.awaitTermination(11, TimeUnit.SECONDS)).isTrue();

        List<Integer> s = IntStream.range(0, 8).boxed().collect(Collectors.toList());
        assertThat(list).isEqualTo(s);
    }

    @Test
    public void testMultipleLocks() throws InterruptedException {
        ExecutorService executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                1000L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());

        Config cfg = createConfig();
        cfg.useSingleServer().setSubscriptionsPerConnection(100);
        RedissonClient redisson = Redisson.create(cfg);

        AtomicInteger acquiredLocks = new AtomicInteger();
        for (int i = 0; i < 500; i++) {
            executorService.submit(() -> {
                RLock test = redisson.getFairLock("lock");
                try {
                    test.lock(5, TimeUnit.SECONDS);
                    try {
                        Thread.sleep(200); // 200ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    acquiredLocks.incrementAndGet();
                } finally {
                    test.unlock();
                }
            });
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(3, TimeUnit.MINUTES)).isTrue();
        assertThat(acquiredLocks.get()).isEqualTo(500);
        redisson.shutdown();
    }

    @Test
    public void testWaitTimeoutDrift() throws Exception {
        int leaseTimeSeconds = 30;
        RLock lock = redisson.getFairLock("test-fair-lock");
        AtomicBoolean lastThreadTryingToLock = new AtomicBoolean(false);


        //create a scenario where the same 3 threads keep on trying to get a lock
        //to exacerbate the problem, use a very short wait time and a long lease time
        //this will end up setting the queue timeout score to a value far away in the future
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 50; i++) {
            final int finalI = i;
            executor.submit(() -> {
                log.info("running {} in thread {}", finalI, Thread.currentThread().getId());
                try {
                    if (lock.tryLock(500, leaseTimeSeconds * 1000, TimeUnit.MILLISECONDS)) {
                        log.info("Lock taken by thread {}", Thread.currentThread().getId());
                        Thread.sleep(10000);
                        try {
                            //this could fail before use sleep for the same value as the lock expiry, that's fine
                            //for the purpose of this test
                            lock.unlock();
                            log.info("Lock released by thread {}", Thread.currentThread().getId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (InterruptedException ex) {
                    log.warn("Interrupted {}", Thread.currentThread().getId());
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            });
            //for the first 3 threads, add a 50ms delay. This is to recreate the worst case scenario, where all threads
            //attempting to lock do so in a staggered pattern. This delay will be carried over by the thread pool.
            if (i < 3) {
                Thread.sleep(50);
            }
        }
        //we now launch one more thread and kill it before it manages to fail and clean up
        //that thread will end up with a timeout that will prevent any others from taking the lock for a long time
        executor.submit(() -> {
            log.info("Final thread trying to take the lock with thread id: {}", Thread.currentThread().getId());
            try {
                lastThreadTryingToLock.set(true);
                if (lock.tryLock(30000, 30000, TimeUnit.MILLISECONDS)) {
                    log.info("Lock taken by final thread {}", Thread.currentThread().getId());
                    Thread.sleep(1000);
                    lock.unlock();
                    log.info("Lock released by final thread {}", Thread.currentThread().getId());
                }
            } catch (InterruptedException ex) {
                log.warn("Interrupted {}", Thread.currentThread().getId());
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        //now we wait for all others threads to stop trying, and only the last thread is running
        while (!lastThreadTryingToLock.get()) {
            Thread.sleep(100);
        }
        //try to kill that last thread, and don't let it clean up after itself
        executor.shutdownNow();
        //force the lock to unlock just in case
        try {
            lock.forceUnlock();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (lock.isLocked()) {
            Assertions.fail("Lock should have been unlocked by now");
        }
        //check the timeout scores - they should all be within a reasonable amount of time from now
        List<Long> queue = redisson.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_ONLY,
                "local result = {}; " +
                        "local timeouts = redis.call('zrange', KEYS[1], 0, 99, 'WITHSCORES'); " +
                        "for i=1,#timeouts,2 do " +
                        "table.insert(result, timeouts[i+1]); " +
                        "end; " +
                        "return result; ",
                RScript.ReturnType.LIST,
                Collections.singletonList("redisson_lock_timeout:{test-fair-lock}"));

        int i = 0;
        for (Long timeout : queue) {
            long epiry = ((timeout - new Date().getTime()) / 1000);
            log.info("Item {} expires in {} seconds", i++, epiry);
            //the Redisson library uses this 60000*5ms delay in the code
            if (epiry > leaseTimeSeconds + 60*5) {
                Assertions.fail("It would take more than " + leaseTimeSeconds + "s to get the lock!");
            }
        }
    }

    @Test
    public void testLockAcquiredTimeoutDrift() throws Exception {
        int leaseTimeSeconds = 30;
        RLock lock = redisson.getFairLock("test-fair-lock");

        //create a scenario where the same 3 threads keep on trying to get a lock
        //to exacerbate the problem, use a very short wait time and a long lease time
        //this will end up setting the queue timeout score to a value far away in the future
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            final int finalI = i;
            executor.submit(() -> {
                log.info("running {} in thread {}", finalI, Thread.currentThread().getId());
                try {
                    if (lock.tryLock(3000, leaseTimeSeconds * 1000, TimeUnit.MILLISECONDS)) {
                        log.info("Lock taken by thread {}", Thread.currentThread().getId());
                        Thread.sleep(100);
                        try {
                            //this could fail before use sleep for the same value as the lock expiry, that's fine
                            //for the purpose of this test
                            lock.unlock();
                            log.info("Lock released by thread {}", Thread.currentThread().getId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (InterruptedException ex) {
                    log.warn("Interrupted {}", Thread.currentThread().getId());
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            });
            //for the first 3 threads, add a 50ms delay. This is to recreate the worst case scenario, where all threads
            //attempting to lock do so in a staggered pattern. This delay will be carried over by the thread pool.
            Thread.sleep(50);
        }

        AtomicBoolean lastThreadTryingToLock = new AtomicBoolean(false);
        //we now launch one more thread and kill it before it manages to fail and clean up
        //that thread will end up with a timeout that will prevent any others from taking the lock for a long time
        executor.submit(() -> {
            log.info("Final thread trying to take the lock with thread id: {}", Thread.currentThread().getId());
            try {
                lastThreadTryingToLock.set(true);
                if (lock.tryLock(30000, 30000, TimeUnit.MILLISECONDS)) {
                    log.info("Lock taken by final thread {}", Thread.currentThread().getId());
                    Thread.sleep(1000);
                    lock.unlock();
                    log.info("Lock released by final thread {}", Thread.currentThread().getId());
                }
            } catch (InterruptedException ex) {
                log.warn("Interrupted");
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        //now we wait for all others threads to stop trying, and only the last thread is running
        while (!lastThreadTryingToLock.get()) {
            Thread.sleep(100);
        }
        //try to kill that last thread, and don't let it clean up after itself
        executor.shutdownNow();
        //force the lock to unlock just in case
        try {
            lock.forceUnlock();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (lock.isLocked()) {
            Assertions.fail("Lock should have been unlocked by now");
        }
        //check the timeout scores - they should all be within a reasonable amount of time from now
        List<Long> queue = redisson.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_ONLY,
                "local result = {}; " +
                        "local timeouts = redis.call('zrange', KEYS[1], 0, 99, 'WITHSCORES'); " +
                        "for i=1,#timeouts,2 do " +
                        "table.insert(result, timeouts[i+1]); " +
                        "end; " +
                        "return result; ",
                RScript.ReturnType.LIST,
                Collections.singletonList("redisson_lock_timeout:{test-fair-lock}"));

        int i = 0;
        for (Long timeout : queue) {
            long epiry = ((timeout - new Date().getTime()) / 1000);
            log.info("Item {} expires in {} seconds", i++, epiry);
            //the Redisson library uses this 5000ms delay in the code
            if (epiry > leaseTimeSeconds + 60*5) {
                Assertions.fail("It would take more than " + leaseTimeSeconds + "s to get the lock!");
            }
        }
    }

    @Test
    public void testAcquireFailedTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 30_000;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        RedissonClient redisson = Redisson.create(createConfig());

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testAcquireFailedTimeoutDrift_Descrete");

        // clear out any prior state
        lock.delete();

        long threadInit = 101;
        long threadFirstWaiter = 102;
        long threadSecondWaiter = 103;
        long threadThirdWaiter = 104;
        long threadFourthWaiter = 105;

        // take the lock successfully
        Long ttl = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadInit, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);

        // fail to get the lock, but end up in the thread queue w/ ttl + 5s timeout
        Long firstTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(firstTTL);
        Assertions.assertTrue(firstTTL >= 29900 && firstTTL <= 30100, "Expected 30000 +/- 100 but was " + firstTTL);

        // fail to get the lock again, but end up in the thread queue w/ ttl + 10s timeout
        Long secondTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(secondTTL);
        Assertions.assertTrue(secondTTL >= 34900 && secondTTL <= 35100, "Expected 35000 +/- 100 but was " + secondTTL);

        // try the third, and check the TTL
        Long thirdTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);
        Assertions.assertTrue(thirdTTL >= 39900 && thirdTTL <= 40100, "Expected 40000 +/- 100 but was " + thirdTTL);

        // try the fourth, and check the TTL
        Long fourthTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFourthWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(fourthTTL);
        Assertions.assertTrue(fourthTTL >= 44900 && fourthTTL <= 45100, "Expected 45000 +/- 100 but was " + fourthTTL);

        // wait timeout the second waiter
        lock.acquireFailedAsync(5000, TimeUnit.MILLISECONDS, threadSecondWaiter).toCompletableFuture().join();;

        // try the first, and check the TTL
        firstTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(firstTTL);
        Assertions.assertTrue(firstTTL >= 29900 && firstTTL <= 30100, "Expected 30000 +/- 100 but was " + firstTTL);

        // try the third, and check the TTL
        thirdTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);
        Assertions.assertTrue(thirdTTL >= 29900 && thirdTTL <= 30100, "Expected 35000 +/- 300 but was " + thirdTTL);

        // try the fourth, and check the TTL
        fourthTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFourthWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(fourthTTL);
        Assertions.assertTrue(fourthTTL >= 29900 && fourthTTL <= 30100, "Expected 40000 +/- 100 but was " + fourthTTL);

        // unlock the original lock holder
        Boolean unlocked = lock.unlockInnerAsync(threadInit, null).toCompletableFuture().join();
        Assertions.assertNotNull(unlocked);
        Assertions.assertTrue(unlocked);

        // acquire the lock immediately with the 1nd
        ttl = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);

        // try the third, and check the TTL
        thirdTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);
        Assertions.assertTrue(thirdTTL >= 29700 && thirdTTL <= 30300, "Expected 30000 +/- 300 but was " + thirdTTL);

        fourthTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFourthWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(fourthTTL);
        Assertions.assertTrue(fourthTTL >= 29700 && fourthTTL <= 30300, "Expected 35000 +/- 100 but was " + fourthTTL);
        redisson.shutdown();
    }

    @Test
    public void testLockAcquiredBooleanTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 500;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        Config config = createConfig()
                .setFairLockWaitTimeout(100);

        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testLockAcquiredTimeoutDrift_Descrete");

        // clear out any prior state
        lock.delete();

        long threadInit = 101;
        long threadFirstWaiter = 102;
        long threadSecondWaiter = 103;
        long threadThirdWaiter = 104;

        // take the lock successfully
        Boolean locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadInit, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertTrue(locked);

        // fail to get the lock, but end up in the thread queue w/ ttl + 100ms timeout
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertFalse(locked);

        // fail to get the lock again, but end up in the thread queue w/ ttl + 200ms timeout
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertFalse(locked);

        // unlock the original lock holder
        Boolean unlocked = lock.unlockInnerAsync(threadInit, null).toCompletableFuture().join();
        Assertions.assertTrue(unlocked);

        // get the lock
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertTrue(locked);

        // fail to get the lock, keeping ttl of lock ttl + 200ms
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertFalse(locked);

        // fail to get the lock, keeping ttl of lock ttl + 100ms
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertFalse(locked);

        // fail to get the lock, keeping ttl of lock ttl + 200ms
        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertFalse(locked);

        Thread.sleep(500);

        locked = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_NULL_BOOLEAN).toCompletableFuture().join();;
        Assertions.assertTrue(locked);
        redisson.shutdown();
    }

    @Test
    public void testLockAcquiredTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 300_000;

        RedissonClient redisson = Redisson.create(createConfig().setLockWatchdogTimeout(2 * leaseTime));

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testLockAcquiredTimeoutDrift_Descrete");

        // clear out any prior state
        lock.delete();

        long threadInit = 101;
        long threadFirstWaiter = 102;
        long threadSecondWaiter = 103;
        long threadThirdWaiter = 104;

        // take the lock successfully
        Long ttl = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadInit, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);

        // fail to get the lock, but end up in the thread queue w/ ttl + 5s timeout
        Long firstTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(firstTTL);

        // fail to get the lock again, but end up in the thread queue w/ ttl + 10s timeout
        Long secondTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(secondTTL);

        // unlock the original lock holder
        Boolean unlocked = lock.unlockInnerAsync(threadInit, null).toCompletableFuture().join();
        Assertions.assertNotNull(unlocked);
        Assertions.assertTrue(unlocked);

        ttl = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);

        Long thirdTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);

        Long secondTTLAgain = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(secondTTLAgain);
        long diff = secondTTL - secondTTLAgain;
        Assertions.assertTrue(diff > 4900 && diff < 5100, "Expected 5000 +/- 100 but was " + diff);
        diff = thirdTTL - secondTTLAgain;
        Assertions.assertTrue(diff > 4900 && diff < 5100, "Expected 5000 +/- 100 but was " + diff);

        thirdTTL = lock.tryLockInnerAsync(5000, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);
        Assertions.assertTrue(thirdTTL <= secondTTLAgain);
        redisson.shutdown();
    }

    @Test
    public void testAbandonedTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 500;
        long threadWaitTime = 100;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        Config config = createConfig()
                .setFairLockWaitTimeout(threadWaitTime);
        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testAbandonedTimeoutDrift_Descrete");

        // clear out any prior state
        lock.delete();

        long threadInit = 101;
        long threadFirstWaiter = 102;
        long threadSecondWaiter = 103;
        long threadThirdWaiter = 104;

        // take the lock successfully
        Long ttl = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadInit, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);

        // fail to get the lock, but end up in the thread queue w/ ttl + 5s timeout
        Long firstTTL = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadFirstWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(firstTTL);

        // fail to get the lock again, but end up in the thread queue w/ ttl + 10s timeout
        Long secondTTL = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadSecondWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(secondTTL);

        Long thirdTTL = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNotNull(thirdTTL);

        long diff = thirdTTL - firstTTL;
        Assertions.assertTrue(diff > 190 && diff < 210, "Expected 200 +/- 10 but was " + diff);

        RScoredSortedSet<String> deadlines = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        deadlines.remove(lock.getLockName(threadFirstWaiter));
        deadlines.remove(lock.getLockName(threadSecondWaiter));
        deadlines.remove(lock.getLockName(threadThirdWaiter));

        Thread.sleep(thirdTTL + threadWaitTime);

        ttl = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);
        redisson.shutdown();
    }

    @Test
    public void testFirstThreadDeathTimeoutDrift() throws Exception {
        int leaseTimeSeconds = 30;
        RLock lock = redisson.getFairLock("test-fair-lock");
        AtomicBoolean lastThreadTryingToLock = new AtomicBoolean(false);


        //create a scenario where the same 3 threads keep on trying to get a lock
        //to exacerbate the problem, use a very short wait time and a long lease time
        //this will end up setting the queue timeout score to a value far away in the future
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 10; i++) {
            final int finalI = i;
            executor.submit(() -> {
                log.info("running {} in thread {}", finalI, Thread.currentThread().getId());
                try {
                    if (lock.tryLock(3000, leaseTimeSeconds * 1000, TimeUnit.MILLISECONDS)) {
                        log.info("Lock taken by thread {}", Thread.currentThread().getId());
                        Thread.sleep(100);
                        try {
                            //this could fail before use sleep for the same value as the lock expiry, that's fine
                            //for the purpose of this test
                            lock.unlock();
                            log.info("Lock released by thread {}", Thread.currentThread().getId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (InterruptedException ex) {
                    log.warn("Interrupted {}", Thread.currentThread().getId());
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            });
            //for the first 3 threads, add a 50ms delay. This is to recreate the worst case scenario, where all threads
            //attempting to lock do so in a staggered pattern. This delay will be carried over by the thread pool.
            if (i < 3) {
                Thread.sleep(50);
            }
        }
        //we now launch one more thread and kill it before it manages to fail and clean up
        //that thread will end up with a timeout that will prevent any others from taking the lock for a long time
        executor.submit(() -> {
            log.info("Final thread trying to take the lock with thread id: {}", Thread.currentThread().getId());
            try {
                lastThreadTryingToLock.set(true);
                if (lock.tryLock(30000, 30000, TimeUnit.MILLISECONDS)) {
                    log.info("Lock taken by final thread {}", Thread.currentThread().getId());
                    Thread.sleep(1000);
                    lock.unlock();
                    log.info("Lock released by final thread {}", Thread.currentThread().getId());
                }
            } catch (InterruptedException ex) {
                log.warn("Interrupted {}", Thread.currentThread().getId());
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        //now we wait for all others threads to stop trying, and only the last thread is running
        while (!lastThreadTryingToLock.get()) {
            Thread.sleep(100);
        }
        //try to kill that last thread, and don't let it clean up after itself
        executor.shutdownNow();
        //force the lock to unlock just in case
        try {
            lock.forceUnlock();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (lock.isLocked()) {
            Assertions.fail("Lock should have been unlocked by now");
        }
        //check the timeout scores - they should all be within a reasonable amount of time from now
        List<Long> queue = redisson.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_ONLY,
                "local result = {}; " +
                        "local timeouts = redis.call('zrange', KEYS[1], 0, 99, 'WITHSCORES'); " +
                        "for i=1,#timeouts,2 do " +
                        "table.insert(result, timeouts[i+1]); " +
                        "end; " +
                        "return result; ",
                RScript.ReturnType.LIST,
                Collections.singletonList("redisson_lock_timeout:{test-fair-lock}"));

        for (int i = 0; i < queue.size(); i++) {
            long timeout = queue.get(i);
            long epiry = ((timeout - new Date().getTime()) / 1000);
            log.info("Item {} expires in {} seconds", i, epiry);
            // the Redisson library uses this 60000*5ms delay in the code
            Assertions.assertFalse(epiry > leaseTimeSeconds + 60*5 * (i + 1),
                                    "It would take more than " + (leaseTimeSeconds + 60*5 * (i + 1)) + "s to get the lock!");
        }
    }

    @Test
    public void testTryLockNonDelayed() throws InterruptedException {
        String LOCK_NAME = "SOME_LOCK";

        Thread t1 = new Thread(() -> {
            RLock fairLock = redisson.getFairLock(LOCK_NAME);
            try {
                if (fairLock.tryLock(0, TimeUnit.SECONDS)) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    Assertions.fail("Unable to acquire lock for some reason");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                fairLock.unlock();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            RLock fairLock = redisson.getFairLock(LOCK_NAME);
            try {
                if (fairLock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    Assertions.fail("Should not be inside second block");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                fairLock.unlock();
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        RLock fairLock = redisson.getFairLock(LOCK_NAME);
        try {
            if (!fairLock.tryLock(0, TimeUnit.SECONDS)) {
                Assertions.fail("Could not get unlocked lock " + LOCK_NAME);
            }
        } finally {
            fairLock.unlock();
        }
    }

    @Test
    public void testTryLockWait() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getFairLock("lock");
            lock.lock();
        });

        RLock lock = redisson.getFairLock("lock");

        long startTime = System.currentTimeMillis();
        lock.tryLock(3, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - startTime).isBetween(2990L, 3100L);
    }

    @Test
    public void testForceUnlock() {
        RLock lock = redisson.getFairLock("lock");
        lock.lock();
        lock.forceUnlock();
        Assertions.assertFalse(lock.isLocked());

        lock = redisson.getFairLock("lock");
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testExpire() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RLock lock1 = redisson.getFairLock("lock");
                lock1.lock();

                long spendTime = System.currentTimeMillis() - startTime;
                System.out.println(spendTime);
                Assertions.assertTrue(spendTime < 2020);
                lock1.unlock();
            };
        };

        t.start();
        t.join();

        lock.unlock();
    }

    @Test
    public void testAutoExpire() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getFairLock("lock");
            lock.lock();
            latch.countDown();
        });

        Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));
        RLock lock = redisson.getFairLock("lock");

        await().atMost(redisson.getConfig().getLockWatchdogTimeout() + 1000, TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }

    @Test
    public void testGetHoldCount() {
        RLock lock = redisson.getFairLock("lock");
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
        RLock lock = redisson.getFairLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getFairLock("lock");
        Assertions.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assertions.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assertions.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RLock lock = redisson.getFairLock("lock");
        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testUnlockFail() {
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> {
            RLock lock = redisson.getFairLock("lock");
            Thread t = new Thread() {
                public void run() {
                    RLock lock = redisson.getFairLock("lock");
                    lock.lock();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    lock.unlock();
                };
            };

            t.start();
            t.join(400);

            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                t.join();
                throw e;
            }
        });
    }


    @Test
    public void testLockUnlock() {
        Lock lock = redisson.getFairLock("lock1");
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    public void testReentrancy() throws InterruptedException {
        Lock lock = redisson.getFairLock("lock1");
        Assertions.assertTrue(lock.tryLock());
        Assertions.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getFairLock("lock1");
                Assertions.assertFalse(lock1.tryLock());
            }
        };
        thread1.start();
        thread1.join();
        lock.unlock();
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        int iterations = 15;
        testSingleInstanceConcurrency(iterations, r -> {
            Lock lock = r.getFairLock("testConcurrency_SingleInstance");
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

        testMultiInstanceConcurrency(16, r -> {
            for (int i = 0; i < iterations; i++) {
                r.getFairLock("testConcurrency_MultiInstance1").lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getFairLock("testConcurrency_MultiInstance1").unlock();
            }
        });

        Assertions.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, r -> {
            Lock lock = r.getFairLock("testConcurrency_MultiInstance2");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assertions.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance_Ordering() throws InterruptedException {
        final ConcurrentLinkedQueue<Thread> queue = new ConcurrentLinkedQueue<>();
        final AtomicInteger lockedCounter = new AtomicInteger();

        int totalThreads = Runtime.getRuntime().availableProcessors()*2;
        for (int i = 0; i < totalThreads; i++) {
            Thread t1 = new Thread(() -> {
                Lock lock = redisson.getFairLock("testConcurrency_MultiInstance2");
                queue.add(Thread.currentThread());
                lock.lock();
                Thread t = queue.poll();
                assertThat(t).isEqualTo(Thread.currentThread());
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                lock.unlock();
            });
            Thread.sleep(10);
            t1.start();
        }

        await().atMost(45, TimeUnit.SECONDS).until(() -> lockedCounter.get() == totalThreads);
    }

    @Test
    public void testNameMapper() {
        Config config = redisson.getConfig();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return "test::" + name;
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace("test::", "");
                    }
                });

        RedissonClient redisson = Redisson.create(config);

        RLock lock = redisson.getFairLock("lock");
        String threadsQueueName = Reflect.on(lock).get("threadsQueueName");
        Assertions.assertTrue(threadsQueueName.contains("test::lock"));

        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());

        redisson.shutdown();
    }

    @Test
    public void testAcquireFailedNotCalledOnRedisTimeout() throws Exception {
        Config config = createConfig();
        config.useSingleServer().setTimeout(1000)
                .setRetryAttempts(10);
        RedissonClient redisson = Redisson.create(config);

        String lockName = "testAcquireFailedNotCalled";

        CountDownLatch hLocked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            RLock l = redisson.getFairLock(lockName);
            l.lock(10, TimeUnit.SECONDS);
            hLocked.countDown();
        });

        CountDownLatch hLocked2 = new CountDownLatch(1);
        Thread holder2 = new Thread(() -> {
            RLock l = redisson.getFairLock(lockName);
            l.lock(10, TimeUnit.SECONDS);
            hLocked2.countDown();
        });

        AtomicReference<Exception> aException = new AtomicReference<>();
        CountDownLatch aDone = new CountDownLatch(1);
        Thread a = new Thread(() -> {
            RLock l = redisson.getFairLock(lockName);
            try {
                l.tryLock(60, 1, TimeUnit.SECONDS);
            } catch (Exception e) {
                aException.set(e);
            } finally {
                aDone.countDown();
            }
        });

        holder.start();
        hLocked.await();
        holder2.start();

        Thread.sleep(200);
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock(lockName);
        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        assertThat(queue.readAll()).isNotEmpty();

        a.start();
        Thread.sleep(100);
        assertThat(queue.readAll()).hasSize(2);

        RedisClientConfig cc = new RedisClientConfig();
        cc.setAddress(redisson.getConfig().useSingleServer().getAddress());
        RedisClient c = RedisClient.create(cc);
        RedisConnection ccc = c.connect();
        ccc.sync(new RedisStrictCommand<Void>("CLIENT", "PAUSE"), "30000", "WRITE");

        aDone.await(20, TimeUnit.SECONDS);

        ccc.sync(new RedisStrictCommand<Void>("CLIENT", "UNPAUSE"));
        ccc.close();

        Thread.sleep(1000);

        assertThat(queue.readAll()).isEmpty();
        Assertions.assertNull(aException.get());

        redisson.shutdown();
    }

    /**
     * The scenario of issue #4595. A thread is queued with an unbounded wait time. Its
     * Redisson instance then dies. No live owner renews its queue entry. Thus the entry
     * must be removed within lockWatchdogTimeout, not after its wait time.
     */
    @Test
    public void testDeadWaiterIsEvictedWithinWatchdogTimeout() throws Exception {
        long watchdogTimeout = 2000;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testDeadWaiterIsEvictedWithinWatchdogTimeout");
        lock.delete();

        long threadHolder = 101;
        long threadDead = 102;

        Long ttl = lock.tryLockInnerAsync(-1, 1000, TimeUnit.MILLISECONDS, threadHolder, RedisCommands.EVAL_LONG).toCompletableFuture().join();
        Assertions.assertNull(ttl);

        // queued with Long.MAX_VALUE. Then the process dies. No renewal runs for this thread.
        Long deadTtl = lock.tryLockInnerAsync(Long.MAX_VALUE, 1000, TimeUnit.MILLISECONDS, threadDead, RedisCommands.EVAL_LONG).toCompletableFuture().join();
        Assertions.assertNotNull(deadTtl);

        RScoredSortedSet<String> alive = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        Double deadline = alive.getScore(lock.getLockName(threadDead));
        assertThat(deadline).isNotNull();
        assertThat(deadline - System.currentTimeMillis()).isBetween(0.0, (double) watchdogTimeout);

        // The lease of the holder ends. The lock is free, and a dead thread is first in line.
        Thread.sleep(1200);
        assertThat(lock.isLocked()).isFalse();

        RLock live = redisson.getFairLock("testDeadWaiterIsEvictedWithinWatchdogTimeout");
        long start = System.currentTimeMillis();
        assertThat(live.tryLock(30, TimeUnit.SECONDS)).isTrue();
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(2 * watchdogTimeout);

        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        assertThat(queue.readAll()).isEmpty();
        assertThat(alive.readAll()).isEmpty();

        live.unlock();
        redisson.shutdown();
    }

    /**
     * A live thread that waits longer than lockWatchdogTimeout keeps its place. Its
     * deadline is renewed, so the eviction rule never applies to it.
     */
    @Test
    public void testLiveWaiterIsRenewedWhileQueued() throws Exception {
        long watchdogTimeout = 1000;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testLiveWaiterIsRenewedWhileQueued");

        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            // held until the forced unlock below, which comes after the assertions
            lock.lock(60, TimeUnit.SECONDS);
            locked.countDown();
        });
        holder.start();
        locked.await();

        AtomicBoolean acquired = new AtomicBoolean();
        AtomicLong waiterThreadId = new AtomicLong();
        Thread waiter = new Thread(() -> {
            waiterThreadId.set(Thread.currentThread().getId());
            try {
                acquired.set(lock.tryLock(60, TimeUnit.SECONDS));
                if (acquired.get()) {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();

        RScoredSortedSet<String> alive = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).hasSize(1));
        String waiterName = lock.getLockName(waiterThreadId.get());

        // After three watchdog periods, the thread is still queued and its deadline is ahead.
        Thread.sleep(3 * watchdogTimeout);
        assertThat(queue.readAll()).containsExactly(waiterName);
        assertThat(alive.getScore(waiterName) - System.currentTimeMillis()).isBetween(0.0, (double) watchdogTimeout);

        lock.forceUnlock();
        waiter.join(10000);
        assertThat(acquired).isTrue();
        assertThat(queue.readAll()).isEmpty();
        assertThat(alive.readAll()).isEmpty();
        redisson.shutdown();
    }

    /**
     * An interrupted waiter leaves the queue, like a waiter that timed out. It does not
     * block the threads behind it until its wait time passes.
     */
    @Test
    public void testInterruptedWaiterLeavesQueue() throws Exception {
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testInterruptedWaiterLeavesQueue");

        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock(30, TimeUnit.SECONDS);
            locked.countDown();
        });
        holder.start();
        locked.await();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                lock.tryLock(60, TimeUnit.SECONDS);
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        waiter.start();

        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).hasSize(1));
        // The waiter asks once more directly after it subscribes. Then it sleeps on its latch.
        // An interrupt during that last request causes a RedisException instead.
        Thread.sleep(300);

        waiter.interrupt();
        waiter.join(5000);
        assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).isEmpty());
        RScoredSortedSet<String> alive = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        assertThat(alive.readAll()).isEmpty();

        lock.forceUnlock();
    }

    /**
     * forceUnlock removes every dead thread from the queue, not only the dead threads at
     * its head. Then it tells the first live thread that the lock is free.
     */
    @Test
    public void testForceUnlockRemovesDeadWaiters() throws Exception {
        long watchdogTimeout = 3000;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testForceUnlockRemovesDeadWaiters");
        lock.delete();

        long threadHolder = 201;
        long threadDead1 = 202;
        long threadDead2 = 203;

        Assertions.assertNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, threadHolder, RedisCommands.EVAL_LONG).toCompletableFuture().join());
        Assertions.assertNotNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, threadDead1, RedisCommands.EVAL_LONG).toCompletableFuture().join());

        RLock live = redisson.getFairLock("testForceUnlockRemovesDeadWaiters");
        AtomicBoolean acquired = new AtomicBoolean();
        CountDownLatch release = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                acquired.set(live.tryLock(60, TimeUnit.SECONDS));
                if (acquired.get()) {
                    // Held until the test has examined the queue. Thus the unlock of this
                    // thread cannot be the action that removed the dead thread behind it.
                    release.await();
                    live.unlock();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();
        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).hasSize(2));

        // a dead thread behind the live thread. The head-of-queue rule cannot remove it.
        Assertions.assertNotNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, threadDead2, RedisCommands.EVAL_LONG).toCompletableFuture().join());
        assertThat(queue.readAll()).hasSize(3);

        Thread.sleep(watchdogTimeout + 500);
        assertThat(acquired).isFalse();

        long start = System.currentTimeMillis();
        assertThat(lock.forceUnlock()).isTrue();
        await().atMost(watchdogTimeout, TimeUnit.MILLISECONDS).untilTrue(acquired);
        // woken by the message, not by the next poll one watchdog period later
        assertThat(System.currentTimeMillis() - start).isLessThan(watchdogTimeout);
        assertThat(queue.readAll()).isEmpty();
        RScoredSortedSet<String> alive = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        assertThat(alive.readAll()).isEmpty();

        release.countDown();
        waiter.join(10000);
        redisson.shutdown();
    }

    /**
     * A queue shared by this version and a version without liveness tracking, as during a
     * rolling upgrade. A thread with a deadline is judged by the deadline alone. A thread
     * without a deadline is judged by its turn estimate. This is the rule of the older version.
     */
    @Test
    public void testWaitersWithoutDeadlineAreJudgedByTheirEstimate() {
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testWaitersWithoutDeadlineAreJudgedByTheirEstimate");
        lock.delete();

        String deadNew = lock.getLockName(401);
        String deadOld = lock.getLockName(402);
        String liveNew = lock.getLockName(403);
        String liveOld = lock.getLockName(404);
        long threadNext = 405;

        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        RScoredSortedSet<String> estimates = redisson.getScoredSortedSet(lock.timeoutSetName, StringCodec.INSTANCE);
        RScoredSortedSet<String> deadlines = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);

        long now = System.currentTimeMillis();
        queue.addAll(Arrays.asList(deadNew, deadOld, liveNew, liveOld));
        // a passed deadline makes a thread dead. The estimate does not matter.
        estimates.add(now + 60000, deadNew);
        deadlines.add(now - 1000, deadNew);
        // no deadline: the estimate decides, as in the older version
        estimates.add(now - 1000, deadOld);
        // a future deadline keeps a thread. The estimate does not matter.
        estimates.add(now - 1000, liveNew);
        deadlines.add(now + 60000, liveNew);
        estimates.add(now + 60000, liveOld);

        Long ttl = lock.tryLockInnerAsync(-1, 1000, TimeUnit.MILLISECONDS, threadNext, RedisCommands.EVAL_LONG).toCompletableFuture().join();
        Assertions.assertNotNull(ttl);

        String next = lock.getLockName(threadNext);
        assertThat(queue.readAll()).containsExactly(liveNew, liveOld, next);
        assertThat(estimates.readAll()).containsExactlyInAnyOrder(liveNew, liveOld, next);
        assertThat(deadlines.readAll()).containsExactlyInAnyOrder(liveNew, next);

        lock.delete();
    }

    /**
     * A version without liveness tracking dequeues a thread and does not remove its
     * deadline. The next acquisition and a forced unlock remove the deadlines that belong
     * to no queued thread. Thus a deadline cannot outlive its thread.
     */
    @Test
    public void testDeadlinesOfDequeuedWaitersArePruned() throws Exception {
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testDeadlinesOfDequeuedWaitersArePruned");
        lock.delete();

        RScoredSortedSet<String> deadlines = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);
        String orphan = lock.getLockName(501);
        deadlines.add(System.currentTimeMillis() + 60000, orphan);

        Long ttl = lock.tryLockInnerAsync(-1, 10000, TimeUnit.MILLISECONDS, 502, RedisCommands.EVAL_LONG).toCompletableFuture().join();
        Assertions.assertNull(ttl);
        assertThat(deadlines.readAll()).isEmpty();

        deadlines.add(System.currentTimeMillis() + 60000, orphan);
        assertThat(lock.forceUnlock()).isTrue();
        assertThat(deadlines.readAll()).isEmpty();
    }

    /**
     * A version without liveness tracking removes a thread when its turn estimate has
     * passed. Thus the renewal also keeps the estimate of a live thread ahead of the clock.
     */
    @Test
    public void testRenewalRaisesPassedEstimate() throws Exception {
        long watchdogTimeout = 1500;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testRenewalRaisesPassedEstimate");

        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock(30, TimeUnit.SECONDS);
            locked.countDown();
        });
        holder.start();
        locked.await();

        AtomicLong waiterThreadId = new AtomicLong();
        Thread waiter = new Thread(() -> {
            waiterThreadId.set(Thread.currentThread().getId());
            try {
                if (lock.tryLock(30, TimeUnit.SECONDS)) {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();

        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).hasSize(1));
        String waiterName = lock.getLockName(waiterThreadId.get());

        // This is what a thread of the older version does to the estimate when it leaves the queue.
        RScoredSortedSet<String> estimates = redisson.getScoredSortedSet(lock.timeoutSetName, StringCodec.INSTANCE);
        estimates.addScore(waiterName, -3_600_000);
        assertThat(estimates.getScore(waiterName)).isLessThan((double) System.currentTimeMillis());

        await().atMost(watchdogTimeout, TimeUnit.MILLISECONDS)
                .until(() -> estimates.getScore(waiterName) > System.currentTimeMillis());
        assertThat(queue.readAll()).containsExactly(waiterName);

        lock.forceUnlock();
        waiter.join(10000);
        redisson.shutdown();
    }

    @Test
    public void testRenewalSurvivesAnAbsentEntry() throws Exception {
        long watchdogTimeout = 600;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testRenewalSurvivesAnAbsentEntry");
        lock.delete();

        long threadId = 601;
        String name = lock.getLockName(threadId);
        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        RScoredSortedSet<String> estimates = redisson.getScoredSortedSet(lock.timeoutSetName, StringCodec.INSTANCE);
        RScoredSortedSet<String> deadlines = redisson.getScoredSortedSet(lock.aliveSetName, StringCodec.INSTANCE);

        // registered, but there is no entry to renew for several rounds
        lock.scheduleWaiterRenewal(threadId);
        Thread.sleep(2 * watchdogTimeout);

        // the thread queues itself again, with a deadline that passes soon
        long now = System.currentTimeMillis();
        queue.add(name);
        estimates.add(now + 60000, name);
        deadlines.add(now + 100, name);

        await().atMost(watchdogTimeout, TimeUnit.MILLISECONDS)
                .until(() -> deadlines.getScore(name) > System.currentTimeMillis() + watchdogTimeout / 2);

        lock.cancelWaiterRenewal(threadId);
        lock.delete();
        redisson.shutdown();
    }

    @Test
    public void testQueuedThreadSleepsAtMostOneLivenessPeriod() throws Exception {
        long watchdogTimeout = 1000;
        Config config = createConfig().setLockWatchdogTimeout(watchdogTimeout);
        RedissonClient redisson = Redisson.create(config);
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testQueuedThreadSleepsAtMostOneLivenessPeriod");
        lock.delete();

        Assertions.assertNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, 701, RedisCommands.EVAL_LONG).toCompletableFuture().join());
        Assertions.assertNotNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, 702, RedisCommands.EVAL_LONG).toCompletableFuture().join());
        Long hint = lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, 702, RedisCommands.EVAL_LONG).toCompletableFuture().join();
        Assertions.assertNotNull(hint);
        assertThat(hint).isBetween(1L, watchdogTimeout);

        lock.delete();
        redisson.shutdown();
    }

    @Test
    public void testTimedOutAsyncWaiterSubtractsItsOwnWait() throws Exception {
        RedissonFairLock lock = (RedissonFairLock) redisson.getFairLock("testTimedOutAsyncWaiterSubtractsItsOwnWait");
        lock.delete();

        Assertions.assertNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, 801, RedisCommands.EVAL_LONG).toCompletableFuture().join());

        long waitTime = 1000;
        RFuture<Boolean> timedOut = lock.tryLockAsync(waitTime, 60000, TimeUnit.MILLISECONDS, 802);
        RList<String> queue = redisson.getList(lock.threadsQueueName, StringCodec.INSTANCE);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).hasSize(1));

        String behind = lock.getLockName(803);
        Assertions.assertNotNull(lock.tryLockInnerAsync(-1, 60000, TimeUnit.MILLISECONDS, 803, RedisCommands.EVAL_LONG).toCompletableFuture().join());
        RScoredSortedSet<String> estimates = redisson.getScoredSortedSet(lock.timeoutSetName, StringCodec.INSTANCE);
        double before = estimates.getScore(behind);

        assertThat(timedOut.toCompletableFuture().get(5, TimeUnit.SECONDS)).isFalse();
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queue.readAll()).containsExactly(behind));
        double after = estimates.getScore(behind);
        assertThat(before - after).isBetween((double) waitTime - 100, (double) waitTime + 100);

        lock.delete();
    }

}
