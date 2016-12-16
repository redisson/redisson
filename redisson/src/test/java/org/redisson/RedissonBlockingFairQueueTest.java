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
        int size = 2000;
        RBlockingQueue<String> queue = redisson.getBlockingFairQueue("test");

        CountDownLatch latch = new CountDownLatch(size);
        AtomicInteger t1Counter = new AtomicInteger();
        AtomicInteger t2Counter = new AtomicInteger();
        AtomicInteger t3Counter = new AtomicInteger();
        AtomicInteger t4Counter = new AtomicInteger();
        Thread t1 = new Thread("test-thread1") {
            public void run() {
                RBlockingFairQueue<String> queue = redisson.getBlockingFairQueue("test");
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
                queue.destroy();
            };
        };

        Thread t2 = new Thread("test-thread2") {
            public void run() {
                RBlockingFairQueue<String> queue = redisson.getBlockingFairQueue("test");
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
                queue.destroy();
            };
        };
        
        RBlockingFairQueue<String> queue34 = redisson.getBlockingFairQueue("test");
        Thread t3 = new Thread("test-thread3") {
            public void run() {
                while (true) {
                    try {
                        String a = queue34.poll(1, TimeUnit.SECONDS);
                        if (a == null) {
                            break;
                        }
                        Thread.sleep(10);
                        latch.countDown();
                        t3Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };
        
        Thread t4 = new Thread("test-thread4") {
            public void run() {
                while (true) {
                    try {
                        String a = queue34.poll(1, TimeUnit.SECONDS);
                        if (a == null) {
                            break;
                        }
                        latch.countDown();
                        t4Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };
        
        queue34.destroy();

        
        for (int i = 0; i < size; i++) {
            queue.add("" + i);
        }

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        
        t1.join();
        t2.join();
        t3.join();
        t4.join();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        System.out.println("t1: " + t1Counter.get());
        System.out.println("t2: " + t2Counter.get());
        System.out.println("t3: " + t3Counter.get());
        System.out.println("t4: " + t4Counter.get());
    }
    
}

