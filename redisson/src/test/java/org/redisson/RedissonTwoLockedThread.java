package org.redisson;

import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.redisson.BaseTest.createInstance;

@RunWith(Parameterized.class)
public class RedissonTwoLockedThread {

    @Parameterized.Parameters(name= "{index} - {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {{new JsonJacksonCodec()}, {new SerializationCodec()}});
    }

    @Parameterized.Parameter(0)
    public Codec codec;

    private RedissonClient redisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            redisson = createInstance();
        }
        Config config = BaseTest.createConfig();
        config.setCodec(codec);
        redisson = Redisson.create(config);
    }

    @After
    public void after() throws InterruptedException {
        redisson.shutdown();
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @Test(timeout = 3000)
    public void testLock() throws InterruptedException {
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
                    Assert.assertTrue("current=" + current + ", millis=" + millis, current - millis >= 500);
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
    }

    @Test(timeout = 3000)
    public void testCountDown() throws InterruptedException {
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
                    Assert.assertTrue("current=" + current + ", millis=" + millis, (current - millis) >= 1000);
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
    }
}
