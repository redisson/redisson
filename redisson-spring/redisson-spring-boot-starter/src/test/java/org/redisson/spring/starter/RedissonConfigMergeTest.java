package org.redisson.spring.starter;

import org.junit.jupiter.api.Test;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Redisson-specific settings defined with <code>spring.redis.redisson.config</code>
 * are merged on top of the Redis settings defined with Spring Boot properties
 * when <code>spring.redis.redisson.merge</code> is enabled.
 */
public class RedissonConfigMergeTest {

    private final AtomicReference<Config> redissonConfig = new AtomicReference<>();

    private static final SslBundles SSL_BUNDLES = new SslBundles() {

        @Override
        public SslBundle getBundle(String name) {
            return SslBundle.of(SslStoreBundle.NONE, SslBundleKey.NONE,
                                SslOptions.of(new String[] { "TLS_AES_128_GCM_SHA256" }, new String[] { "TLSv1.3" }),
                                SslBundle.DEFAULT_PROTOCOL);
        }

        @Override
        public void addBundleUpdateHandler(String name, Consumer<SslBundle> handler) {
        }

        @Override
        public void addBundleRegisterHandler(BiConsumer<String, SslBundle> handler) {
        }

        @Override
        public List<String> getBundleNames() {
            return Collections.singletonList("test-bundle");
        }

    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedissonAutoConfigurationV4.class,
                                                        DataRedisAutoConfiguration.class))
            .withBean(RedissonAutoConfigurationCustomizer.class, () -> redissonConfig::set);

    @Test
    public void testSpringPropertiesMergedIntoRedissonConfig() {
        contextRunner.withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6380",
                "spring.data.redis.database=4",
                "spring.redis.redisson.merge=true",
                "spring.data.redis.client-name=merged-client",
                "spring.data.redis.timeout=5s",
                "spring.data.redis.connect-timeout=7s",
                "spring.redis.redisson.config=lazyInitialization: true\nnettyThreads: 8\n")
            .run(context -> {
                assertThat(context).hasNotFailed();

                SingleServerConfig server = redissonConfig.get().useSingleServer();
                assertThat(server.getAddress()).isEqualTo("redis://127.0.0.1:6380");
                assertThat(server.getDatabase()).isEqualTo(4);
                assertThat(server.getClientName()).isEqualTo("merged-client");
                assertThat(server.getTimeout()).isEqualTo(5000);
                assertThat(server.getConnectTimeout()).isEqualTo(7000);

                Config config = redissonConfig.get();
                assertThat(config.isLazyInitialization()).isTrue();
                assertThat(config.getNettyThreads()).isEqualTo(8);
            });
    }

    @Test
    public void testUrlPropertyMergedIntoRedissonConfig() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.url=redis://127.0.0.1:6379/6",
                "spring.data.redis.database=6",
                "spring.redis.redisson.config=lazyInitialization: true\n")
            .run(context -> {
                assertThat(context).hasNotFailed();

                SingleServerConfig server = redissonConfig.get().useSingleServer();
                assertThat(server.getAddress()).isEqualTo("redis://127.0.0.1:6379");
                assertThat(server.getDatabase()).isEqualTo(6);
            });
    }

    @Test
    public void testRedissonSingleConfigOverridesSpringProperties() {
        contextRunner.withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6380",
                "spring.data.redis.database=4",
                "spring.redis.redisson.merge=true",
                "spring.data.redis.timeout=5s",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "singleServerConfig:\n"
                        + "  address: \"redis://127.0.0.1:6390\"\n"
                        + "  database: 3\n"
                        + "  connectionMinimumIdleSize: 4\n"
                        + "  connectionPoolSize: 8\n")
            .run(context -> {
                assertThat(context).hasNotFailed();

                SingleServerConfig server = redissonConfig.get().useSingleServer();
                assertThat(server.getAddress()).isEqualTo("redis://127.0.0.1:6390");
                assertThat(server.getDatabase()).isEqualTo(3);
                assertThat(server.getConnectionMinimumIdleSize()).isEqualTo(4);
                assertThat(server.getConnectionPoolSize()).isEqualTo(8);
                assertThat(server.getTimeout()).isEqualTo(5000);
            });
    }

    @Test
    public void testRedissonSentinelConfigOverridesSpringProperties() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.sentinel.master=spring-master",
                "spring.data.redis.sentinel.nodes=127.0.0.1:26379,127.0.0.1:26380",
                "spring.data.redis.sentinel.username=spring-sentinel-user",
                "spring.data.redis.sentinel.password=spring-sentinel-pass",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "sentinelServersConfig:\n"
                        + "  masterName: yaml-master\n"
                        + "  sentinelAddresses:\n"
                        + "    - \"redis://127.0.0.1:26381\"\n"
                        + "  sentinelUsername: yaml-sentinel-user\n"
                        + "  sentinelPassword: yaml-sentinel-pass\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    SentinelServersConfig server = config.useSentinelServers();
                    assertThat(server.getMasterName()).isEqualTo("yaml-master");
                    assertThat(server.getSentinelAddresses()).containsExactly("redis://127.0.0.1:26381");
                    assertThat(server.getSentinelUsername()).isEqualTo("yaml-sentinel-user");
                    assertThat(server.getSentinelPassword()).isEqualTo("yaml-sentinel-pass");
                    assertThat(config.isLazyInitialization()).isTrue();
                });
    }

    @Test
    public void testRedissonClusterConfigOverridesSpringProperties() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001",
                "spring.data.redis.timeout=5s",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "clusterServersConfig:\n"
                        + "  nodeAddresses:\n"
                        + "    - \"redis://127.0.0.1:7002\"\n"
                        + "  slaveConnectionMinimumIdleSize: 5\n"
                        + "  slaveConnectionPoolSize: 10\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ClusterServersConfig server = redissonConfig.get().useClusterServers();
                    assertThat(server.getNodeAddresses()).containsExactly("redis://127.0.0.1:7002");
                    assertThat(server.getSlaveConnectionMinimumIdleSize()).isEqualTo(5);
                    assertThat(server.getSlaveConnectionPoolSize()).isEqualTo(10);
                    assertThat(server.getTimeout()).isEqualTo(5000);
                });
    }

    @Test
    public void testSentinelPropertiesMergedIntoRedissonConfig() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.sentinel.master=mymaster",
                "spring.data.redis.sentinel.nodes=127.0.0.1:26379,127.0.0.1:26380",
                "spring.redis.redisson.config=lazyInitialization: true\n")
            .run(context -> {
                assertThat(context).hasNotFailed();

                Config config = redissonConfig.get();
                SentinelServersConfig server = config.useSentinelServers();
                assertThat(server.getMasterName()).isEqualTo("mymaster");
                assertThat(server.getSentinelAddresses()).containsExactly("redis://127.0.0.1:26379",
                                                                            "redis://127.0.0.1:26380");
                assertThat(config.isLazyInitialization()).isTrue();
            });
    }

    @Test
    public void testSinglePropertiesMergedIntoRedissonConfig() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.host=10.0.0.1",
                "spring.data.redis.port=6381",
                "spring.redis.redisson.config=lazyInitialization: true\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://10.0.0.1:6381");
                    assertThat(config.isLazyInitialization()).isTrue();
                });
    }

    @Test
    public void testClusterPropertiesMergedIntoRedissonConfig() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002",
                "spring.data.redis.timeout=5s",
                "spring.redis.redisson.config=lazyInitialization: true\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    ClusterServersConfig server = config.useClusterServers();
                    assertThat(server.getNodeAddresses()).containsExactly("redis://127.0.0.1:7000",
                                                                          "redis://127.0.0.1:7001",
                                                                          "redis://127.0.0.1:7002");
                    assertThat(server.getTimeout()).isEqualTo(5000);
                    assertThat(config.isLazyInitialization()).isTrue();
                });
    }

    @Test
    public void testRedissonSentinelTopologyReplacesSpringTopology() {
        contextRunner.withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.redis.redisson.merge=true",
                "spring.data.redis.port=6380",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "sentinelServersConfig:\n"
                        + "  masterName: mymaster\n"
                        + "  sentinelAddresses:\n"
                        + "    - \"redis://127.0.0.1:26379\"\n")
            .run(context -> {
                assertThat(context).hasNotFailed();

                Config config = redissonConfig.get();
                assertThat(config.useSentinelServers().getMasterName()).isEqualTo("mymaster");
                assertThat(new ConfigSupport().toYAML(config)).doesNotContain("singleServerConfig");
            });
    }

    @Test
    public void testRedissonClusterTopologyReplacesSpringTopology() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.sentinel.master=spring-master",
                "spring.data.redis.sentinel.nodes=127.0.0.1:26379",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "clusterServersConfig:\n"
                        + "  nodeAddresses:\n"
                        + "    - \"redis://127.0.0.1:7000\"\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    assertThat(config.useClusterServers().getNodeAddresses()).containsExactly("redis://127.0.0.1:7000");
                    assertThat(new ConfigSupport().toYAML(config)).doesNotContain("sentinelServersConfig");
                });
    }

    @Test
    public void testRedissonSingleTopologyReplacesSpringTopology() {
        contextRunner.withPropertyValues(
                "spring.redis.redisson.merge=true",
                "spring.data.redis.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001",
                "spring.redis.redisson.config="
                        + "lazyInitialization: true\n"
                        + "singleServerConfig:\n"
                        + "  address: \"redis://127.0.0.1:6379\"\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://127.0.0.1:6379");
                    assertThat(new ConfigSupport().toYAML(config)).doesNotContain("clusterServersConfig");
                });
    }

    @Test
    public void testMergeDisabledByDefault() {
        contextRunner.withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.redis.redisson.config=lazyInitialization: true\n")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("server(s) address(es) not defined!");
            });
    }


    @Test
    public void testSpringSslBundleAppliedToRedissonConfig() {
        contextRunner.withBean(SslBundles.class, () -> SSL_BUNDLES)
                .withPropertyValues(
                        "spring.redis.redisson.merge=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.ssl.enabled=true",
                        "spring.data.redis.ssl.bundle=test-bundle",
                        "spring.redis.redisson.config=lazyInitialization: true\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    assertThat(config.useSingleServer().getAddress()).isEqualTo("rediss://127.0.0.1:6379");
                    assertThat(config.getSslCiphers()).containsExactly("TLS_AES_128_GCM_SHA256");
                    assertThat(config.getSslProtocols()).containsExactly("TLSv1.3");
                });
    }

    @Test
    public void testYamlSslSettingsReplaceSpringSslBundle() {
        contextRunner.withBean(SslBundles.class, () -> SSL_BUNDLES)
                .withPropertyValues(
                        "spring.redis.redisson.merge=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.ssl.enabled=true",
                        "spring.data.redis.ssl.bundle=test-bundle",
                        "spring.redis.redisson.config="
                                + "lazyInitialization: true\n"
                                + "sslKeystoreType: PKCS12\n")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    Config config = redissonConfig.get();
                    assertThat(config.getSslKeystoreType()).isEqualTo("PKCS12");
                    assertThat(config.getSslCiphers()).isNull();
                    assertThat(config.getSslProtocols()).isNull();
                });
    }
    
}
