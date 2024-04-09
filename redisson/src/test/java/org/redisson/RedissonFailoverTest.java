package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonFailoverTest extends RedisDockerTest {

    @Test
    public void testFailoverInCluster() {
        withNewCluster((nodes, redisson) -> {
            List<ContainerState> masters = getMasterNodes(nodes);

            List<RFuture<?>> futures = new ArrayList<>();
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
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            stop(masters.get(0));
            System.out.println("master " + masters.get(0).getFirstMappedPort() + " has been stopped!");

            try {
                TimeUnit.SECONDS.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            List<ContainerState> newMasters = getMasterNodes(nodes);
            newMasters.removeAll(masters);
            assertThat(newMasters).hasSize(1);

            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            stop(newMasters.get(0));
            System.out.println("new master " + newMasters.get(0).getFirstMappedPort() + " has been stopped!");

            try {
                assertThat(latch.await(180, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

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

            assertThat(readonlyErrors).isZero();
            assertThat(errors).isLessThan(2900);
            assertThat(success).isGreaterThan(3000);
        });
    }

    @Test
    public void testFailoverInClusterSlave() {
        withNewCluster((nodes, redisson) -> {
            ContainerState slave = getSlaveNodes(nodes).get(0);

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
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            slave.getDockerClient().pauseContainerCmd(slave.getContainerId()).exec();
            System.out.println("slave " + slave.getFirstMappedPort() + " has been stopped!");
            try {
                TimeUnit.SECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            slave.getDockerClient().unpauseContainerCmd(slave.getContainerId()).exec();

            try {
                assertThat(latch.await(70, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int errors = 0;
            int success = 0;
            int readonlyErrors = 0;

            for (RFuture<?> rFuture : futures) {
                try {
                    rFuture.toCompletableFuture().join();
                    success++;
                } catch (Exception e) {
                    errors++;
                    // skip
                }
            }

            assertThat(readonlyErrors).isZero();
            assertThat(errors).isBetween(15, 200);
            assertThat(success).isGreaterThan(600 - 200);
            assertThat(futures.get(futures.size() - 1).isDone()).isTrue();
            assertThat(futures.get(futures.size() - 1).toCompletableFuture().isCompletedExceptionally()).isFalse();
        });
    }

    @Test
    public void testFailoverInSentinel() throws Exception {
        withSentinel((nodes, config) -> {
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
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            GenericContainer master = nodes.get(0);
            master.setPortBindings(Arrays.asList(master.getFirstMappedPort() + ":" + master.getExposedPorts().get(0)));
            master.stop();
            System.out.println("master has been stopped!");

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(70));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            master.start();
            System.out.println("master has been started!");


            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            try {
                assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

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
            assertThat(errors).isBetween(100, 820);
            assertThat(readonlyErrors).isZero();

            redisson.shutdown();
        }, 2);
    }

    @Test
    public void testFailoverWithoutErrorsInCluster() {
        withNewCluster((nodes, redissonClient) -> {
            Config config = redissonClient.getConfig();
            config.useClusterServers()
                    .setRetryAttempts(30)
                    .setReadMode(ReadMode.MASTER)
                    .setSubscriptionMode(SubscriptionMode.MASTER);
            RedissonClient redisson = Redisson.create(config);

            List<ContainerState> masters = getMasterNodes(nodes);

            Set<InetSocketAddress> oldMasters = new HashSet<>();
            Collection<RedisClusterMaster> masterNodes = redisson.getRedisNodes(RedisNodes.CLUSTER).getMasters();
            for (RedisClusterMaster clusterNode : masterNodes) {
                oldMasters.add(clusterNode.getAddr());
            }

            stop(masters.get(0));

            List<RFuture<?>> futures = new ArrayList<>();
            for (int j = 0; j < 2000; j++) {
                RFuture<?> f2 = redisson.getBucket("" + j).setAsync("");
                futures.add(f2);
            }

            System.out.println("master " + masters.get(0).getFirstMappedPort() + " has been stopped!");

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(40));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            List<ContainerState> newMasters = getMasterNodes(nodes);
            newMasters.removeAll(masters);
            assertThat(newMasters).hasSize(1);

            for (RFuture<?> rFuture : futures) {
                rFuture.toCompletableFuture().join();
            }

            redisson.shutdown();
        });
    }


}
