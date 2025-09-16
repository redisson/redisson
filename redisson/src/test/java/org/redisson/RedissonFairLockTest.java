package org.redisson;

import org.joor.Reflect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NameMapper;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
            executorService.submit(new Runnable() {
                @Override
                public void run() {
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
                RScript.ReturnType.MULTI,
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
                RScript.ReturnType.MULTI,
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
        Boolean unlocked = lock.unlockInnerAsync(threadInit).toCompletableFuture().join();;
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
    }

    @Test
    public void testLockAcquiredBooleanTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 500;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        Config config = createConfig()
                .setFairLockWaitTimeout(100);

        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = new RedissonFairLock(
                ((Redisson) redisson).getCommandExecutor(),
                "testLockAcquiredTimeoutDrift_Descrete");

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
        Boolean unlocked = lock.unlockInnerAsync(threadInit).toCompletableFuture().join();;
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
    }

    @Test
    public void testLockAcquiredTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 300_000;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        RedissonClient redisson = Redisson.create(createConfig());

        RedissonFairLock lock = new RedissonFairLock(
                ((Redisson) redisson).getCommandExecutor(),
                "testLockAcquiredTimeoutDrift_Descrete");

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
        Boolean unlocked = lock.unlockInnerAsync(threadInit).toCompletableFuture().join();;
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
        Assertions.assertTrue(thirdTTL < secondTTLAgain);
    }

    @Test
    public void testAbandonedTimeoutDrift_Descrete() throws Exception {
        long leaseTime = 500;
        long threadWaitTime = 100;

        // we're testing interaction of various internal methods, so create a Redisson instance for protected access
        Config config = createConfig()
                .setFairLockWaitTimeout(threadWaitTime);
        RedissonClient redisson = Redisson.create(config);

        RedissonFairLock lock = new RedissonFairLock(
                ((Redisson) redisson).getCommandExecutor(),
                "testAbandonedTimeoutDrift_Descrete");

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

        Thread.sleep(thirdTTL + threadWaitTime);

        ttl = lock.tryLockInnerAsync(-1, leaseTime, TimeUnit.MILLISECONDS, threadThirdWaiter, RedisCommands.EVAL_LONG).toCompletableFuture().join();;
        Assertions.assertNull(ttl);
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
                RScript.ReturnType.MULTI,
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
    }


}
