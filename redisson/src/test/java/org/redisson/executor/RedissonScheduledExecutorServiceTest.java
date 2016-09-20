package org.redisson.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.CronSchedule;
import org.redisson.RedissonNode;
import org.redisson.RedissonRuntimeEnvironment;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RScheduledFuture;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;

public class RedissonScheduledExecutorServiceTest extends BaseTest {

    private static RedissonNode node;
    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            BaseTest.beforeClass();
            Config config = createConfig();
            RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
            nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 1));
            node = RedissonNode.create(nodeConfig);
            node.start();
        }
    }
    
    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            BaseTest.afterClass();
            node.shutdown();
        }
    }

    @Before
    @Override
    public void before() throws IOException, InterruptedException {
        super.before();
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 1));
        node = RedissonNode.create(nodeConfig);
        node.start();
    }

    @After
    @Override
    public void after() throws InterruptedException {
        super.after();
        node.shutdown();
    }

    @Test
    public void testCronExpression() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0/2 * * * * ?”"));
        Thread.sleep(4000);
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(2);
    }
    
    @Test
    public void testCancel() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 1, TimeUnit.SECONDS);
        cancel(future1);
        Thread.sleep(2000);
        assertThat(redisson.getAtomicLong("executed1").isExists()).isFalse();
    }
    
    @Test
    public void testShutdownWithCancelAndOfflineExecutor() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test2");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 1, TimeUnit.SECONDS);
        cancel(future1);
        Thread.sleep(2000);
        assertThat(redisson.getAtomicLong("executed1").isExists()).isFalse();
        assertThat(executor.delete()).isFalse();
    }
    
    @Test
    public void testCancelAndInterrupt() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future = executor.schedule(new ScheduledLongRunnableTask("executed1"), 1, TimeUnit.SECONDS);
        Thread.sleep(2000);
        cancel(future);
        assertThat(redisson.<Integer>getBucket("executed1").get()).isBetween(1000, Integer.MAX_VALUE);
        
        RScheduledFuture<?> futureAsync = executor.scheduleAsync(new ScheduledLongRunnableTask("executed2"), 1, TimeUnit.SECONDS);
        Thread.sleep(2000);
        assertThat(executor.cancelScheduledTask(futureAsync.getTaskId())).isTrue();
        assertThat(redisson.<Integer>getBucket("executed2").get()).isBetween(1000, Integer.MAX_VALUE);
    }
    
    @Test
    public void testCancelWithFixedDelay() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.scheduleWithFixedDelay(new ScheduledRunnableTask("executed1"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(10000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(5);
        
        cancel(future1);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(5);
        
        RScheduledFuture<?> futureAsync = executor.scheduleWithFixedDelayAsync(new ScheduledRunnableTask("executed2"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(4000);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(2);
        assertThat(executor.cancelScheduledTask(futureAsync.getTaskId())).isTrue();
        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(2);
    }
    
    @Test
    public void testCancelAndInterruptWithFixedDelay() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.scheduleWithFixedDelay(new ScheduledLongRepeatableTask("counter", "executed1"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(6000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        
        cancel(future1);
        assertThat(redisson.<Integer>getBucket("executed1").get()).isBetween(1000, Integer.MAX_VALUE);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
    }

    private void cancel(ScheduledFuture<?> future1) throws InterruptedException, ExecutionException {
        assertThat(future1.cancel(true)).isTrue();
        boolean canceled = false;
        try {
            future1.get();
        } catch (CancellationException e) {
            canceled = true;
        }
        assertThat(canceled).isTrue();
    }


    @Test
    public void testCancelAtFixedRate() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.scheduleAtFixedRate(new ScheduledRunnableTask("executed1"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(10000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(5);
        
        cancel(future1);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(5);
    }


    @Test
    public void testMultipleTasksWithTimeShift() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 2, TimeUnit.SECONDS);
        ScheduledFuture<?> future2 = executor.schedule(new ScheduledRunnableTask("executed2"), 3, TimeUnit.SECONDS);
        ScheduledFuture<?> future3 = executor.schedule(new ScheduledRunnableTask("executed3"), 4, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        future1.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(2000L, 2200L);
        future2.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(3000L, 3200L);
        future3.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(4000L, 4200L);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("executed3").get()).isEqualTo(1);
    }
    
    @Test
    public void testMultipleTasks() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 5, TimeUnit.SECONDS);
        ScheduledFuture<?> future2 = executor.schedule(new ScheduledRunnableTask("executed2"), 5, TimeUnit.SECONDS);
        ScheduledFuture<?> future3 = executor.schedule(new ScheduledRunnableTask("executed3"), 5, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        future1.get();
        future2.get();
        future3.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(5000L, 5200L);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("executed3").get()).isEqualTo(1);
    }
    
    @Test
    public void testRunnableTask() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future = executor.schedule(new ScheduledRunnableTask("executed"), 5, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        future.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(5000L, 5200L);
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(1);
    }

    @Test
    public void testCallableTask() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<Long> future = executor.schedule(new ScheduledCallableTask(), 3, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        future.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(3000L, 3200L);
        assertThat(future.get()).isEqualTo(100);
    }
    
}
