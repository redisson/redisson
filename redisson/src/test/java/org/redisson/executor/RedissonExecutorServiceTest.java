package org.redisson.executor;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.awaitility.Awaitility;
import org.joor.Reflect;
import org.junit.jupiter.api.*;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.RedissonNode;
import org.redisson.RedissonTopic;
import org.redisson.api.*;
import org.redisson.api.annotation.RInject;
import org.redisson.api.executor.TaskFinishedListener;
import org.redisson.api.executor.TaskStartedListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.redisson.config.RedissonNodeConfig;

import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(value = 8, unit = TimeUnit.MINUTES)
public class RedissonExecutorServiceTest extends RedisDockerTest {

    private static RedissonNode node;
    
    @BeforeEach
    public void before() {
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 1));
        node = RedissonNode.create(nodeConfig);
        node.start();
    }

    @AfterEach
    public void after() {
        node.shutdown();
    }

    private void cancel(RExecutorFuture<?> future) throws InterruptedException, ExecutionException {
        assertThat(future.cancel(true)).isTrue();
        boolean canceled = false;
        try {
            future.get();
        } catch (CancellationException e) {
            canceled = true;
        }
        assertThat(canceled).isTrue();
    }

    @Test
    public void testTaskCount() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService("test");
        assertThat(e.getTaskCount()).isEqualTo(0);

        e.submit(new DelayedTask(1000, "testcounter"));
        e.submit(new DelayedTask(1000, "testcounter"));
        assertThat(e.getTaskCount()).isEqualTo(2);
        for (int i = 0; i < 20; i++) {
            e.submit(new RunnableTask());
        }
        assertThat(e.getTaskCount()).isBetween(5, 22);

        Thread.sleep(1500);

        assertThat(e.getTaskCount()).isZero();
    }

    @Test
    public void testBatchSubmitRunnable() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService("test");
        RExecutorBatchFuture future = e.submit(new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"), 
                    new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"));
        
        future.get(5, TimeUnit.SECONDS);
        future.getTaskFutures().stream().forEach(x -> x.toCompletableFuture().join());
        
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testBatchSubmitCallable() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService("test");
        RExecutorBatchFuture future = e.submit(new IncrementCallableTask("myCounter"), new IncrementCallableTask("myCounter"), 
                    new IncrementCallableTask("myCounter"), new IncrementCallableTask("myCounter"));
        
        future.get(5, TimeUnit.SECONDS);
        future.getTaskFutures().stream().forEach(x -> assertThat(x.toCompletableFuture().getNow(null)).isEqualTo("1234"));
        
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
    }

    
    @Test
    public void testBatchExecuteNPE() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            RExecutorService e = redisson.getExecutorService("test");
            e.execute();
        });
    }

