package org.redisson.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.RedissonNode;
import org.redisson.api.ExecutorOptions;
import org.redisson.api.RExecutorBatchFuture;
import org.redisson.api.RExecutorFuture;
import org.redisson.api.RExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;
import org.redisson.connection.balancer.RandomLoadBalancer;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

public class RedissonExecutorServiceTest extends BaseTest {

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
    public void testBatchSubmitRunnable() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService("test");
        RExecutorBatchFuture future = e.submit(new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"), 
                    new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"));
        
        future.get(5, TimeUnit.SECONDS);
        future.getTaskFutures().stream().forEach(x -> x.syncUninterruptibly());
        
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
    }
    
    @Test
    public void testBatchSubmitCallable() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService("test");
        RExecutorBatchFuture future = e.submit(new IncrementCallableTask("myCounter"), new IncrementCallableTask("myCounter"), 
                    new IncrementCallableTask("myCounter"), new IncrementCallableTask("myCounter"));
        
        future.get(5, TimeUnit.SECONDS);
        future.getTaskFutures().stream().forEach(x -> assertThat(x.getNow()).isEqualTo("1234"));
        
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
    }

    
    @Test(expected = NullPointerException.class)
    public void testBatchExecuteNPE() {
        RExecutorService e = redisson.getExecutorService("test");
        e.execute();
    }

    @Test
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
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        
        Thread.sleep(5000); 
        
        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");
        
        RedissonClient redisson = Redisson.create(config);
        
        RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
        nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test2", 1));
        node.shutdown();
        node = RedissonNode.create(nodeConfig);
        node.start();

        RExecutorService executor = redisson.getExecutorService("test2", ExecutorOptions.defaults().taskRetryInterval(10, TimeUnit.SECONDS));
        for (int i = 0; i < 10; i++) {
            executor.submit(new DelayedTask(2000, "counter"));
        }
        Thread.sleep(2500);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);

        master.stop();
        System.out.println("master " + master.getRedisServerAddressAndPort() + " stopped!");
        
        Thread.sleep(TimeUnit.SECONDS.toMillis(70));
        
        master = new RedisRunner()
                .port(master.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();

        System.out.println("master " + master.getRedisServerAddressAndPort() + " started!");
        
        Thread.sleep(25000);
        
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(10);
        
        redisson.shutdown();
        node.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    
    @Test
    public void testNodeFailover() throws Exception {
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
        
        
        RExecutorService executor = redisson.getExecutorService("test2", ExecutorOptions.defaults().taskRetryInterval(10, TimeUnit.SECONDS));
        RExecutorFuture<?> f = executor.submit(new IncrementRunnableTask("counter"));
        f.get();
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(1);
        Thread.sleep(2000);
        node.shutdown();

        node = RedissonNode.create(nodeConfig);
        node.start();
        
        Thread.sleep(8500);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);

        Thread.sleep(16000);
        assertThat(redisson.getAtomicLong("counter").get()).isEqualTo(2);
        
        executor.delete();
        redisson.getKeys().delete("counter");
        assertThat(redisson.getKeys().count()).isEqualTo(1);
    }
    
    @Test
    public void testBatchExecute() {
        RExecutorService e = redisson.getExecutorService("test");
        e.execute(new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"), 
                    new IncrementRunnableTask("myCounter"), new IncrementRunnableTask("myCounter"));
        
        await().atMost(Duration.FIVE_SECONDS).until(() -> redisson.getAtomicLong("myCounter").get() == 4);
        redisson.getKeys().delete("myCounter");
        assertThat(redisson.getKeys().count()).isZero();
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
    
    @Test(expected = RejectedExecutionException.class)
    public void testRejectExecute() throws InterruptedException, ExecutionException {
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
    }
    
    @Test(expected = RejectedExecutionException.class)
    public void testRejectSubmitRunnable() throws InterruptedException, ExecutionException {
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
    }

    @Test(expected = RejectedExecutionException.class)
    public void testRejectSubmitCallable() throws InterruptedException, ExecutionException {
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

    
    @Test(expected = RejectedExecutionException.class)
    public void testEmptyRejectSubmitRunnable() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService("test");
        e.shutdown();
        
        assertThat(e.isShutdown()).isTrue();
        e.submit(new RunnableTask2());
        
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
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
    
    @Test(expected = IllegalArgumentException.class)
    public void testAnonymousRunnable() {
        redisson.getExecutorService("test").submit(new Runnable() {
            @Override
            public void run() {
            }
        });
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testAnonymousCallable() {
        redisson.getExecutorService("test").submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return null;
            }
        });
    }
    
    public class TaskCallableClass implements Callable<String> {

        @Override
        public String call() throws Exception {
            return "123";
        }

    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testNonStaticInnerClassCallable() {
        redisson.getExecutorService("test").submit(new TaskCallableClass());
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
    
    @Test(expected = IllegalArgumentException.class)
    public void testNonStaticInnerClassRunnable() {
        redisson.getExecutorService("test").submit(new TaskRunnableClass());
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

    @Test(expected = IllegalArgumentException.class)
    public void testAnonymousRunnableExecute() {
        redisson.getExecutorService("test").execute(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

}
