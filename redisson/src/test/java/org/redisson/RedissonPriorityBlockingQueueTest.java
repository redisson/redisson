package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonPriorityBlockingQueueTest extends RedissonBlockingQueueTest {

    @Test
    public void testLambda() {
        RPriorityBlockingQueue<RedisURI> priorityQueue = redisson.getPriorityBlockingQueue("anyQueue");
        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            priorityQueue.trySetComparator(Comparator.comparing(RedisURI::getHost));
        });
    }

    @Test
    public void testTakeInterrupted() throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();

        Thread t = new Thread(() -> {
            try {
                RBlockingQueue<Integer> queue1 = getQueue(redisson);
                queue1.take();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        t.start();
        t.join(1000);

        t.interrupt();
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilTrue(interrupted);

        RBlockingQueue<Integer> q = getQueue(redisson);
        q.add(1);
        Thread.sleep(1000);
        assertThat(q.contains(1)).isTrue();
    }

    @Override
    <T> RBlockingQueue<T> getQueue() {
        return redisson.getPriorityBlockingQueue("queue");
    }

    @Override
    <T> RBlockingQueue<T> getQueue(String name) {
        return redisson.getPriorityBlockingQueue(name);
    }
    
    @Override
    <T> RBlockingQueue<T> getQueue(RedissonClient redisson) {
        return redisson.getPriorityBlockingQueue("queue");
    }
    
    @Test
    public void testPollAsyncReattach() throws InterruptedException, ExecutionException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);
        
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.pollAsync(10, TimeUnit.SECONDS);
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }

        restart(redis);

        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i + 1000);
        }
        Integer result = f.get();
        assertThat(queue1.size()).isEqualTo(10);
        
        assertThat(result).isEqualTo(123);
        
        redisson.shutdown();
        redis.stop();
    }
    
    @Test
    public void testTakeReattach() throws Exception {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.takeAsync();
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }

        restart(redis);

        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i + 10000);
        }

        Integer result = f.get();
        assertThat(result).isEqualTo(123);
        assertThat(queue1.size()).isEqualTo(10);

        redisson.shutdown();
        redis.stop();
    }

 
    @Test
    public void testDrainToCollection() throws Exception {
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);
        queue1.put(2);
        queue1.put(3);

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst);
        assertThat(dst).containsExactly(1, 2, 3);
        Assertions.assertEquals(0, queue1.size());
    }

    @Test
    public void testDrainToCollectionLimited() throws Exception {
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);
        queue1.put(2);
        queue1.put(3);

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst, 2);
        assertThat(dst).containsExactly(1, 2);
        Assertions.assertEquals(1, queue1.size());

        dst.clear();
        queue1.drainTo(dst, 2);
        assertThat(dst).containsExactly(3);
    }
    
    
}