//    @Test
    public void testTaskFinishing() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        new MockUp<TasksRunnerService>() {
            @Mock
            private void finish(Invocation invocation, String requestId) {
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
        
        RExecutorService executor = redisson.getExecutorService("test2");
        RExecutorFuture<?> f = executor.submit(new FailoverTask("finished"));
        Thread.sleep(2000);
        node.shutdown();

        f.get();
        assertThat(redisson.<Boolean>getBucket("finished").get()).isTrue();
    }

    @Test
    public void testFailoverInSentinel() throws Exception {
        withSentinel((nodes, config) -> {
            node.shutdown();

            config.useSentinelServers().setRetryAttempts(10);

            RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
            nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test2", 1));
            node = RedissonNode.create(nodeConfig);
            node.start();

            RedissonClient redisson = Redisson.create(config);
            RExecutorService executor = redisson.getExecutorService("test2", ExecutorOptions.defaults().taskRetryInterval(25, TimeUnit.SECONDS));
            for (int i = 0; i < 10; i++) {
                executor.submit(new DelayedTask(2000, "counter"));
            }
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);

            Integer port = nodes.get(0).getFirstMappedPort();
            nodes.get(0).stop();
            System.out.println("master stopped! " + port);

            Awaitility.waitAtMost(Duration.ofSeconds(120)).untilAsserted(() -> {
                assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(10);
            });

            redisson.shutdown();
        }, 1);
    }

    
    @Test
    public void testNodeFailover() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        new MockUp<TasksRunnerService>() {
            @Mock
            void finish(Invocation invocation, String requestId, boolean removeTask) {
                if (counter.incrementAndGet() > 1) {
                    invocation.proceed();
                } else {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        
        Config config = createConfig();
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test2", 1));
        node.shutdown();
        node = RedissonNode.create(nodeConfig);
        node.start();
        
        
        RExecutorService executor = redisson.getExecutorService("test2", ExecutorOptions.defaults().taskRetryInterval(10, TimeUnit.SECONDS));
        RExecutorFuture<?> f = executor.submit(new IncrementRunnableTask("counter"));
        assertThat(executor.getTaskCount()).isEqualTo(1);
        Thread.sleep(1000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);
        Thread.sleep(1000);
        System.out.println("shutdown");
        node.shutdown();

        assertThat(executor.getTaskCount()).isEqualTo(1);

        node = RedissonNode.create(nodeConfig);
        node.start();

        assertThat(executor.getTaskCount()).isEqualTo(1);

        Thread.sleep(8500);
        assertThat(executor.getTaskCount()).isEqualTo(0);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);

        Thread.sleep(16000);
        assertThat(executor.getTaskCount()).isEqualTo(0);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);

        redisson.getKeys().delete("counter");
        f.get();
        assertThat(redisson.getKeys().count()).isEqualTo(3);
    }
    
    @Test
    public void testBatchExecute() {
        RExecutorService e = redisson.getExecutorService("test");
        e.execute(new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"), 
                    new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"));
        
        await().atMost(Duration.ofSeconds(5)).until(() -> redisson.getAtomicLong("myCounter").get() == 4);
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
    }


    public static class TestClass implements Runnable, Serializable {

        @RInject
        private String id;

        @RInject
        private RedissonClient client;

        @Override
        public void run() {
            client.getBucket("id").set(id);
        }
    }

    @Test
    public void testTaskId() throws ExecutionException, InterruptedException {
        RExecutorService executor = redisson.getExecutorService("test");
        RExecutorFuture<?> future = executor.submit(new TestClass());
        future.get();
        String id = redisson.<String>getBucket("id").get();
        assertThat(future.getTaskId()).isEqualTo(id);
    }

    @Test
    public void testNameMapper() throws ExecutionException, InterruptedException, TimeoutException {
        Config c = createConfig();
        c.useSingleServer().setNameMapper(new NameMapper() {
            @Override
            public String map(String name) {
                return name + ":mysuffix";
            }

            @Override
            public String unmap(String name) {
                return name.replace(":mysuffix", "");
            }
        });
        RedissonClient redisson = Redisson.create(c);

        RScheduledExecutorService e = redisson.getExecutorService("test");
        e.registerWorkers(WorkerOptions.defaults());

        RExecutorFuture<?> future = e.submit(new RunnableTask());
        future.toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    @Test
    public void testTaskStarted() throws InterruptedException {
        RExecutorService executor = redisson.getExecutorService("test1");
        CountDownLatch l = new CountDownLatch(1);
        executor.registerWorkers(WorkerOptions.defaults().addListener(new TaskStartedListener() {
            @Override
            public void onStarted(String taskId) {
                assertThat(taskId).isNotEmpty();
                l.countDown();
            }
        }));

        RExecutorFuture<?> future = executor.submit(new RunnableTask());

        l.await();

        executor.shutdown();
    }

    @Test
    public void testTaskFinished() throws InterruptedException {
        RExecutorService executor = redisson.getExecutorService("test1");
        CountDownLatch l = new CountDownLatch(1);
        executor.registerWorkers(WorkerOptions.defaults().addListener(new TaskFinishedListener() {
            @Override
            public void onFinished(String taskId) {
                assertThat(taskId).isNotEmpty();
                l.countDown();
            }
        }));

        RExecutorFuture<?> future = executor.submit(new RunnableTask());

        l.await();

        executor.shutdown();
    }

    @Test
    public void testTaskTimeout() throws InterruptedException {
        RExecutorService executor = redisson.getExecutorService("test1");
        executor.registerWorkers(WorkerOptions.defaults().taskTimeout(1, TimeUnit.SECONDS));

        RExecutorFuture<?> future = executor.submit(new ScheduledLongRunnableTask("executed1"));

        Thread.sleep(1150);

        assertThat(future.isCancelled()).isTrue();

        executor.shutdown();
    }

    @Test
    public void testSetTaskId() {
        RExecutorService executor = redisson.getExecutorService("test");
        RExecutorFuture<?> future = executor.submit("1234", new ScheduledRunnableTask("executed1"));
        assertThat(future.getTaskId()).isEqualTo("1234");
        future.toCompletableFuture().join();

        assertThat(redisson.getAtomicLong("executed1").get()).isEqualTo(1);
    }

    @Test
    public void testCancelAndInterrupt() throws InterruptedException, ExecutionException {
        RExecutorService executor = redisson.getExecutorService("test");
        RExecutorFuture<?> future = executor.submit(new ScheduledLongRunnableTask("executed1"));
        Thread.sleep(2000);
        cancel(future);
        assertThat(redisson.<Long>getBucket("executed1").get()).isBetween(1000L, Long.MAX_VALUE);
        RExecutorFuture<?> futureAsync = executor.submitAsync(new ScheduledLongRunnableTask("executed2"));
        Thread.sleep(2000);
        assertThat(executor.cancelTask(futureAsync.getTaskId())).isTrue();
        assertThat(redisson.<Long>getBucket("executed2").get()).isBetween(1000L, Long.MAX_VALUE);
        
        executor.delete();
        redisson.getKeys().delete("executed1", "executed2");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testMultipleTasks() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService("test");
        e.execute(new RunnableTask());
        Future<?> f = e.submit(new RunnableTask2());
        f.get();
        Future<String> fs = e.submit(new CallableTask());
        assertThat(fs.get()).isEqualTo(CallableTask.RESULT);
        
        Future<Integer> f2 = e.submit(new RunnableTask(), 12);
        assertThat(f2.get()).isEqualTo(12);
        
        String invokeResult = e.invokeAny(Arrays.asList(new CallableTask(), new CallableTask(), new CallableTask()));
        assertThat(invokeResult).isEqualTo(CallableTask.RESULT);
        
        String a = e.invokeAny(Arrays.asList(new CallableTask(), new CallableTask(), new CallableTask()), 5, TimeUnit.SECONDS);
        assertThat(a).isEqualTo(CallableTask.RESULT);
        
        List<CallableTask> invokeAllParams = Arrays.asList(new CallableTask(), new CallableTask(), new CallableTask());
        List<Future<String>> allResult = e.invokeAll(invokeAllParams);
        assertThat(allResult).hasSize(invokeAllParams.size());
        for (Future<String> future : allResult) {
            assertThat(future.get()).isEqualTo(CallableTask.RESULT);
        }
        
        List<CallableTask> invokeAllParams1 = Arrays.asList(new CallableTask(), new CallableTask(), new CallableTask());
        List<Future<String>> allResult1 = e.invokeAll(invokeAllParams1, 5, TimeUnit.SECONDS);
        assertThat(allResult1).hasSize(invokeAllParams.size());
        for (Future<String> future : allResult1) {
            assertThat(future.get()).isEqualTo(CallableTask.RESULT);
        }
    }
    
    @Test
    @Timeout(1)
    public void testRejectExecute() {
        Assertions.assertThrows(RejectedExecutionException.class, () -> {
            RExecutorService e = redisson.getExecutorService("test");
            e.execute(new RunnableTask());
            Future<?> f1 = e.submit(new RunnableTask2());
            Future<String> f2 = e.submit(new CallableTask());

            e.shutdown();

            f1.get();
            assertThat(f2.get()).isEqualTo(CallableTask.RESULT);

            assertThat(e.isShutdown()).isTrue();
            e.execute(new RunnableTask());

            assertThat(redisson.getKeys().count()).isZero();
        });
    }
    
    @Test
    public void testRejectSubmitRunnable() {
        Assertions.assertThrows(RejectedExecutionException.class, () -> {
            RExecutorService e = redisson.getExecutorService("test");
            e.execute(new RunnableTask());
            Future<?> f1 = e.submit(new RunnableTask2());
            Future<String> f2 = e.submit(new CallableTask());

            e.shutdown();

            f1.get();
            assertThat(f2.get()).isEqualTo(CallableTask.RESULT);

            assertThat(e.isShutdown()).isTrue();
            e.submit(new RunnableTask2());

            assertThat(redisson.getKeys().count()).isZero();
        });
    }

    @Test
    public void testRejectSubmitCallable() {
        Assertions.assertThrows(RejectedExecutionException.class, () -> {
            RExecutorService e = redisson.getExecutorService("test");
            e.execute(new RunnableTask());
            Future<?> f1 = e.submit(new RunnableTask2());
            Future<String> f2 = e.submit(new CallableTask());

            e.shutdown();

            f1.get();
            assertThat(f2.get()).isEqualTo(CallableTask.RESULT);

            assertThat(e.isShutdown()).isTrue();
            e.submit(new CallableTask());

            assertThat(redisson.getKeys().count()).isZero();
        });
    }
    
    @Test
    public void testInvokeAll() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService("test");
        List<Future<String>> futures = e.invokeAll(Arrays.asList(new CallableTask(), new CallableTask()));
        for (Future<String> future : futures) {
            assertThat(future.isDone());
        }
        e.shutdown();
    }

    @Test
    public void testInvokeAny() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService("test");
        Object res = e.invokeAny(Arrays.asList((Callable<Object>)(Object)new CallableTask(), new DelayedTask(20000, "counter")));
        assertThat(res).isEqualTo(CallableTask.RESULT);
        e.shutdown();
    }

    
    @Test
    public void testEmptyRejectSubmitRunnable() {
        Assertions.assertThrows(RejectedExecutionException.class, () -> {
            RExecutorService e = redisson.getExecutorService("test");
            e.shutdown();

            assertThat(e.isShutdown()).isTrue();
            e.submit(new RunnableTask2());

            assertThat(redisson.getKeys().count()).isZero();
        });
    }

