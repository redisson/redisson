package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class RedissonTwoLockedThread extends BaseTest {

    public static Iterable<Codec> data() {
        return Arrays.asList(new JsonJacksonCodec(), new SerializationCodec());
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testLock(Codec codec) throws InterruptedException {
        Config config = BaseTest.createConfig();
        config.setCodec(codec);
        RedissonClient redisson = Redisson.create(config);
        
        Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
            final String lockName = "lock1";
    
            final CountDownLatch startSignal = new CountDownLatch(1);
            final CountDownLatch testSignal = new CountDownLatch(1);
            final CountDownLatch completeSignal = new CountDownLatch(2);
    
            System.out.println("configure");
    
            final long millis = System.currentTimeMillis();
    
            new Thread() {
                @Override
                public void run() {
                    try {
                        startSignal.await();
                        RLock lock = redisson.getLock(lockName);
                        System.out.println("1. getlock " + lock.getName() + " - " + Thread.currentThread().getId());
                        lock.lock();
                        System.out.println("1. lock " + lock.getName() + " - " + Thread.currentThread().getId());
                        testSignal.countDown();
                        Thread.sleep(500);
                        lock.unlock();
                        System.out.println("1. unlock " + lock.getName() + " - " + Thread.currentThread().getId());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    completeSignal.countDown();
                }
            }.start();
    
            new Thread() {
                @Override
                public void run() {
                    try {
                        testSignal.await();
                        RLock lock = redisson.getLock(lockName);
                        System.out.println("2. getlock " + lock.getName() + " - " + Thread.currentThread().getId());
                        lock.lock();
                        System.out.println("2. lock " + lock.getName() + " - " + Thread.currentThread().getId());
                        long current = System.currentTimeMillis();
                        Assertions.assertTrue(current - millis >= 500, "current=" + current + ", millis=" + millis);
                        Thread.sleep(500);
                        lock.unlock();
                        System.out.println("2. unlock " + lock.getName() + " - " + Thread.currentThread().getId());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    completeSignal.countDown();
                }
            }.start();
    
            System.out.println("start");
            startSignal.countDown();
            completeSignal.await();
            System.out.println("complete");
        });

        redisson.shutdown();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCountDown(Codec codec) throws InterruptedException {
        Config config = BaseTest.createConfig();
        config.setCodec(codec);
        RedissonClient redisson = Redisson.create(config);

        Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
            final String countDownName = getClass().getName() + ":countDown#1";

            final CountDownLatch startSignal = new CountDownLatch(1);
            final CountDownLatch testSignal = new CountDownLatch(1);
            final CountDownLatch completeSignal = new CountDownLatch(2);

            System.out.println("configure");

            final long millis = System.currentTimeMillis();

            new Thread() {
                @Override
                public void run() {
                    try {
                        startSignal.await();
                        RCountDownLatch countDownLatch = redisson.getCountDownLatch(countDownName);
                        System.out.println("1. getCountDownLatch " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                        countDownLatch.trySetCount(1);
                        System.out.println("1. trySetCount " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                        Thread.sleep(500);
                        testSignal.countDown();
                        Thread.sleep(500);
                        System.out.println("1. sleep " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                        countDownLatch.countDown();
                        System.out.println("1. countDown " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    completeSignal.countDown();
                }
            }.start();

            new Thread() {
                @Override
                public void run() {
                    try {
                        testSignal.await();
                        RCountDownLatch countDownLatch = redisson.getCountDownLatch(countDownName);
                        System.out.println("2. getCountDownLatch " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                        countDownLatch.await();
                        System.out.println("2. await " + countDownLatch.getName() + " - " + Thread.currentThread().getId());
                        long current = System.currentTimeMillis();
                        Assertions.assertTrue((current - millis) >= 1000, "current=" + current + ", millis=" + millis);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    completeSignal.countDown();
                }
            }.start();

            System.out.println("start");
            startSignal.countDown();
            completeSignal.await();
            System.out.println("complete");
        });
    }
}
