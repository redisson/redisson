package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.redisson.api.RBlockingFairQueue;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;

public class RedissonBlockingFairQueueTest extends BaseTest {

    @Test
    public void testTimeout() throws InterruptedException {
        int size = 1000;
        CountDownLatch latch = new CountDownLatch(size);
        AtomicInteger t1Counter = new AtomicInteger();
        AtomicInteger t2Counter = new AtomicInteger();
        AtomicInteger t3Counter = new AtomicInteger();

        RedissonClient redisson1 = createInstance();
        RBlockingFairQueue<String> queue1 = redisson1.getBlockingFairQueue("test");
        Thread t1 = new Thread("test-thread1") {
            public void run() {
                while (true) {
                    try {
                        String a = queue1.poll(5, TimeUnit.SECONDS);
                        if (latch.getCount() == 0) {
                            break;
                        }
                        if (a == null) {
                            continue;
                        }
                        latch.countDown();
                        t1Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };

        RedissonClient redisson2 = createInstance();
        RBlockingFairQueue<String> queue2 = redisson2.getBlockingFairQueue("test");
        Thread t2 = new Thread("test-thread2") {
            public void run() {
                try {
                    String a = queue2.poll(2, TimeUnit.SECONDS);
                    if (a != null) {
                        latch.countDown();
                        t2Counter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                }
            };
        };
        
        RedissonClient redisson3 = createInstance();
        RBlockingFairQueue<String> queue3 = redisson3.getBlockingFairQueue("test");
        Thread t3 = new Thread("test-thread3") {
            public void run() {
                while (true) {
                    try {
                        String a = queue3.poll(5, TimeUnit.SECONDS);
                        if (latch.getCount() == 0) {
                            break;
                        }
                        if (a == null) {
                            continue;
                        }
                        latch.countDown();
                        t3Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };
        
        t1.start();
        t1.join(500);
        t2.start();
        t2.join(500);
        t3.start();
        t3.join(500);
        
        RBlockingQueue<String> queue = redisson.getBlockingFairQueue("test");
        assertThat(redisson.getList("{" + queue.getName() + "}:list").size()).isEqualTo(3);
        
        for (int i = 0; i < size; i++) {
            queue.add("" + i);
        }

        t1.join();
        t2.join();
        t3.join();
 
        assertThat(latch.await(50, TimeUnit.SECONDS)).isTrue();
        
        assertThat(t1Counter.get()).isBetween(499, 500);
        assertThat(t2Counter.get()).isEqualTo(1);
        assertThat(t3Counter.get()).isBetween(499, 500);
        
        assertThat(redisson.getList("{" + queue.getName() + "}:list").size()).isEqualTo(2);
    }
    
    @Test
    public void testFairness() throws InterruptedException {
        int size = 1000;

        CountDownLatch latch = new CountDownLatch(size);
        AtomicInteger t1Counter = new AtomicInteger();
        AtomicInteger t2Counter = new AtomicInteger();
        AtomicInteger t3Counter = new AtomicInteger();
        AtomicInteger t4Counter = new AtomicInteger();

        RedissonClient redisson1 = createInstance();
        RBlockingFairQueue<String> queue1 = redisson1.getBlockingFairQueue("test");
        Thread t1 = new Thread("test-thread1") {
            public void run() {
                while (true) {
                    try {
                        String a = queue1.poll(1, TimeUnit.SECONDS);
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

        RedissonClient redisson2 = createInstance();
        RBlockingFairQueue<String> queue2 = redisson2.getBlockingFairQueue("test");
        Thread t2 = new Thread("test-thread2") {
            public void run() {
                while (true) {
                    try {
                        String a = queue2.poll(1, TimeUnit.SECONDS);
                        if (a == null) {
                            break;
                        }
                        Thread.sleep(50);
                        latch.countDown();
                        t2Counter.incrementAndGet();
                    } catch (InterruptedException e) {
                    }
                }
            };
        };
        
        RedissonClient redisson3 = createInstance();
        RBlockingFairQueue<String> queue3 = redisson3.getBlockingFairQueue("test");
        Thread t3 = new Thread("test-thread3") {
            public void run() {
                while (true) {
                    try {
                        String a = queue3.poll(1, TimeUnit.SECONDS);
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
        
        RedissonClient redisson4 = createInstance();
        RBlockingFairQueue<String> queue4 = redisson4.getBlockingFairQueue("test");
        Thread t4 = new Thread("test-thread4") {
            public void run() {
                while (true) {
                    try {
                        String a = queue4.poll(1, TimeUnit.SECONDS);
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
        
        RBlockingQueue<String> queue = redisson.getBlockingFairQueue("test");
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
        
        queue1.destroy();
        queue2.destroy();
        queue3.destroy();
        queue4.destroy();
        redisson1.shutdown();
        redisson2.shutdown();
        redisson3.shutdown();
        redisson4.shutdown();
        
        assertThat(t1Counter.get()).isEqualTo(250);
        assertThat(t2Counter.get()).isEqualTo(250);
        assertThat(t3Counter.get()).isEqualTo(250);
        assertThat(t4Counter.get()).isEqualTo(250);
        assertThat(redisson.getKeys().count()).isEqualTo(1);
    }
    
}

