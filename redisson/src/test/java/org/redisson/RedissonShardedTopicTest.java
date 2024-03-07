package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RShardedTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;
import org.redisson.api.redisnode.RedisCluster;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonShardedTopicTest extends RedisDockerTest {

    @Test
    public void testInvalidCommand() {
        GenericContainer<?> redis = createRedis("6.2");
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
        withNewCluster(redissonClient -> {
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
            topic.addListener(Integer.class, new MessageListener<Integer>() {
                @Override
                public void onMessage(CharSequence channel, Integer msg) {
                    executed.set(true);
                }
            });

            RedisCluster rnc = redisson.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterMaster master : rnc.getMasters()) {
                RedisClientConfig cc = new RedisClientConfig();
                cc.setAddress("redis://" + master.getAddr().getHostString() + ":" + master.getAddr().getPort());
                RedisClient c = RedisClient.create(cc);
                RedisConnection cn = c.connect();
                List<String> channels = cn.sync(RedisCommands.PUBSUB_SHARDCHANNELS);
                if (channels.contains("3")) {
                    cn.async(RedisCommands.SHUTDOWN);
                }
                c.shutdown();
            }

            Awaitility.waitAtMost(Duration.ofSeconds(30)).until(() -> subscriptions.get() == 2);

            redisson.getShardedTopic("3").publish(1);
            assertThat(executed.get()).isTrue();

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
