package org.redisson.helidon;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.service.registry.ServiceRegistryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Testcontainers
public class RedissonFactoryTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                        .withFixedExposedPort(6379, 6379);

    private ServiceRegistryManager registryManager;

    @BeforeEach
    void startServiceRegistry() {
        System.setProperty("org.redisson.Redisson.simple.singleServerConfig.address", "redis://127.0.0.1:6379");
        registryManager = ServiceRegistryManager.create();
    }

    @AfterEach
    void stopServiceRegistry() {
        registryManager.shutdown();
    }

    @Test
    public void test() {
        RedissonClient redissonClient = registryManager.registry().getNamed(RedissonClient.class, "simple");

        RBucket<String> b = redissonClient.getBucket("test");
        b.set("1");
        assertThat(b.get()).isEqualTo("1");
        assertThat(b.delete()).isTrue();
    }

    @Test
    public void testInjection() {
        InjectedClients clients = registryManager.registry().get(InjectedClients.class);

        assertThat(clients.simpleClient()).isSameAs(registryManager.registry().getNamed(RedissonClient.class, "simple"));
        assertThat(clients.defaultClient()).isSameAs(registryManager.registry().getNamed(RedissonClient.class, "default"));

        RBucket<String> b = clients.simpleClient().getBucket("test");
        b.set("1");
        assertThat(clients.defaultClient().getBucket("test").isExists()).isFalse();
        assertThat(b.delete()).isTrue();
    }

    @Test
    public void testShutdown() {
        RedissonClient redissonClient = registryManager.registry().getNamed(RedissonClient.class, "simple");

        registryManager.shutdown();
        assertThat(redissonClient.isShutdown()).isTrue();
    }

    @Test
    public void testConfig() {
        Config config = Config.just("""
                org.redisson.Redisson:
                  cluster:
                    threads: 16
                    codec: "org.redisson.client.codec.StringCodec"
                    clusterServersConfig:
                      scanInterval: 2000
                      nodeAddresses:
                        - "redis://127.0.0.1:7000"
                        - "redis://127.0.0.1:7001"
                """, MediaTypes.APPLICATION_YAML);

        org.redisson.config.Config redissonConfig = RedissonFactory.toRedissonConfig(config.get("org.redisson.Redisson.cluster"));
        assertThat(redissonConfig.getThreads()).isEqualTo(16);
        assertThat(redissonConfig.getCodec()).isInstanceOf(StringCodec.class);
        assertThat(redissonConfig.useClusterServers().getScanInterval()).isEqualTo(2000);
        assertThat(redissonConfig.useClusterServers().getNodeAddresses())
                .containsExactly("redis://127.0.0.1:7000", "redis://127.0.0.1:7001");
    }

}
