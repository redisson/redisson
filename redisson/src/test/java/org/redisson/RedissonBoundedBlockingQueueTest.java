package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NameMapper;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonBoundedBlockingQueueTest extends RedisDockerTest {

    @Test
    public void testNameMapper() {
        Config config = createConfig();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return name + ":suffix:";
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace(":suffix:", "");
                    }
                });

        RedissonClient redisson = Redisson.create(config);
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("bounded-queue");

        queue.trySetCapacity(5);
        queue.add(1);

        queue.delete();
        assertThat(redisson.getKeys().count()).isZero();
    }

        @Test
    public void testOfferTimeout() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("bounded-queue");
        queue.trySetCapacity(5);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);
        queue.add(5);

        long start = System.currentTimeMillis();
        assertThat(queue.offer(6, 2, TimeUnit.SECONDS)).isFalse();
        assertThat(System.currentTimeMillis() - start).isGreaterThan(1900);
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        final AtomicBoolean executed = new AtomicBoolean();
        executor.schedule(new Runnable() {

            @Override
            public void run() {
                RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue");
                assertThat(queue1.remove()).isEqualTo(1);
                executed.set(true);
            }
            
        }, 1, TimeUnit.SECONDS);

        start = System.currentTimeMillis();
        assertThat(queue.offer(6, 3, TimeUnit.SECONDS)).isTrue();
        assertThat(System.currentTimeMillis() - start).isBetween(1000L, 2000L);
        
        await().atMost(2, TimeUnit.SECONDS).untilTrue(executed);
        
        assertThat(queue).containsExactly(2, 3, 4, 5, 6);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }
    
    @Test
    public void testAddAll() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("bounded-queue");
        queue.trySetCapacity(11);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);
        queue.add(5);

        Assertions.assertTrue(queue.addAll(Arrays.asList(7, 8, 9)));
        assertThat(queue.remainingCapacity()).isEqualTo(3);

        Assertions.assertTrue(queue.addAll(Arrays.asList(9, 1, 9)));
        assertThat(queue.remainingCapacity()).isEqualTo(0);

        assertThat(queue).containsExactly(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9);
    }

    
    @Test
    public void testRemoveAll() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("bounded-queue");
        queue.trySetCapacity(5);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);
        queue.add(5);

        Assertions.assertFalse(queue.removeAll(Collections.emptyList()));
        assertThat(queue.remainingCapacity()).isEqualTo(0);
        
        Assertions.assertTrue(queue.removeAll(Arrays.asList(3, 2, 10, 6)));
        assertThat(queue.remainingCapacity()).isEqualTo(2);
        assertThat(queue).containsExactly(1, 4, 5);

        Assertions.assertTrue(queue.removeAll(Arrays.asList(4)));
        assertThat(queue.remainingCapacity()).isEqualTo(3);
        assertThat(queue).containsExactly(1, 5);

        Assertions.assertTrue(queue.removeAll(Arrays.asList(1, 5, 1, 5)));
        assertThat(queue.remainingCapacity()).isEqualTo(5);
        Assertions.assertTrue(queue.isEmpty());
    }

    @Test
    public void testPut() throws InterruptedException {
        final RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue");
        queue1.trySetCapacity(3);
        queue1.add(1);
        queue1.add(2);
        queue1.add(3);
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        
        final AtomicBoolean executed = new AtomicBoolean();
        
        executor.schedule(new Runnable() {

            @Override
            public void run() {
                RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue");
                assertThat(queue1.poll()).isEqualTo(1);
                executed.set(true);
            }
            
        }, 1, TimeUnit.SECONDS);

        queue1.put(4);
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        assertThat(queue1).containsExactly(2, 3, 4);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }
    
    @Test
    public void testConcurrentPut() throws InterruptedException {
        final RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:testConcurrentPut");
        assertThat(queue1.trySetCapacity(10000)).isTrue();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        for (int i = 0; i < 10000; i++) {
            final int k = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    queue1.add(k);
                }
            });
        }
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
        
        assertThat(queue1.size()).isEqualTo(10000);
    }
    
    @Test
    public void testRemainingCapacity() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:testRemainingCapacity");
        assertThat(queue1.trySetCapacity(3)).isTrue();
        assertThat(queue1.remainingCapacity()).isEqualTo(3);
        assertThat(queue1.add(1)).isTrue();
        assertThat(queue1.remainingCapacity()).isEqualTo(2);
        assertThat(queue1.add(2)).isTrue();
        assertThat(queue1.remainingCapacity()).isEqualTo(1);
        assertThat(queue1.add(3)).isTrue();
        assertThat(queue1.remainingCapacity()).isEqualTo(0);
        
        RBoundedBlockingQueue<Integer> queue2 = redisson.getBoundedBlockingQueue("bounded-queue:testRemainingCapacityEmpty");
        assertThat(queue2.trySetCapacity(3)).isTrue();
        for (int i = 0; i < 5; i++) {
            queue2.poll(1, TimeUnit.SECONDS);
            assertThat(queue2.remainingCapacity()).isEqualTo(3);
        }
    }
    
    @Test
    public void testAddFullQueueError() {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:testAddFullQueueError");
        assertThat(queue1.trySetCapacity(1)).isTrue();
        assertThat(queue1.add(1)).isTrue();
        try {
            queue1.add(2);
        } catch (RedisException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }
    
    @Test
    public void testAddRemoveFullQueueError() {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:testAddRemoveFullQueueError");
        assertThat(queue1.trySetCapacity(1)).isTrue();
        assertThat(queue1.add(12)).isTrue();
        assertThat(queue1.remove()).isEqualTo(12);
        assertThat(queue1.add(1)).isTrue();
        try {
            queue1.add(2);
        } catch (RedisException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void testInitCapacityError() {
        Assertions.assertThrows(RedisException.class, () -> {
            RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:testInitCapacityError");
            queue1.add(1);
        });
    }
    
    @Test
    public void testPollWithBrokenConnection() throws InterruptedException, ExecutionException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("bounded-queue:pollTimeout");
        assertThat(queue1.trySetCapacity(5)).isTrue();
        RFuture<Integer> f = queue1.pollAsync(5, TimeUnit.SECONDS);

        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (TimeoutException e) {
            // skip
        }
        redis.stop();

        long start = System.currentTimeMillis();
        assertThat(f.get()).isNull();
        assertThat(System.currentTimeMillis() - start).isGreaterThan(3800);
        
        redisson.shutdown();
    }
    
    @Test
    public void testPollReattach() throws InterruptedException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        final AtomicBoolean executed = new AtomicBoolean();
        
        Thread t = new Thread() {
            public void run() {
                try {
                    RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:pollany");
                    assertThat(queue1.trySetCapacity(10)).isTrue();
                    long start = System.currentTimeMillis();
                    Integer res = queue1.poll(10, TimeUnit.SECONDS);
                    assertThat(System.currentTimeMillis() - start).isGreaterThan(2000);
                    assertThat(res).isEqualTo(123);
                    executed.set(true);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            };
        };
        
        t.start();
        t.join(1000);

        restart(redis);

        Thread.sleep(1000);

        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:pollany");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        queue1.put(123);
        
        t.join();
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        redisson.shutdown();
        redis.stop();
    }
    
    @Test
    public void testPollAsyncReattach() throws InterruptedException, ExecutionException, TimeoutException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);
        
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:pollany");
        RFuture<Integer> f = queue1.pollAsync(10, TimeUnit.SECONDS);
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }

        restart(redis);

        assertThat(queue1.trySetCapacity(15)).isTrue();
        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i);
        }
        assertThat(queue1.size()).isEqualTo(10);
        
        Integer result = f.get(1, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(123);
        
        redisson.shutdown();
        redis.stop();
    }

    
    @Test
    public void testTakeReattach() throws InterruptedException, ExecutionException, TimeoutException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("testTakeReattach");
        assertThat(queue1.trySetCapacity(15)).isTrue();
        RFuture<Integer> f = queue1.takeAsync();
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }

        restart(redis);

        assertThat(queue1.trySetCapacity(15)).isTrue();
        queue1.put(123);
        
        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i);
        }
        assertThat(queue1.size()).isEqualTo(10);
        
        Integer result = f.get(1, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(123);

        redisson.shutdown();
        redis.stop();
    }
    
    @Test
    public void testTakeAsyncCancel() {
        Config config = createConfig();
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);

        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("testTakeAsyncCancel");
        assertThat(queue1.trySetCapacity(15)).isTrue();
        for (int i = 0; i < 10; i++) {
            RFuture<Integer> f = queue1.takeAsync();
            f.cancel(true);
        }
        assertThat(queue1.add(1)).isTrue();
        assertThat(queue1.add(2)).isTrue();
        assertThat(queue1.size()).isEqualTo(2);
        
        redisson.shutdown();
    }
    
    @Test
    public void testPollAsyncCancel() {
        Config config = createConfig();
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);

        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:pollany");
        assertThat(queue1.trySetCapacity(15)).isTrue();
        for (int i = 0; i < 10; i++) {
            RFuture<Integer> f = queue1.pollAsync(1, TimeUnit.SECONDS);
            f.cancel(true);
        }
        assertThat(queue1.add(1)).isTrue();
        assertThat(queue1.add(2)).isTrue();
        assertThat(queue1.size()).isEqualTo(2);
        
        redisson.shutdown();
    }

    @Test
    public void testPollFromAny() throws InterruptedException {
        final RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:pollany");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            RBoundedBlockingQueue<Integer> queue2 = redisson.getBoundedBlockingQueue("queue:pollany1");
            assertThat(queue2.trySetCapacity(10)).isTrue();
            RBoundedBlockingQueue<Integer> queue3 = redisson.getBoundedBlockingQueue("queue:pollany2");
            assertThat(queue3.trySetCapacity(10)).isTrue();
            try {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            } catch (Exception e) {
                Assertions.fail();
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollFromAny(40, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assertions.assertEquals(2, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }

    @Test
    public void testTake() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue:take");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue:take");
            try {
                queue.put(3);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.take();

        Assertions.assertEquals(3, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue1");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        queue1.put(1);
        Assertions.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));

        long s = System.currentTimeMillis();
        Assertions.assertNull(queue1.poll(5, TimeUnit.SECONDS));
        Assertions.assertTrue(System.currentTimeMillis() - s > 5000);
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("queue1");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        queue1.put(1);

        Assertions.assertEquals((Integer)1, queue1.poll(10, TimeUnit.SECONDS));
    }

    @Test
    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        final RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("{queue}1");
        queue1.trySetCapacity(10);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                queue1.put(3);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);

        RBoundedBlockingQueue<Integer> queue2 = redisson.getBoundedBlockingQueue("{queue}2");
        queue2.trySetCapacity(10);
        queue2.put(4);
        queue2.put(5);
        queue2.put(6);

        Integer value = queue1.pollLastAndOfferFirstTo(queue2.getName(), 10, TimeUnit.SECONDS);
        assertThat(value).isEqualTo(3);
        assertThat(queue2).containsExactly(3, 4, 5, 6);
    }

    @Test
    public void testTakeLastAndOfferFirstTo() throws InterruptedException {
        final RBoundedBlockingQueue<Integer> queue1 = redisson.getBoundedBlockingQueue("{queue}1");
        queue1.trySetCapacity(10);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                queue1.put(3);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 3, TimeUnit.SECONDS);

        RBoundedBlockingQueue<Integer> queue2 = redisson.getBoundedBlockingQueue("{queue}2");
        queue2.trySetCapacity(10);
        queue2.put(4);
        queue2.put(5);
        queue2.put(6);

        long startTime = System.currentTimeMillis();
        Integer value = queue1.takeLastAndOfferFirstTo(queue2.getName());
        assertThat(System.currentTimeMillis() - startTime).isBetween(2900L, 3200L);
        assertThat(value).isEqualTo(3);
        assertThat(queue2).containsExactly(3, 4, 5, 6);
    }

    
    @Test
    public void testOffer() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("blocking:queue");
        assertThat(queue.trySetCapacity(2)).isTrue();
        assertThat(queue.offer(1)).isTrue();
        assertThat(queue.offer(2)).isTrue();
        assertThat(queue.offer(3)).isFalse();
        assertThat(queue.offer(4)).isFalse();
    }
    
    @Test
    public void testAddOffer() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("blocking:queue");
        assertThat(queue.trySetCapacity(10)).isTrue();
        assertThat(queue.add(1)).isTrue();
        assertThat(queue.offer(2)).isTrue();
        assertThat(queue.add(3)).isTrue();
        assertThat(queue.offer(4)).isTrue();

        assertThat(queue).containsExactly(1, 2, 3, 4);
        Assertions.assertEquals((Integer) 1, queue.poll());
        assertThat(queue).containsExactly(2, 3, 4);
        Assertions.assertEquals((Integer) 2, queue.element());
    }

    @Test
    public void testRemove() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("blocking:queue");
        assertThat(queue.trySetCapacity(10)).isTrue();
        assertThat(queue.add(1)).isTrue();
        assertThat(queue.add(2)).isTrue();
        assertThat(queue.add(3)).isTrue();
        assertThat(queue.add(4)).isTrue();

        assertThat(queue.remove()).isEqualTo(1);
        assertThat(queue.remove()).isEqualTo(2);

        assertThat(queue).containsExactly(3, 4);
        assertThat(queue.remove()).isEqualTo(3);
        assertThat(queue.remove()).isEqualTo(4);

        Assertions.assertTrue(queue.isEmpty());
    }

    @Test
    public void testRemoveEmpty() {
        Assertions.assertThrows(NoSuchElementException.class, () -> {
            RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("blocking:queue");
            queue.trySetCapacity(10);
            queue.remove();
        });
    }

    @Test
    public void testDrainTo() {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue");
        queue.trySetCapacity(100);
        for (int i = 0 ; i < 100; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
        Assertions.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        assertThat(queue.remainingCapacity()).isEqualTo(0);
        int count = queue.drainTo(batch, 10);
        assertThat(queue.remainingCapacity()).isEqualTo(10);
        Assertions.assertEquals(10, count);
        Assertions.assertEquals(10, batch.size());
        Assertions.assertEquals(90, queue.size());
        queue.drainTo(batch, 10);
        assertThat(queue.remainingCapacity()).isEqualTo(20);
        queue.drainTo(batch, 20);
        assertThat(queue.remainingCapacity()).isEqualTo(40);
        queue.drainTo(batch, 60);
        assertThat(queue.remainingCapacity()).isEqualTo(100);
        Assertions.assertEquals(0, queue.size());
    }
    
    @Test
    public void testDrainToAsync() throws ExecutionException, InterruptedException {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue");
        queue.trySetCapacity(100);
        for (int i = 0 ; i < 100; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
        Assertions.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        RFuture<Integer> future = queue.drainToAsync(batch, 0);
        Assertions.assertEquals(future.get(), 0);
        Assertions.assertEquals(batch.size(), 0);
    }

    @Test
    public void testBlockingQueue() {

        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("test_:blocking:queue:");
        queue.trySetCapacity(10);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        int total = 100;
        for (int i = 0; i < total; i++) {
            // runnable won't be executed in any particular order, and hence, int value as well.
            executor.submit(() -> {
                redisson.getQueue("test_:blocking:queue:").add(counter.incrementAndGet());
            });
        }
        int count = 0;
        while (count < total) {
            try {
                // blocking
                int item = queue.take();
                assertThat(item > 0 && item <= total).isTrue();
            } catch (InterruptedException exception) {
                Assertions.fail();
            }
            count++;
        }

        assertThat(counter.get()).isEqualTo(total);
        queue.delete();
    }

    @Test
    public void testDrainToCollection() throws Exception {
        RBoundedBlockingQueue<Object> queue1 = redisson.getBoundedBlockingQueue("queue1");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst);
        assertThat(queue1.remainingCapacity()).isEqualTo(10);
        assertThat(dst).containsExactly(1, 2L, "e");
        Assertions.assertEquals(0, queue1.size());
    }

    @Test
    public void testDrainToCollectionLimited() throws Exception {
        RBoundedBlockingQueue<Object> queue1 = redisson.getBoundedBlockingQueue("queue1");
        assertThat(queue1.trySetCapacity(10)).isTrue();
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst, 2);
        assertThat(queue1.remainingCapacity()).isEqualTo(9);
        assertThat(dst).containsExactly(1, 2L);
        Assertions.assertEquals(1, queue1.size());

        dst.clear();
        queue1.drainTo(dst, 2);
        assertThat(queue1.remainingCapacity()).isEqualTo(10);
        assertThat(dst).containsExactly("e");
    }
    
    @Test
    public void testSingleCharAsKeyName() {
        String value = "Long Test Message;Long Test Message;Long Test Message;"
                + "Long Test Message;Long Test Message;Long Test Message;Long "
                + "Test Message;Long Test Message;Long Test Message;Long Test "
                + "Message;Long Test Message;Long Test Message;Long Test Messa"
                + "ge;Long Test Message;Long Test Message;Long Test Message;Lo"
                + "ng Test Message;Long Test Message;Long Test Message;Long Te"
                + "st Message;Long Test Message;Long Test Message;Long Test Me"
                + "ssage;Long Test Message;Long Test Message;Long Test Message"
                + ";Long Test Message;Long Test Message;Long Test Message;Long"
                + " Test Message;Long Test Message;Long Test Message;Long Test"
                + " Message;Long Test Message;Long Test Message;Long Test Mess"
                + "age;";
        try {
            for (int i = 0; i < 10; i++) {
                System.out.println("Iteration: " + i);
                RBoundedBlockingQueue<String> q = redisson.getBoundedBlockingQueue(String.valueOf(i));
                q.trySetCapacity(10);
                q.add(value);
                System.out.println("Message added to [" + i + "]");
                q.expire(Duration.ofSeconds(1));
                System.out.println("Expiry set to [" + i + "]");
                String poll = q.poll(1, TimeUnit.SECONDS);
                System.out.println("Message polled from [" + i + "]" + poll);
                Assertions.assertEquals(value, poll);
            }
        } catch (Exception e) {
            Assertions.fail(e.getLocalizedMessage());
        }
    }
    
    @Test
    public void testExpire() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue1");
        queue.trySetCapacity(10);
        queue.add(1);
        queue.add(2);

        queue.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        assertThat(queue).isEmpty();
        assertThat(queue.size()).isZero();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue1");
        queue.trySetCapacity(10);
        queue.add(1);
        queue.add(2);

        queue.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(5000);

        assertThat(queue).isEmpty();
        assertThat(queue.size()).isZero();
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RBoundedBlockingQueue<Integer> queue = redisson.getBoundedBlockingQueue("queue1");
        queue.trySetCapacity(10);
        queue.add(1);
        queue.add(2);

        queue.expireAt(System.currentTimeMillis() + 100);

        queue.clearExpire();

        Thread.sleep(500);

        assertThat(queue).containsExactly(1, 2);
    }

}
