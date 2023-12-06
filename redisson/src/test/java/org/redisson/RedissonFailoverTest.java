package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

public class RedissonFailoverTest {

    @Test
    public void testFailoverInCluster() throws Exception {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();
        RedisRunner slave4 = new RedisRunner().port(6903).randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1, slave4)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(7000);

        Config config = new Config();
        config.useClusterServers()
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisRunner.RedisProcess master = process.getNodes().stream().filter(x -> x.getRedisServerPort() == master1.getPort()).findFirst().get();

        List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < 2000; i++) {
                    RFuture<?> f1 = redisson.getBucket("i" + i).getAsync();
                    RFuture<?> f2 = redisson.getBucket("i" + i).setAsync("");
                    RFuture<?> f3 = redisson.getTopic("topic").publishAsync("testmsg");
                    futures.add(f1);
                    futures.add(f2);
                    futures.add(f3);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    if (i % 100 == 0) {
                        System.out.println("step: " + i);
                    }
                }
                latch.countDown();
            };
        };
        t.start();
        t.join(1000);

        Set<InetSocketAddress> oldMasters = new HashSet<>();
        Collection<RedisClusterMaster> masterNodes = redisson.getRedisNodes(org.redisson.api.redisnode.RedisNodes.CLUSTER).getMasters();
        for (RedisClusterMaster clusterNode : masterNodes) {
            oldMasters.add(clusterNode.getAddr());
        }

        master.stop();
        System.out.println("master " + master.getRedisServerAddressAndPort() + " has been stopped!");

        Thread.sleep(TimeUnit.SECONDS.toMillis(90));

        RedisRunner.RedisProcess newMaster = null;
        Collection<RedisClusterMaster> newMasterNodes = redisson.getRedisNodes(RedisNodes.CLUSTER).getMasters();
        for (RedisClusterMaster clusterNode : newMasterNodes) {
            if (!oldMasters.contains(clusterNode.getAddr())) {
                newMaster = process.getNodes().stream().filter(x -> x.getRedisServerPort() == clusterNode.getAddr().getPort()).findFirst().get();
                break;
            }
        }

        assertThat(newMaster).isNotNull();

        Thread.sleep(30000);

        newMaster.stop();

        System.out.println("new master " + newMaster.getRedisServerAddressAndPort() + " has been stopped!");

        Thread.sleep(TimeUnit.SECONDS.toMillis(80));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

        int errors = 0;
        int success = 0;
        int readonlyErrors = 0;

        for (RFuture<?> rFuture : futures) {
            try {
                rFuture.toCompletableFuture().join();
                success++;
            } catch (Exception e) {
                errors++;
            }
        }

        redisson.shutdown();
        process.shutdown();

        assertThat(readonlyErrors).isZero();
        assertThat(errors).isLessThan(1000);
        assertThat(success).isGreaterThan(5000);
    }

    @Test
    public void testFailoverInClusterSlave() throws Exception {
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
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(7000);

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisRunner.RedisProcess slave = process.getNodes().stream().filter(x -> x.getRedisServerPort() == slave1.getPort()).findFirst().get();

        List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < 600; i++) {
                    RFuture<?> f1 = redisson.getBucket("i" + i).getAsync();
                    futures.add(f1);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    if (i % 100 == 0) {
                        System.out.println("step: " + i);
                    }
                }
                latch.countDown();
            };
        };
        t.start();
        t.join(1000);

        slave.restart(20);
        System.out.println("slave " + slave.getRedisServerAddressAndPort() + " has been stopped!");

        assertThat(latch.await(70, TimeUnit.SECONDS)).isTrue();

        int errors = 0;
        int success = 0;
        int readonlyErrors = 0;

        for (RFuture<?> rFuture : futures) {
            try {
                rFuture.toCompletableFuture().join();
                success++;
            } catch (Exception e) {
                e.printStackTrace();
                errors++;
                // skip
            }
        }

        redisson.shutdown();
        process.shutdown();

        assertThat(readonlyErrors).isZero();
        assertThat(errors).isLessThan(200);
        assertThat(success).isGreaterThan(600 - 200);
        assertThat(futures.get(futures.size() - 1).isDone()).isTrue();
        assertThat(futures.get(futures.size() - 1).toCompletableFuture().isCompletedExceptionally()).isFalse();
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

        List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < 1000; i++) {
                    RFuture<?> f1 = redisson.getBucket("i" + i).getAsync();
                    RFuture<?> f2 = redisson.getBucket("i" + i).setAsync("");
                    RFuture<?> f3 = redisson.getTopic("topic").publishAsync("testmsg");
                    futures.add(f1);
                    futures.add(f2);
                    futures.add(f3);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                latch.countDown();
            };
        };
        t.start();
        t.join(1000);

        master.stop();
        System.out.println("master " + master.getRedisServerAddressAndPort() + " stopped!");

        Thread.sleep(TimeUnit.SECONDS.toMillis(70));

        master = new RedisRunner()
                .port(master.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();

        System.out.println("master " + master.getRedisServerAddressAndPort() + " started!");


        Thread.sleep(15000);

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

        int errors = 0;
        int success = 0;
        int readonlyErrors = 0;

        for (RFuture<?> rFuture : futures) {
            try {
                rFuture.toCompletableFuture().join();
                success++;
            } catch (CompletionException e) {
                if (e.getCause().getMessage().contains("READONLY You can't write against")) {
                    readonlyErrors++;
                }
                errors++;
                // skip
            }
        }

        System.out.println("errors " + errors + " success " + success + " readonly " + readonlyErrors);

        assertThat(futures.get(futures.size() - 1).isDone()).isTrue();
        assertThat(futures.get(futures.size() - 1).toCompletableFuture().isCompletedExceptionally()).isFalse();
        assertThat(errors).isLessThan(820);
        assertThat(readonlyErrors).isZero();

        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    @Test
    public void testFailoverWithoutErrorsInCluster() throws Exception {
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
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setRetryAttempts(30)
        .setReadMode(ReadMode.MASTER)
        .setSubscriptionMode(SubscriptionMode.MASTER)
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisRunner.RedisProcess master = process.getNodes().stream().filter(x -> x.getRedisServerPort() == master1.getPort()).findFirst().get();

        List<RFuture<?>> futures = new ArrayList<RFuture<?>>();

        Set<InetSocketAddress> oldMasters = new HashSet<>();
        Collection<RedisClusterMaster> masterNodes = redisson.getRedisNodes(RedisNodes.CLUSTER).getMasters();
        for (RedisClusterMaster clusterNode : masterNodes) {
            oldMasters.add(clusterNode.getAddr());
        }

        master.stop();

        for (int j = 0; j < 2000; j++) {
            RFuture<?> f2 = redisson.getBucket("" + j).setAsync("");
            futures.add(f2);
        }

        System.out.println("master " + master.getRedisServerAddressAndPort() + " has been stopped!");

        Thread.sleep(TimeUnit.SECONDS.toMillis(40));

        RedisRunner.RedisProcess newMaster = null;
        Collection<RedisClusterMaster> newMasterNodes = redisson.getRedisNodes(RedisNodes.CLUSTER).getMasters();
        for (RedisClusterMaster clusterNode : newMasterNodes) {
            if (!oldMasters.contains(clusterNode.getAddr())) {
                newMaster = process.getNodes().stream().filter(x -> x.getRedisServerPort() == clusterNode.getAddr().getPort()).findFirst().get();
                break;
            }
        }

        assertThat(newMaster).isNotNull();

        for (RFuture<?> rFuture : futures) {
            try {
                rFuture.toCompletableFuture().join();
            } catch (Exception e) {
                Assertions.fail(e.getMessage());
            }
        }

        redisson.shutdown();
        process.shutdown();
    }


}
