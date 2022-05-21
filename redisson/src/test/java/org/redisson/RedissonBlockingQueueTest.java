package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS.l;

public class RedissonBlockingQueueTest extends RedissonQueueTest {

    @Override
    <T> RBlockingQueue<T> getQueue() {
        return redisson.getBlockingQueue("queue");
    }
    
    <T> RBlockingQueue<T> getQueue(String name) {
        return redisson.getBlockingQueue(name);
    }
    
    <T> RBlockingQueue<T> getQueue(RedissonClient redisson) {
        return redisson.getBlockingQueue("queue");
    }
    
    @Test
    public void testPollWithBrokenConnection() throws IOException, InterruptedException, ExecutionException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        final RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.pollAsync(5, TimeUnit.SECONDS);

        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (TimeoutException e) {
            // skip
        }
        runner.stop();

        long start = System.currentTimeMillis();
        assertThat(f.get()).isNull();
        assertThat(System.currentTimeMillis() - start).isGreaterThan(3800);
        
        redisson.shutdown();
    }
    
    @Test
    public void testShortPoll() {
        Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
            RBlockingQueue<Integer> queue = getQueue();
            queue.poll(500, TimeUnit.MILLISECONDS);
            queue.poll(10, TimeUnit.MICROSECONDS);
        });
    }
    
    @Test
    public void testPollReattach() throws InterruptedException, IOException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .requirepass("1234")
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort())
        .setPassword("1234");
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        
        Thread t = new Thread() {
            public void run() {
                try {
                    RBlockingQueue<Integer> queue1 = getQueue(redisson);
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
        runner.stop();

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .requirepass("1234")
                .run();
        
        Thread.sleep(1000);

        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        queue1.put(123);
        
        t.join();
        
        await().atMost(7, TimeUnit.SECONDS).untilTrue(executed);
        
        redisson.shutdown();
        runner.stop();
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
            queue1.put(i);
        }
        assertThat(queue1.size()).isEqualTo(10);
        
        Integer result = f.get(1, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(123);
        
        redisson.shutdown();
        runner.stop();
    }

    @Test
    public void testTakeReattachCluster() throws IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();

        Thread.sleep(1000);

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisProcess master = process.getNodes().stream().filter(x -> x.getRedisServerPort() == master1.getPort()).findFirst().get();

        List<RFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RBlockingQueue<Integer> queue = redisson.getBlockingQueue("queue" + i);
            RFuture<Integer> f = queue.takeAsync();
            try {
                f.toCompletableFuture().get(1, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                // skip
            }
            futures.add(f);
        }

        master.stop();

        Thread.sleep(TimeUnit.SECONDS.toMillis(80));

        for (int i = 0; i < 10; i++) {
            RBlockingQueue<Integer> queue = redisson.getBlockingQueue("queue" + i);
            queue.put(i*100);
        }

        for (int i = 0; i < 10; i++) {
            RFuture<Integer> f = futures.get(i);
            try {
                f.toCompletableFuture().get(20, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                // skip
            }
            Integer result = f.toCompletableFuture().getNow(null);
            assertThat(result).isEqualTo(i*100);
        }

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testTakeReattachSentinel() throws IOException, InterruptedException, TimeoutException, ExecutionException {
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

        Thread.sleep(1000);

        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");
        RedissonClient redisson = Redisson.create(config);

        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.takeAsync();
        try {
            f.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        }

        master.stop();

        Thread.sleep(TimeUnit.SECONDS.toMillis(60));

        queue1.put(123);

        // check connection rotation
        for (int i = 0; i < 10; i++) {
            queue1.put(i + 10000);
        }
        assertThat(queue1.size()).isEqualTo(10);

        Integer result = f.get(80, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(123);

        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();

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
            e.printStackTrace();
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
        assertThat(queue1.size()).isEqualTo(10);
        
        Integer result = f.get(1, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(123);
        runner.stop();
        
        redisson.shutdown();
    }
    
    @Test
    public void testTakeInterrupted() throws InterruptedException {
        final AtomicBoolean interrupted = new AtomicBoolean();
        
        Thread t = new Thread() {
            public void run() {
                try {
                    RBlockingQueue<Integer> queue1 = getQueue(redisson);
                    queue1.take();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            };
        };

        t.start();
        t.join(1000);

        t.interrupt();
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilTrue(interrupted);

        RBlockingQueue<Integer> q = getQueue(redisson);
        q.add(1);
        Thread.sleep(1000);
        assertThat(q.contains(1)).isTrue();
    }

    @Test
    public void testPollInterrupted() throws InterruptedException {
        final AtomicBoolean interrupted = new AtomicBoolean();
        
        Thread t = new Thread() {
            public void run() {
                try {
                    RBlockingQueue<Integer> queue1 = getQueue(redisson);
                    queue1.poll(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            };
        };
        
        t.start();
        t.join(1000);
        
        t.interrupt();
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilTrue(interrupted);
    }
    
    @Test
    public void testTakeAsyncCancel() {
        Config config = createConfig();
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
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
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
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
    public void testPollFromAnyInCluster() throws Exception {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();
        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Thread.sleep(5000); 
        
        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
            RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
            try {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }, 3, TimeUnit.SECONDS);

        Awaitility.await().between(Duration.ofSeconds(2), Duration.ofSeconds(4)).untilAsserted(() -> {
            int value = queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");
            assertThat(value).isEqualTo(1);
        });

        redisson.shutdown();
        process.shutdown();
    }
    
    @Test
    public void testPollFromAny() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
            RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
            Assertions.assertDoesNotThrow(() -> {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            });
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assertions.assertEquals(2, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
    }

    @Test
    public void testPollFirstFromAny() throws InterruptedException {
//        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("7.0.0") > 0);

        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
        RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
        Assertions.assertDoesNotThrow(() -> {
            queue3.put(1);
            queue3.put(2);
            queue3.put(3);
            queue1.put(4);
            queue1.put(5);
            queue1.put(6);
            queue2.put(7);
            queue2.put(8);
            queue2.put(9);
        });

        Map<String, List<Integer>> res = queue1.pollFirstFromAny(Duration.ofSeconds(4), 2, "queue:pollany1", "queue:pollany2");
        assertThat(res.get("queue:pollany")).containsExactly(4, 5);
        queue1.clear();
        Map<String, List<Integer>> res2 = queue1.pollFirstFromAny(Duration.ofSeconds(4), 2);
        assertThat(res2).isNull();
    }

    @Test
    public void testPollLastFromAny() throws InterruptedException {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("7.0.0") > 0);

        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
        RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
        Assertions.assertDoesNotThrow(() -> {
            queue3.put(1);
            queue3.put(2);
            queue3.put(3);
            queue1.put(4);
            queue1.put(5);
            queue1.put(6);
            queue2.put(7);
            queue2.put(8);
            queue2.put(9);
        });

        Map<String, List<Integer>> res = queue1.pollLastFromAny(Duration.ofSeconds(4), 2, "queue:pollany1", "queue:pollany2");
        assertThat(res.get("queue:pollany")).containsExactly(6, 5);
    }

    @Test
    public void testTake() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = getQueue();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueue<Integer> queue = getQueue();
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
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);
        Assertions.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));

        long s = System.currentTimeMillis();
        Assertions.assertNull(queue1.poll(5, TimeUnit.SECONDS));
        Assertions.assertTrue(System.currentTimeMillis() - s > 4900);
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);

        Assertions.assertEquals((Integer)1, queue1.poll(10, TimeUnit.SECONDS));
    }

    @Test
    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("{queue}1");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                queue1.put(3);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 5, TimeUnit.SECONDS);

        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("{queue}2");
        queue2.put(4);
        queue2.put(5);
        queue2.put(6);

        Integer value = queue1.pollLastAndOfferFirstTo(queue2.getName(), 5, TimeUnit.SECONDS);
        assertThat(value).isEqualTo(3);
        assertThat(queue2).containsExactly(3, 4, 5, 6);

        RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("{queue}1");
        Integer value1 = queue3.pollLastAndOfferFirstTo(queue2.getName(), 1, TimeUnit.SECONDS);
        assertThat(value1).isNull();
    }
    
    @Test
    public void testTakeLastAndOfferFirstTo() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("{queue}1");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                queue1.put(3);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 3, TimeUnit.SECONDS);

        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("{queue}2");
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
    public void testDrainToSingle() {
        RBlockingQueue<Integer> queue = getQueue();
        Assertions.assertTrue(queue.add(1));
        Assertions.assertEquals(1, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        int count = queue.drainTo(batch);
        Assertions.assertEquals(1, count);
        Assertions.assertEquals(1, batch.size());
        Assertions.assertTrue(queue.isEmpty());
    }
    
    @Test
    public void testDrainTo() {
        RBlockingQueue<Integer> queue = getQueue();
        for (int i = 0 ; i < 100; i++) {
            queue.offer(i);
        }
        Assertions.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        int count = queue.drainTo(batch, 10);
        Assertions.assertEquals(10, count);
        Assertions.assertEquals(10, batch.size());
        Assertions.assertEquals(90, queue.size());
        queue.drainTo(batch, 10);
        queue.drainTo(batch, 20);
        queue.drainTo(batch, 60);
        Assertions.assertEquals(0, queue.size());
    }

    @Test
    public void testDrainToCollection() throws Exception {
        RBlockingQueue<Object> queue1 = getQueue();
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst);
        assertThat(dst).containsExactly(1, 2L, "e");
        Assertions.assertEquals(0, queue1.size());
    }

    @Test
    public void testDrainToCollectionLimited() throws Exception {
        RBlockingQueue<Object> queue1 = getQueue();
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst, 2);
        assertThat(dst).containsExactly(1, 2L);
        Assertions.assertEquals(1, queue1.size());

        dst.clear();
        queue1.drainTo(dst, 2);
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
                RBlockingQueue<String> q = getQueue(String.valueOf(i));
                q.add(value);
                System.out.println("Message added to [" + i + "]");
                q.expire(Duration.ofMinutes(1));
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
    public void testSubscribeOnElements() throws InterruptedException {
        RBlockingQueue<Integer> q = redisson.getBlockingQueue("test");
        Set<Integer> values = new HashSet<>();
        int listnerId = q.subscribeOnElements(v -> {
            values.add(v);
        });

        for (int i = 0; i < 10; i++) {
            q.add(i);
        }

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> {
            return values.size() == 10;
        });

        q.unsubscribe(listnerId);

        q.add(11);
        q.add(12);

        Thread.sleep(1000);

        assertThat(values).hasSize(10);
    }

}
