package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

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
        
        Assert.assertFalse(f.await(1, TimeUnit.SECONDS));
        runner.stop();

        long start = System.currentTimeMillis();
        assertThat(f.get()).isNull();
        assertThat(System.currentTimeMillis() - start).isGreaterThan(3800);
        
        redisson.shutdown();
    }
    
    @Test(timeout = 3000)
    public void testShortPoll() throws InterruptedException {
        RBlockingQueue<Integer> queue = getQueue();
        queue.poll(500, TimeUnit.MILLISECONDS);
        queue.poll(10, TimeUnit.MICROSECONDS);
    }
    
    @Test
    public void testPollReattach() throws InterruptedException, IOException, ExecutionException, TimeoutException {
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
        
        await().atMost(7, TimeUnit.SECONDS).until(() -> executed.get());
        
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
        f.await(1, TimeUnit.SECONDS);
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
        
        RBlockingQueue<Integer> queue1 = getQueue(redisson);
        RFuture<Integer> f = queue1.takeAsync();
        f.await(1, TimeUnit.SECONDS);
        
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
        
        queue1.put(1);
        assertThat(f.get()).isEqualTo(1);
        
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
        f.await(1, TimeUnit.SECONDS);
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

        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
            RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
            try {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assert.assertEquals(2, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
        
        redisson.shutdown();
        process.shutdown();
    }
    
    @Test
    public void testPollFromAny() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
            RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
            try {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assert.assertEquals(2, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
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

        Assert.assertEquals(3, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);
        Assert.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));

        long s = System.currentTimeMillis();
        Assert.assertNull(queue1.poll(5, TimeUnit.SECONDS));
        Assert.assertTrue(System.currentTimeMillis() - s > 4900);
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = getQueue();
        queue1.put(1);

        Assert.assertEquals((Integer)1, queue1.poll(10, TimeUnit.SECONDS));
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
        Assert.assertTrue(queue.add(1));
        Assert.assertEquals(1, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        int count = queue.drainTo(batch);
        Assert.assertEquals(1, count);
        Assert.assertEquals(1, batch.size());
        Assert.assertTrue(queue.isEmpty());
    }
    
    @Test
    public void testDrainTo() {
        RBlockingQueue<Integer> queue = getQueue();
        for (int i = 0 ; i < 100; i++) {
            queue.offer(i);
        }
        Assert.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        int count = queue.drainTo(batch, 10);
        Assert.assertEquals(10, count);
        Assert.assertEquals(10, batch.size());
        Assert.assertEquals(90, queue.size());
        queue.drainTo(batch, 10);
        queue.drainTo(batch, 20);
        queue.drainTo(batch, 60);
        Assert.assertEquals(0, queue.size());
    }

    @Test
    public void testBlockingQueue() {

        RBlockingQueue<Integer> queue = getQueue();

        ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        int total = 100;
        for (int i = 0; i < total; i++) {
            // runnable won't be executed in any particular order, and hence, int value as well.
            executor.submit(() -> {
                getQueue().add(counter.incrementAndGet());
            });
        }
        int count = 0;
        while (count < total) {
            try {
                // blocking
                int item = queue.take();
                assertThat(item > 0 && item <= total).isTrue();
            } catch (InterruptedException exception) {
                Assert.fail();
            }
            count++;
        }

        assertThat(counter.get()).isEqualTo(total);
        queue.delete();
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
        Assert.assertEquals(0, queue1.size());
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
        Assert.assertEquals(1, queue1.size());

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
                q.expire(1, TimeUnit.MINUTES);
                System.out.println("Expiry set to [" + i + "]");
                String poll = q.poll(1, TimeUnit.SECONDS);
                System.out.println("Message polled from [" + i + "]" + poll);
                Assert.assertEquals(value, poll);
            }
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }
}