//    @Test
    public void testPerformance() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService("test");
        for (int i = 0; i < 5000; i++) {
            e.execute(new RunnableTask());        
        }
        e.shutdown();
        assertThat(e.awaitTermination(1500, TimeUnit.MILLISECONDS)).isTrue();
    }
    
    @Test
    public void testShutdown() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService("test");
        assertThat(e.isShutdown()).isFalse();
        assertThat(e.isTerminated()).isFalse();
        e.execute(new RunnableTask());
        e.shutdown();
        assertThat(e.isShutdown()).isTrue();
        assertThat(e.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(e.isTerminated()).isTrue();
    }

    @Test
    public void testIdCheck() {
        RExecutorService e = redisson.getExecutorService("test");

        e.submit("1", new RunnableTask());

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            e.submit("1", new RunnableTask());
        });

        e.submit("2", new CallableTask());

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            e.submit("2", new CallableTask());
        });

        e.submit("3", new CallableTask(), Duration.ofSeconds(10));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            e.submit("3", new CallableTask(), Duration.ofSeconds(10));
        });

        e.submit("4", new RunnableTask(), Duration.ofSeconds(10));

        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            e.submit("4", new RunnableTask(), Duration.ofSeconds(10));
        });
    }
    
    @Test
    public void testShutdownEmpty() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService("test");
        assertThat(e.isShutdown()).isFalse();
        assertThat(e.isTerminated()).isFalse();
        e.shutdown();
        assertThat(e.isShutdown()).isTrue();
        assertThat(e.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(e.isTerminated()).isTrue();
    }

    @Test
    public void testResetShutdownState() throws InterruptedException, ExecutionException, TimeoutException {
        for (int i = 0; i < 10; i++) {
            RExecutorService e = redisson.getExecutorService("test");
            e.execute(new RunnableTask());
            assertThat(e.isShutdown()).isFalse();
            e.shutdown();
            assertThat(e.isShutdown()).isTrue();
            assertThat(e.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(e.isTerminated()).isTrue();
            assertThat(e.delete()).isTrue();
            assertThat(e.isShutdown()).isFalse();
            assertThat(e.isTerminated()).isFalse();
            Future<?> future = e.submit(new RunnableTask());
            future.get(30, TimeUnit.SECONDS);
        }
    }
    
    @Test
    public void testRedissonInjected() throws InterruptedException, ExecutionException {
        Future<Long> s1 = redisson.getExecutorService("test").submit(new CallableRedissonTask(1L));
        Future<Long> s2 = redisson.getExecutorService("test").submit(new CallableRedissonTask(2L));
        Future<Long> s3 = redisson.getExecutorService("test").submit(new CallableRedissonTask(30L));
        Future<Void> s4 = (Future<Void>) redisson.getExecutorService("test").submit(new RunnableRedissonTask("runnableCounter"));
        
        List<Long> results = Arrays.asList(s1.get(), s2.get(), s3.get());
        assertThat(results).containsOnlyOnce(33L);
        
        s4.get();
        assertThat(redisson.getAtomicLong("runnableCounter").get()).isEqualTo(100L);
        
        redisson.getExecutorService("test").delete();
        redisson.getKeys().delete("runnableCounter", "counter");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testParameterizedTask() throws InterruptedException, ExecutionException {
        Future<String> future = redisson.getExecutorService("test").submit(new ParameterizedTask("testparam"));
        assertThat(future.get()).isEqualTo("testparam");
    }

    @Test
    public void testTTL() throws InterruptedException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        executor.submit(new DelayedTask(2000, "test"));
        Future<?> future = executor.submit(new ScheduledRunnableTask("testparam"), 1, TimeUnit.SECONDS);
        Thread.sleep(10);
        assertThat(executor.getTaskCount()).isEqualTo(2);
        Thread.sleep(2000);
        assertThat(executor.getTaskCount()).isEqualTo(0);
        assertThat(redisson.getKeys().countExists("testparam")).isEqualTo(1);
    }

    @Test
    public void testExpiration() throws InterruptedException, ExecutionException {
        RScheduledExecutorService executor = redisson.getExecutorService("test");
        Future<?> future = executor.submit(new ScheduledRunnableTask("testparam"), 10, TimeUnit.SECONDS);

        future.get();

        assertThat(redisson.getKeys().countExists("testparam")).isEqualTo(1);
        String tasksExpirationTimeName = Reflect.on(executor).get("tasksExpirationTimeName");
        RScoredSortedSet<String> set = redisson.getScoredSortedSet(tasksExpirationTimeName);
        assertThat(set.size()).isEqualTo(0);
    }

    @Test
    public void testAnonymousRunnable() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            redisson.getExecutorService("test").submit(new Runnable() {
                @Override
                public void run() {
                }
            });
        });
    }
    
    @Test
    public void testAnonymousCallable() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            redisson.getExecutorService("test").submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return null;
                }
            });
        });
    }
    
    public class TaskCallableClass implements Callable<String> {

        @Override
        public String call() throws Exception {
            return "123";
        }

    }
    
    @Test
    public void testNonStaticInnerClassCallable() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            redisson.getExecutorService("test").submit(new TaskCallableClass());
        });
    }

    public static class TaskStaticCallableClass implements Callable<String>, Serializable {

        @Override
        public String call() throws Exception {
            return "123";
        }
        
    }

    @Test
    public void testInnerClassCallable() throws InterruptedException, ExecutionException {
        String res = redisson.getExecutorService("test").submit(new TaskStaticCallableClass()).get();
        assertThat(res).isEqualTo("123");
    }
    
    public class TaskRunnableClass implements Runnable, Serializable {

        @Override
        public void run() {
        }

    }
    
    @Test
    public void testNonStaticInnerClassRunnable() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            redisson.getExecutorService("test").submit(new TaskRunnableClass());
        });
    }

    public static class TaskStaticRunnableClass implements Runnable, Serializable {

        @Override
        public void run() {
        }

    }

    @Test
    public void testInnerClassRunnable() throws InterruptedException, ExecutionException {
        redisson.getExecutorService("test").submit(new TaskStaticRunnableClass()).get();
    }

    @Test
    public void testAnonymousRunnableExecute() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            redisson.getExecutorService("test").execute(new Runnable() {
                @Override
                public void run() {
                }
            });
        });
    }

    @Test
    public void testTaskDelay4TaskService() throws IllegalAccessException, NoSuchFieldException, InterruptedException {
        RScheduledExecutorService test = redisson.getExecutorService("test");
        String topicName = Reflect.on(test).get("schedulerChannelName");
        RedissonTopic topic = RedissonTopic.createRaw(LongCodec.INSTANCE, ((Redisson) redisson).getCommandExecutor(), topicName);

        AtomicInteger counter = new AtomicInteger();

        topic.addListener(Long.class, new MessageListener<Long>() {
            @Override
            public void onMessage(CharSequence channel, Long msg) {
                counter.incrementAndGet();
            }
        });

        test.submitAsync(new DelayedTask(10000, "test-counter"));
        Thread.sleep(2000);

        assertThat(counter.get()).isGreaterThan(0);
    }

    @Test
    public void testSubmitAfterPause() throws InterruptedException {

        RExecutorService redissonES = redisson.getExecutorService("test-worker");
        redissonES.registerWorkers(WorkerOptions.defaults().workers(2));

        redissonES.submit(new RunnableTask());
        Thread.sleep(Duration.ofSeconds(1));
        assertThat(redissonES.getTaskCount()).isEqualTo(0);

        Thread.sleep(Duration.ofMinutes(1));

        redissonES.submit(new RunnableTask());
        Thread.sleep(Duration.ofSeconds(1));
        assertThat(redissonES.getTaskCount()).isEqualTo(0);
    }

}
