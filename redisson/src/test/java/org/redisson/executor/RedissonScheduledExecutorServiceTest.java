package org.redisson.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.RedissonNode;
import org.redisson.api.CronSchedule;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RScheduledFuture;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;

public class RedissonScheduledExecutorServiceTest extends BaseTest {

    private static RedissonNode node;
    
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

    @Test(timeout = 7000)
    public void testTaskResume() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<Long> future1 = executor.schedule(new ScheduledCallableTask(), 5, TimeUnit.SECONDS);
        ScheduledFuture<Long> future2 = executor.schedule(new ScheduledCallableTask(), 5, TimeUnit.SECONDS);
        ScheduledFuture<Long> future3 = executor.schedule(new ScheduledCallableTask(), 5, TimeUnit.SECONDS);
        
        node.shutdown();
        
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(redisson.getConfig());
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 1));
        node = RedissonNode.create(nodeConfig);
        node.start();

        assertThat(future1.get()).isEqualTo(100);
        assertThat(future2.get()).isEqualTo(100);
        assertThat(future3.get()).isEqualTo(100);
    }
    
    @Test
    public void testLoad() {
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test2", Runtime.getRuntime().availableProcessors()*2));
        RedissonNode node = RedissonNode.create(nodeConfig);
        node.start();

        List<RScheduledFuture<?>> futures = new ArrayList<>();
        for(int i = 0; i<10000; i++){
            RScheduledFuture<?> future = redisson.getExecutorService("test2").scheduleAsync(new RunnableTask(), 5, TimeUnit.SECONDS);
            futures.add(future);
        }
        
        for (RScheduledFuture<?> future : futures) {
            assertThat(future.awaitUninterruptibly(5000)).isTrue();
        }
        
        node.shutdown();
    }
    
    @Test
    public void testCronExpression() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0/2 * * * * ?”"));
        Thread.sleep(4200);
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(2);
    }
    
    @Test
    public void testCronExpressionMultipleTasks() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.schedule(new ScheduledRunnableTask("executed1"), CronSchedule.of("0/5 * * * * ?"));
        executor.schedule(new ScheduledRunnableTask("executed2"), CronSchedule.of("0/1 * * * * ?"));
        Thread.sleep(30000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(6);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(30);
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
        assertThat(redisson.<Long>getBucket("executed1").get()).isBetween(1000L, Long.MAX_VALUE);
        
        RScheduledFuture<?> futureAsync = executor.scheduleAsync(new ScheduledLongRunnableTask("executed2"), 1, TimeUnit.SECONDS);
        Thread.sleep(2000);
        assertThat(executor.cancelTask(futureAsync.getTaskId())).isTrue();
        assertThat(redisson.<Long>getBucket("executed2").get()).isBetween(1000L, Long.MAX_VALUE);
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
        assertThat(executor.cancelTask(futureAsync.getTaskId())).isTrue();
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
        assertThat(redisson.<Long>getBucket("executed1").get()).isBetween(1000L, Long.MAX_VALUE);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        redisson.getAtomicLong("counter").delete();
        
        RScheduledFuture<?> future2 = executor.scheduleWithFixedDelay(new ScheduledLongRepeatableTask("counter", "executed2"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(6000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        
        executor.cancelTask(future2.getTaskId());
        assertThat(redisson.<Long>getBucket("executed2").get()).isBetween(1000L, Long.MAX_VALUE);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
    }

    private void cancel(ScheduledFuture<?> future1) throws InterruptedException, ExecutionException {
        assertThat(future1.cancel(true)).isTrue();
        try {
            future1.get();
            Assert.fail("CancellationException should be arise");
        } catch (CancellationException e) {
            // skip
        }
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
        assertThat(System.currentTimeMillis() - startTime).isBetween(4900L, 5300L);
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
