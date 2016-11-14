package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.api.RBlockingFairQueue;

public class RedissonBlockingFairQueueTest extends BaseTest {

    @Test
    public void testPollTimeout() throws InterruptedException {
        int size = 100;
        RBlockingFairQueue<String> queue = redisson.getBlockingFairQueue("test");

        CountDownLatch latch = new CountDownLatch(size);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < size; i++) {
            final int j = i;
            Thread t = new Thread() {
                public void run() {
                    try {
                        String value = queue.poll(1, TimeUnit.SECONDS);
                        assertThat(value).isEqualTo("" + j);
                        latch.countDown();
                    } catch (InterruptedException e) {
                    }
                };
            };
            
            threads.add(t);
        }
        
        for (Thread thread : threads) {
            thread.start();
            thread.join(5);
        }
        
        for (int i = 0; i < size; i++) {
            queue.add("" + i);
        }
        
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }
    
}
