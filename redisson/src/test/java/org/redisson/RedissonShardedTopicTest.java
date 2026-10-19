package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RShardedTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonShardedTopicTest extends RedisDockerTest {

    @Test
    public void testInvalidCommand() {
        GenericContainer<?> redis = createContainerByImage("redis:6.2");
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        RShardedTopic t = redisson.getShardedTopic("ttt");
        RedisException e = Assertions.assertThrows(RedisException.class, () -> {
            t.addListener(String.class, (channel, msg) -> {
            });
        });
        assertThat(e.getMessage()).contains("ERR unknown command `SSUBSCRIBE`");

        redisson.shutdown();
        redis.stop();
    }

    @Test
    public void testReattachInClusterMaster() {
        withNewCluster((nodes, redissonClient) -> {
            Config cfg = redissonClient.getConfig();
            cfg.useClusterServers()
                    .setPingConnectionInterval(0)
                    .setSubscriptionMode(SubscriptionMode.MASTER);

            RedissonClient redisson = Redisson.create(cfg);
            final AtomicBoolean executed = new AtomicBoolean();
            final AtomicInteger subscriptions = new AtomicInteger();

            RTopic topic = redisson.getShardedTopic("3");
            topic.addListener(new StatusListener() {

                @Override
                public void onUnsubscribe(String channel) {
                }

                @Override
                public void onSubscribe(String channel) {
                    subscriptions.incrementAndGet();
                }
            });
            topic.addListener(Integer.class, (channel, msg) -> executed.set(true));

            assertThat(topic.countSubscribers()).isEqualTo(1);

            List<ContainerState> masters = getMasterNodes(nodes);
            for (ContainerState master : masters) {
                String r = execute(master, "redis-cli", "pubsub", "shardchannels");
                if (r.contains("3")) {
                    stop(master);
                    break;
                }
            }

            Awaitility.waitAtMost(Duration.ofSeconds(30)).until(() -> subscriptions.get() == 2);

            redisson.getShardedTopic("3").publish(1);

            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> {
                assertThat(executed.get()).isTrue();
            });

            redisson.shutdown();
        });
    }

    @Test
    public void testSlotMigrationInCluster() {
        withNewCluster((nodes, client) -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setScanInterval(1000)
                    .setSubscriptionMode(SubscriptionMode.MASTER);

            RedissonClient redisson = Redisson.create(config);

            RedisClientConfig cfg = new RedisClientConfig();
            cfg.setAddress(config.useClusterServers().getNodeAddresses().get(0));
            RedisClient c = RedisClient.create(cfg);
            RedisConnection cc = c.connect();
            List<ClusterNodeInfo> mastersList = cc.sync(RedisCommands.REDIS_CLUSTER_NODES);
            mastersList = mastersList.stream().filter(i -> i.containsFlag(ClusterNodeInfo.Flag.MASTER)).collect(Collectors.toList());
            c.shutdown();

            int slot = 10922;

            ClusterNodeInfo destination = mastersList.stream().filter(i -> !i.getSlotRanges().isEmpty() &&
                                    !i.getSlotRanges().iterator().next().hasSlot(slot)).findAny().get();
            ClusterNodeInfo source = mastersList.stream().filter(i -> !i.getSlotRanges().isEmpty() &&
                                    i.getSlotRanges().iterator().next().hasSlot(slot)).findAny().get();

            RedisClientConfig sourceCfg = new RedisClientConfig();
            sourceCfg.setAddress(config.useClusterServers().getNatMapper().map(source.getAddress()));
            RedisClient sourceClient = RedisClient.create(sourceCfg);
            RedisConnection sourceConnection = sourceClient.connect();

            RedisClientConfig destinationCfg = new RedisClientConfig();
            destinationCfg.setAddress(config.useClusterServers().getNatMapper().map(destination.getAddress()));
            RedisClient destinationClient = RedisClient.create(destinationCfg);
            RedisConnection destinationConnection = destinationClient.connect();

            AtomicReference<String> reference = new AtomicReference<>();
            String channelName = "test{kaO}";
            RTopic topic = redisson.getShardedTopic(channelName);
            topic.addListener(String.class, (ch, m) -> {
                reference.set(m);
            });

            assertThat(destinationConnection.sync(RedisCommands.PUBSUB_SHARDCHANNELS)).isEmpty();
            assertThat(sourceConnection.sync(RedisCommands.PUBSUB_SHARDCHANNELS)).containsOnly(channelName);

            destinationConnection.sync(RedisCommands.CLUSTER_SETSLOT, slot, "IMPORTING", source.getNodeId());
            sourceConnection.sync(RedisCommands.CLUSTER_SETSLOT, slot, "MIGRATING", destination.getNodeId());

            for (ClusterNodeInfo node : mastersList) {
                if (node.getSlotRanges().isEmpty()) {
                    continue;
                }
                RedisClientConfig cc1 = new RedisClientConfig();
                cc1.setAddress(config.useClusterServers().getNatMapper().map(node.getAddress()));
                RedisClient ccc = RedisClient.create(cc1);
                RedisConnection connection = ccc.connect();
                connection.sync(RedisCommands.CLUSTER_SETSLOT, slot,
                        "NODE", destination.getNodeId());
                ccc.shutdownAsync();
            }

            Awaitility.waitAtMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(destinationConnection.sync(RedisCommands.PUBSUB_SHARDCHANNELS)).containsOnly(channelName);
            });
            assertThat(sourceConnection.sync(RedisCommands.PUBSUB_SHARDCHANNELS)).isEmpty();

            topic.publish("mymessage");
            Awaitility.waitAtMost(Duration.ofSeconds(1)).until(() -> "mymessage".equals(reference.get()));

            sourceClient.shutdown();
            destinationClient.shutdown();
            redisson.shutdown();
        });
    }

    @Test
    public void testClusterSharding() {
        testInCluster(redisson -> {
            AtomicInteger counter = new AtomicInteger();
            for (int i = 0; i < 10; i++) {
                int j = i;
                RTopic topic = redisson.getShardedTopic("test" + i);
                topic.addListener(Integer.class, (c, v) -> {
                    assertThat(v).isEqualTo(j);
                    counter.incrementAndGet();
                });
            }

            for (int i = 0; i < 10; i++) {
                RTopic topic = redisson.getShardedTopic("test" + i);
                long s = topic.publish(i);
                assertThat(s).isEqualTo(1);
            }

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> counter.get() == 10);

            for (int i = 0; i < 10; i++) {
                RTopic topic = redisson.getShardedTopic("test" + i);
                topic.removeAllListeners();
            }
        });
    }


}
