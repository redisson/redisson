package org.redisson.executor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.Redisson;
import org.redisson.RedissonClient;
import org.redisson.api.RExecutorService;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.*;

public class RedissonExecutorServiceTest extends BaseTest {

    private static RedissonClient redissonClient;
    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        BaseTest.beforeClass();
        
        Config config = createConfig();
        redissonClient = Redisson.create(config);
        redissonClient.getExecutorService().registerExecutors(1);
    }
    
    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        BaseTest.afterClass();
        
        redissonClient.shutdown();
    }
    
    @Test
    public void test2() throws InterruptedException, ExecutionException, TimeoutException {
        RExecutorService e = redisson.getExecutorService();
        e.execute(new RedissonRunnableTask());
        Future<?> f = e.submit(new RedissonRunnableTask2());
        f.get();
        Future<String> fs = e.submit(new RedissonCallableTask());
        assertThat(fs.get()).isEqualTo(RedissonCallableTask.RESULT);
        
        Future<Integer> f2 = e.submit(new RedissonRunnableTask(), 12);
        assertThat(f2.get()).isEqualTo(12);
        
        String invokeResult = e.invokeAny(Arrays.asList(new RedissonCallableTask(), new RedissonCallableTask(), new RedissonCallableTask()));
        assertThat(invokeResult).isEqualTo(RedissonCallableTask.RESULT);
        
        String a = e.invokeAny(Arrays.asList(new RedissonCallableTask(), new RedissonCallableTask(), new RedissonCallableTask()), 1, TimeUnit.SECONDS);
        assertThat(a).isEqualTo(RedissonCallableTask.RESULT);
        
        List<RedissonCallableTask> invokeAllParams = Arrays.asList(new RedissonCallableTask(), new RedissonCallableTask(), new RedissonCallableTask());
        List<Future<String>> allResult = e.invokeAll(invokeAllParams);
        assertThat(allResult).hasSize(invokeAllParams.size());
        for (Future<String> future : allResult) {
            assertThat(future.get()).isEqualTo(RedissonCallableTask.RESULT);
        }

        List<RedissonCallableTask> invokeAllParams1 = Arrays.asList(new RedissonCallableTask(), new RedissonCallableTask(), new RedissonCallableTask());
        List<Future<String>> allResult1 = e.invokeAll(invokeAllParams1, 1, TimeUnit.SECONDS);
        assertThat(allResult1).hasSize(invokeAllParams.size());
        for (Future<String> future : allResult1) {
            assertThat(future.get()).isEqualTo(RedissonCallableTask.RESULT);
        }

    }
    
    @Test(expected = RejectedExecutionException.class)
    public void testRejectExecute() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService();
        e.execute(new RedissonRunnableTask());
        Future<?> f1 = e.submit(new RedissonRunnableTask2());
        Future<String> f2 = e.submit(new RedissonCallableTask());
        
        e.shutdown();
        
        f1.get();
        assertThat(f2.get()).isEqualTo(RedissonCallableTask.RESULT);
        
        assertThat(e.isShutdown()).isTrue();
        e.execute(new RedissonRunnableTask());
    }
    
    @Test(expected = RejectedExecutionException.class)
    public void testRejectSubmitRunnable() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService();
        e.execute(new RedissonRunnableTask());
        Future<?> f1 = e.submit(new RedissonRunnableTask2());
        Future<String> f2 = e.submit(new RedissonCallableTask());
        
        e.shutdown();
        
        f1.get();
        assertThat(f2.get()).isEqualTo(RedissonCallableTask.RESULT);
        
        assertThat(e.isShutdown()).isTrue();
        e.submit(new RedissonRunnableTask2());
    }

    @Test(expected = RejectedExecutionException.class)
    public void testRejectSubmitCallable() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService();
        e.execute(new RedissonRunnableTask());
        Future<?> f1 = e.submit(new RedissonRunnableTask2());
        Future<String> f2 = e.submit(new RedissonCallableTask());
        
        e.shutdown();
        
        f1.get();
        assertThat(f2.get()).isEqualTo(RedissonCallableTask.RESULT);
        
        assertThat(e.isShutdown()).isTrue();
        e.submit(new RedissonCallableTask());
    }
    
    @Test(expected = RejectedExecutionException.class)
    public void testEmptyRejectSubmitRunnable() throws InterruptedException, ExecutionException {
        RExecutorService e = redisson.getExecutorService();
        e.shutdown();
        
        assertThat(e.isShutdown()).isTrue();
        e.submit(new RedissonRunnableTask2());
    }

    
    @Test
    public void testShutdown() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService();
        assertThat(e.isShutdown()).isFalse();
        assertThat(e.isTerminated()).isFalse();
        e.execute(new RedissonRunnableTask());
        e.shutdown();
        assertThat(e.isShutdown()).isTrue();
        assertThat(e.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(e.isTerminated()).isTrue();
    }
    
    @Test
    public void testShutdownEmpty() throws InterruptedException {
        RExecutorService e = redisson.getExecutorService();
        assertThat(e.isShutdown()).isFalse();
        assertThat(e.isTerminated()).isFalse();
        e.shutdown();
        assertThat(e.isShutdown()).isTrue();
        assertThat(e.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(e.isTerminated()).isTrue();
    }

    @Test
    public void testResetShutdownState() throws InterruptedException, ExecutionException {
        for (int i = 0; i < 100; i++) {
            RExecutorService e = redisson.getExecutorService();
            e.execute(new RedissonRunnableTask());
            assertThat(e.isShutdown()).isFalse();
            e.shutdown();
            assertThat(e.isShutdown()).isTrue();
            assertThat(e.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(e.isTerminated()).isTrue();
            assertThat(e.delete()).isTrue();
            assertThat(e.isShutdown()).isFalse();
            assertThat(e.isTerminated()).isFalse();
            Future<?> future = e.submit(new RedissonRunnableTask());
            future.get();
        }
    }
    
}
