package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedissonPriorityBlockingQueueTest extends RedissonBlockingQueueTest {

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
    public void testPollAsyncReattach() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.pollAsync(10, TimeUnit.SECONDS);
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }
        runner.stop();

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();
        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i + 1000);
        }
        Integer result = f.get();
        assertThat(queue1.size()).isEqualTo(10);
        
        assertThat(result).isEqualTo(123);
        
        redisson.shutdown();
        runner.stop();
    }
    
    @Test
    public void testTakeReattach() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.takeAsync();
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }
        runner.stop();

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();
        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i + 10000);
        }

        Integer result = f.get();
        assertThat(result).isEqualTo(123);
        assertThat(queue1.size()).isEqualTo(10);
        runner.stop();
        
        redisson.shutdown();
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
