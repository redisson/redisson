package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBlockingFairQueue;
import org.redisson.api.RBlockingQueue;

public class RedissonBlockingFairQueueTest extends BaseTest {

    @Test
    public void testFairness() throws InterruptedException {
        int size = 10000;
        RBlockingQueue<String> queue = redisson.getBlockingQueue("test");

        CountDownLatch latch = new CountDownLatch(size);
        AtomicInteger t1Counter = new AtomicInteger();
        AtomicInteger t2Counter = new AtomicInteger();
        Thread t1 = new Thread("test-thread1") {
            public void run() {
                while (true) {
                    try {
                        String a = queue.poll(1, TimeUnit.SECONDS);
                        if (a == null) {
                            break;
                        }
                        latch.countDown();
                        t1Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };

        Thread t2 = new Thread("test-thread1") {
            public void run() {
                while (true) {
                    try {
                        String a = queue.poll(1, TimeUnit.SECONDS);
                        if (a == null) {
                            break;
                        }
                        Thread.sleep(5);
                        latch.countDown();
                        t2Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };
        
        for (int i = 0; i < size; i++) {
            queue.add("" + i);
        }

        t1.start();
        t2.start();
        
        t2.join();
        t1.join();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        System.out.println("t1: " + t1Counter.get());
        System.out.println("t2: " + t2Counter.get());
    }
    
}

