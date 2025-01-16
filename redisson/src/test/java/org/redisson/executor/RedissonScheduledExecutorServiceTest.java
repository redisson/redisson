package org.redisson.executor;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.joor.Reflect;
import org.junit.jupiter.api.*;
import org.redisson.*;
import org.redisson.api.*;
import org.redisson.api.annotation.RInject;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonScheduledExecutorServiceTest extends RedisDockerTest {

    private static RedissonNode node;
    
    @BeforeEach
    public void before() throws IOException, InterruptedException {
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 5));
        node = RedissonNode.create(nodeConfig);
        node.start();
    }

    @AfterEach
    public void after() {
        node.shutdown();
    }
    
    public static class TestTask implements Runnable, Serializable {
        
        @RInject
        RedissonClient redisson;
        
        @Override
        public void run() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            redisson.getAtomicLong("counter").incrementAndGet();
        }

    }

    public static class TestTask2 implements Runnable, Serializable {

        @RInject
        RedissonClient redisson;

        @Override
        public void run() {
            RList<Object> list = redisson.getList("timelist");
            list.add(System.currentTimeMillis());

            RAtomicLong counter = redisson.getAtomicLong("counter");
            try {
                if (counter.get() == 0) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            counter.incrementAndGet();
        }

    }

    @Test
    public void testTasksExecution() throws InterruptedException {
        RScheduledExecutorService executorService = redisson.getExecutorService("test");

        Map<String, RScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            RScheduledFuture<?> s = executorService.schedule(new IncrementRunnableTask(),
                                        ThreadLocalRandom.current().nextInt(500), TimeUnit.MILLISECONDS);
            futureMap.put(s.getTaskId(), s);
            s.whenComplete((r, e) -> {
                futureMap.remove(s.getTaskId());
            });
        }

        Thread.sleep(2000);
        assertThat(futureMap).hasSize(0);
    }

    @Test
    public void testScheduleAtFixedRate() throws InterruptedException {
        RScheduledExecutorService executorService = redisson.getExecutorService("test");
        executorService.scheduleAtFixedRate(new TestTask2(), 1000L, 200L, TimeUnit.MILLISECONDS);

        Thread.sleep(4000);

        RList<Long> list = redisson.getList("timelist");

        long start = list.get(1);
        list.stream().skip(1).limit(5).reduce(start, (r, e) -> {
            assertThat(e - r).isLessThan(20L);
            return e;
        });

        long start2 = list.get(5);
        list.stream().skip(6).limit(15).reduce(start2, (r, e) -> {
            assertThat(e - r).isBetween(160L, 380L);
            return e;
        });
    }

    @Test
    public void testTTL() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.submit(new DelayedTask(3000, "test"));
        Future<?> future = executor.schedule(new ScheduledRunnableTask("testparam"), 1, TimeUnit.SECONDS,2, TimeUnit.SECONDS);
        Thread.sleep(500);
        assertThat(executor.getTaskCount()).isEqualTo(2);
        Thread.sleep(3000);
        assertThat(executor.getTaskCount()).isEqualTo(0);
        assertThat(redisson.getAtomicLong("testparam").get()).isEqualTo(1);
    }

    
    @Test
    public void testSingleWorker() throws InterruptedException {
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.getExecutorServiceWorkers().put("JobA", 1);
        RedissonNode node = RedissonNode.create(nodeConfig);
        node.start();
        
        RedissonClient client = Redisson.create(config);
        RScheduledExecutorService executorService = client.getExecutorService("JobA");
        executorService.schedule(new TestTask() , CronSchedule.of("0/1 * * * * ?"));
        
        TimeUnit.MILLISECONDS.sleep(5000);
        
        assertThat(client.getAtomicLong("counter").get()).isEqualTo(4);
        
        client.shutdown();
        node.shutdown();
    }

    @Test
    public void testTaskCount() throws InterruptedException {
        RScheduledExecutorService e = redisson.getExecutorService("test");
        e.schedule(new RunnableTask(), 1, TimeUnit.SECONDS);
        e.schedule(new RunnableTask(), 2, TimeUnit.SECONDS);
        assertThat(e.getTaskCount()).isEqualTo(2);

        Thread.sleep(1100);
        assertThat(e.getTaskCount()).isEqualTo(1);

        Thread.sleep(1100);
        assertThat(e.getTaskCount()).isEqualTo(0);
    }


    @Test
    @Disabled("Doesn't work with JDK 11+")
    public void testDelay() throws ExecutionException, InterruptedException, TimeoutException {
        RScheduledExecutorService executor = redisson.getExecutorService("test", ExecutorOptions.defaults().taskRetryInterval(5, TimeUnit.SECONDS));
        long start = System.currentTimeMillis();
        RScheduledFuture<?> f = executor.schedule(new ScheduledCallableTask(), 11, TimeUnit.SECONDS);
        f.toCompletableFuture().get(12, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - start).isBetween(11000L, 11500L);
        
        Reflect.onClass(RedissonExecutorService.class).set("RESULT_OPTIONS", RemoteInvocationOptions.defaults().noAck().expectResultWithin(3, TimeUnit.SECONDS));
    
        executor = redisson.getExecutorService("test", ExecutorOptions.defaults().taskRetryInterval(5, TimeUnit.SECONDS));
        start = System.currentTimeMillis();
        RScheduledFuture<?> f1 = executor.schedule(new ScheduledCallableTask(), 5, TimeUnit.SECONDS);
        f1.toCompletableFuture().get(6, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - start).isBetween(5000L, 5500L);

        start = System.currentTimeMillis();
        RScheduledFuture<?> f2 = executor.schedule(new RunnableTask(), 5, TimeUnit.SECONDS);
        f2.toCompletableFuture().get(6, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - start).isBetween(5000L, 5500L);
    }
    
    @Test
    public void testScheduleWithFixedDelay() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test", ExecutorOptions.defaults().taskRetryInterval(5, TimeUnit.SECONDS));
        executor.scheduleWithFixedDelay(new IncrementRunnableTask("counter"), 0, 7, TimeUnit.SECONDS);
        Thread.sleep(500);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);
        Thread.sleep(7000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);
        Thread.sleep(7000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
    }
    
    @Test
    public void testTaskFailover() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        // don't allow to mark task as completed
        new MockUp<TasksRunnerService>() {
            @Mock
            void finish(Invocation invocation, String requestId, boolean removeTask) {
                if (counter.incrementAndGet() > 1) {
                    invocation.proceed();
                }
            }
        };
        
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test2", 1));
        node.shutdown();
        node = RedissonNode.create(nodeConfig);
        node.start();
        
        RScheduledExecutorService executor = redisson.getExecutorService("test2",
                                                        ExecutorOptions.defaults().taskRetryInterval(10, TimeUnit.SECONDS));
        long start = System.currentTimeMillis();
        RExecutorFuture<?> f = executor.schedule(new IncrementRunnableTask("counter"), 1, TimeUnit.SECONDS);
        f.toCompletableFuture().join();
        assertThat(System.currentTimeMillis() - start).isBetween(900L, 1350L);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);
        Thread.sleep(2000);
        node.shutdown();

        node = RedissonNode.create(nodeConfig);
        node.start();
        
        Thread.sleep(8500);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);

        Thread.sleep(16000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);
        
        redisson.getKeys().delete("counter");
        assertThat(redisson.getKeys().count()).isEqualTo(1);
    }

    @Test
    @Timeout(7)
    public void testTaskResume() throws ExecutionException, InterruptedException {
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
    public void testSetTaskId() {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RExecutorFuture<?> future = executor.schedule("1234", new ScheduledRunnableTask("executed1"), Duration.ofSeconds(10));
        assertThat(future.getTaskId()).isEqualTo("1234");
        future.cancel(true);
    }

    @Test
    public void testLoad() throws InterruptedException {
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
            try {
                future.toCompletableFuture().get(5500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Assertions.fail(e);
            } catch (ExecutionException e) {
                // skip
            }
        }
        
        node.shutdown();
    }
    
//    @Test
    public void testCronExpression2() throws InterruptedException {
        ExecutorOptions e = ExecutorOptions.defaults().taskRetryInterval(2, TimeUnit.SECONDS);
        RScheduledExecutorService executor = redisson.getExecutorService("test", e);
        LocalDateTime n = LocalDateTime.now();
        int s = n.getSecond() % 10;
        Thread.sleep((10 - s)*1000);
        executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0/10 * * ? * *"));

        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 2));
        node.shutdown();
        node = RedissonNode.create(nodeConfig);

        Thread.sleep(18000);

        node.start();
        Thread.sleep(500);

        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(1);

        Thread.sleep(1100);

        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(1);

        Thread.sleep(1100);

        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(2);

        Thread.sleep(5000);

        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(2);
    }

    @Test
    public void testCronExpression() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0/2 * * * * ?"));
        Thread.sleep(4000);
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(2);
    }

    @Test
    public void testHasTask() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.schedule(new ScheduledRunnableTask("executed"), 1, TimeUnit.SECONDS);
        assertThat(executor.hasTask(future.getTaskId())).isTrue();
        Thread.sleep(1100);
        assertThat(executor.hasTask(future.getTaskId())).isFalse();
    }

    @Test
    public void testTaskIds() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.schedule(new ScheduledRunnableTask("executed"), 1, TimeUnit.SECONDS);
        assertThat(executor.getTaskIds().contains(future.getTaskId())).isTrue();
        Thread.sleep(1200);
        assertThat(executor.getTaskIds().isEmpty()).isTrue();
        executor.delete();
    }

    @Test
    public void testWrongCronExpression() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RScheduledExecutorService executor = redisson.getExecutorService("test");
            executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0 44 12 19 JUN ? 2018 32"));
        });
    }
    
    @Test
    public void testCronExpressionMultipleTasks() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test", ExecutorOptions.defaults().taskRetryInterval(2, TimeUnit.SECONDS));
        executor.schedule(new ScheduledRunnableTask("executed1"), CronSchedule.of("0/5 * * * * ?"));
        executor.schedule(new ScheduledRunnableTask("executed2"), CronSchedule.of("0/1 * * * * ?"));
        Thread.sleep(30500);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(6);
        assertThat(redisson.getAtomicLong("executed2").get()).isEqualTo(30);
    }

    @Test
    public void testCancelCronExpression() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.schedule(new ScheduledRunnableTask("executed"), CronSchedule.of("0/2 * * * * ?"));
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(5);

        cancel(future);

        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(5);

        executor.delete();
        redisson.getKeys().delete("executed");
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testCancelAndInterruptCronExpression() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.schedule(new ScheduledLongRepeatableTask("counter", "executed"), CronSchedule.of("0/2 * * * * ?"));
        Thread.sleep(TimeUnit.SECONDS.toMillis(6));
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);

        cancel(future);
        Thread.sleep(50);
        assertThat(redisson.<Long>getBucket("executed").get()).isGreaterThan(1000L);

        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);

        executor.delete();
        redisson.getKeys().delete("counter", "executed");
        assertThat(redisson.getKeys().count()).isZero();
    }

    public static class RunnableTask2 implements Runnable, Serializable {

        @Override
        public void run() {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        }

    }

    @Test
    @Timeout(15)
    public void testCancel2() throws InterruptedException {
        RScheduledExecutorService e = redisson.getExecutorService("myExecutor");
        e.registerWorkers(WorkerOptions.defaults());
        String taskId = redisson.getExecutorService("myExecutor").schedule(new RunnableTask2(), 2000, TimeUnit.MILLISECONDS).getTaskId();
        Thread.sleep(5500);

        assertThat(e.cancelTask(taskId)).isFalse();
    }

    @Test
    public void testCancel() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 1, TimeUnit.SECONDS);
        cancel(future1);
        Thread.sleep(2000);
        assertThat(redisson.getAtomicLong("executed1").isExists()).isFalse();
        
        executor.delete();
        redisson.getKeys().delete("executed1");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testShutdownWithCancelAndOfflineExecutor() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test2");
        ScheduledFuture<?> future1 = executor.schedule(new ScheduledRunnableTask("executed1"), 1, TimeUnit.SECONDS);
        cancel(future1);
        Thread.sleep(2000);
        assertThat(redisson.getAtomicLong("executed1").isExists()).isFalse();
        executor.delete();
        
        redisson.getKeys().delete("executed1");
        assertThat(redisson.getKeys().count()).isZero();
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
        
        executor.delete();
        redisson.getKeys().delete("executed1", "executed2");
        assertThat(redisson.getKeys().count()).isZero();
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
        
        executor.delete();
        redisson.getKeys().delete("executed1", "executed2");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testCancelAndInterruptWithFixedDelay() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future1 = executor.scheduleWithFixedDelay(new ScheduledLongRepeatableTask("counter", "executed1"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(6000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        
        cancel(future1);
        Thread.sleep(50);
        assertThat(redisson.<Long>getBucket("executed1").get()).isGreaterThan(1000L);

        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        redisson.getAtomicLong("counter").delete();
        
        RScheduledFuture<?> future2 = executor.scheduleWithFixedDelay(new ScheduledLongRepeatableTask("counter", "executed2"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(6000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);

        assertThat(executor.cancelTask(future2.getTaskId())).isTrue();
        assertThat(redisson.<Long>getBucket("executed2").get()).isGreaterThan(1000L);


        Thread.sleep(3000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(3);
        
        executor.delete();
        redisson.getKeys().delete("counter", "executed1", "executed2");
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testCancelAndInterruptSwallowedWithFixedDelay() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.scheduleWithFixedDelay(new SwallowingInterruptionTask("execution1", "cancel1"), 0, 1, TimeUnit.SECONDS);

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));

        assertThat(redisson.getAtomicLong("cancel1").get()).isZero();
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        cancel(future);

        assertThat(redisson.getAtomicLong("cancel1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        Thread.sleep(TimeUnit.SECONDS.toMillis(6));

        assertThat(executor.getTaskCount()).isZero();
        assertThat(redisson.getAtomicLong("cancel1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        executor.delete();
        redisson.getKeys().delete("execution1", "cancel1");
        assertThat(redisson.getKeys().count()).isZero();
    }

    private void cancel(ScheduledFuture<?> future1) throws InterruptedException, ExecutionException {
        assertThat(future1.cancel(true)).isTrue();
        try {
            future1.get();
            Assertions.fail("CancellationException should arise");
        } catch (CancellationException e) {
            // skip
        }
    }
    
    public static class ScheduledRunnableTask2 implements Runnable, Serializable {
        private static final long serialVersionUID = -3523561767248576192L;
        private String key;

        @RInject
        private RedissonClient redisson;

        public ScheduledRunnableTask2(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            System.out.println("job is running");
            try {
                redisson.getAtomicLong(key).incrementAndGet();
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("job is over");
        }
    }

    @Test
    public void testCancelAtFixedDelay2() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test", ExecutorOptions.defaults().taskRetryInterval(30, TimeUnit.MINUTES));
        executor.registerWorkers(WorkerOptions.defaults().workers(5));
        RScheduledFuture<?> future1 = executor.scheduleWithFixedDelay(new ScheduledRunnableTask2("executed1"), 1, 2, TimeUnit.SECONDS);
        Thread.sleep(5000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(1);
        assertThat(executor.cancelTask(future1.getTaskId())).isTrue();
        Thread.sleep(30000);
        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(1);
    }

    @Test
    public void testIdCheck() {
        RScheduledExecutorService executor = redisson.getExecutorService("test");

        executor.schedule("1", new RunnableTask(), Duration.ofSeconds(12));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            executor.submit("1", new RunnableTask(), Duration.ofSeconds(12));
        });

        executor.schedule("2", new CallableTask(), Duration.ofSeconds(12));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            executor.submit("2", new CallableTask(), Duration.ofSeconds(12));
        });

        executor.scheduleWithFixedDelay("3", new RunnableTask(), Duration.ofSeconds(1), Duration.ofSeconds(10));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            executor.scheduleWithFixedDelay("3", new RunnableTask(), Duration.ofSeconds(1), Duration.ofSeconds(10));
        });

        executor.scheduleAtFixedRate("4", new RunnableTask(), Duration.ofSeconds(1), Duration.ofSeconds(10));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            executor.scheduleAtFixedRate("4", new RunnableTask(), Duration.ofSeconds(1), Duration.ofSeconds(10));
        });
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
        
        executor.delete();
        redisson.getKeys().delete("executed1");
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testCancelAndInterruptSwallowedAtFixedRate() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        RScheduledFuture<?> future = executor.scheduleAtFixedRate(new SwallowingInterruptionTask("execution1", "cancel1"), 0, 6, TimeUnit.SECONDS);

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));

        assertThat(redisson.getAtomicLong("cancel1").get()).isZero();
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        cancel(future);

        assertThat(redisson.getAtomicLong("cancel1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        Thread.sleep(TimeUnit.SECONDS.toMillis(6));

        assertThat(executor.getTaskCount()).isZero();
        assertThat(redisson.getAtomicLong("cancel1").get()).isEqualTo(1);
        assertThat(redisson.getAtomicLong("execution1").get()).isEqualTo(1);

        executor.delete();
        redisson.getKeys().delete("execution1", "cancel1");
        assertThat(redisson.getKeys().count()).isZero();
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
        
        executor.delete();
        redisson.getKeys().delete("executed1", "executed2", "executed3");
        assertThat(redisson.getKeys().count()).isZero();
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
        
        executor.delete();
        redisson.getKeys().delete("executed1", "executed2", "executed3");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testRunnableTask() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        ScheduledFuture<?> future = executor.schedule(new ScheduledRunnableTask("executed"), 5, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        future.get();
        assertThat(System.currentTimeMillis() - startTime).isBetween(5000L, 5200L);
        assertThat(redisson.getAtomicLong("executed").get()).isEqualTo(1);
        
        executor.delete();
        redisson.getKeys().delete("executed");
        assertThat(redisson.getKeys().count()).isZero();
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
