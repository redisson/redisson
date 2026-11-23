package org.redisson.jnosql;

import org.eclipse.jnosql.communication.CommunicationException;
import org.eclipse.jnosql.communication.Settings;
import org.eclipse.jnosql.communication.keyvalue.KeyValueConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonKeyValueConfigurationTest extends BaseTest {

    @Test
    public void testServiceLoader() {
        KeyValueConfiguration configuration = KeyValueConfiguration.getConfiguration();
        RedissonKeyValueConfiguration typed = KeyValueConfiguration.getConfiguration(RedissonKeyValueConfiguration.class);

        assertThat(configuration).isInstanceOf(RedissonKeyValueConfiguration.class);
        assertThat(typed).isNotNull();
    }

    @Test
    public void testProperties() {
        Settings settings = Settings.builder()
                .put("jnosql.redisson.singleServerConfig.address", address())
                .put("jnosql.redisson.single-server-config.connection-pool-size", 8)
                .put("jnosql.redisson.single-server-config.connection-minimum-idle-size", 2)
                .put("jnosql.redisson.threads", 4)
                .build();

        try (RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(settings)) {
            factory.apply("books").put("1", "a");
        }

        assertThat(redisson.getBucket("books:1", StringCodec.INSTANCE).get()).isEqualTo("\"a\"");
    }

    @Test
    public void testClasspathFile() {
        System.setProperty("redisson.jnosql.test.address", address());
        Settings settings = Settings.builder()
                .put(RedissonConfigurations.FILE, "classpath:redisson-jnosql.yaml")
                .build();
        Settings leadingSlash = Settings.builder()
                .put(RedissonConfigurations.FILE, "classpath:/redisson-jnosql.yaml")
                .build();

        try (RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(settings);
                RedissonBucketManagerFactory other = new RedissonKeyValueConfiguration().apply(leadingSlash)) {
            factory.apply("books").put("1", "a");
            other.apply("books").put("2", "b");
        } finally {
            System.clearProperty("redisson.jnosql.test.address");
        }

        assertThat(redisson.getBucket("books:1", StringCodec.INSTANCE).get()).isEqualTo("\"a\"");
        assertThat(redisson.getBucket("books:2", StringCodec.INSTANCE).get()).isEqualTo("\"b\"");
    }

    @Test
    public void testFile(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("redisson.yaml"),
                "singleServerConfig:\n  address: \"" + address() + "\"\n");
        Settings settings = Settings.builder()
                .put(RedissonConfigurations.FILE, file.toString())
                .build();

        try (RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(settings)) {
            factory.apply("books").put("1", "a");
        }

        assertThat(redisson.getBucket("books:1", StringCodec.INSTANCE).get()).isEqualTo("\"a\"");
    }

    @Test
    public void testMissingFile(@TempDir Path dir) {
        Settings classpath = Settings.builder().put(RedissonConfigurations.FILE, "classpath:missing.yaml").build();
        Settings path = Settings.builder().put(RedissonConfigurations.FILE, dir.resolve("missing.yaml").toString()).build();

        assertThatThrownBy(() -> new RedissonKeyValueConfiguration().apply(classpath))
                .isInstanceOf(CommunicationException.class)
                .hasMessageContaining("classpath:missing.yaml");
        assertThatThrownBy(() -> new RedissonKeyValueConfiguration().apply(path))
                .isInstanceOf(CommunicationException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    public void testFileAndProperties() {
        Settings settings = Settings.builder()
                .put(RedissonConfigurations.FILE, "classpath:redisson-jnosql.yaml")
                .put("jnosql.redisson.threads", 4)
                .build();

        assertThatThrownBy(() -> new RedissonKeyValueConfiguration().apply(settings))
                .isInstanceOf(CommunicationException.class)
                .hasMessageContaining("jnosql.redisson.threads");
    }

    @Test
    public void testEmptyProperty() {
        Settings delegate = Settings.builder()
                .put("jnosql.redisson.singleServerConfig.address", address())
                .put("jnosql.redisson.threads", "")
                .build();
        Settings settings = (Settings) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Settings.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("get") && "jnosql.redisson.threads".equals(args[0])) {
                        return Optional.empty();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        assertThatThrownBy(() -> new RedissonKeyValueConfiguration().apply(settings))
                .isInstanceOf(CommunicationException.class)
                .hasMessageContaining("jnosql.redisson.threads");
    }

    @Test
    public void testConfig() {
        try (RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(config())) {
            factory.apply("books").put("1", "a");
        }

        assertThat(redisson.getBucket("books:1", StringCodec.INSTANCE).get()).isEqualTo("\"a\"");
    }

    @Test
    public void testNullArguments() {
        RedissonKeyValueConfiguration configuration = new RedissonKeyValueConfiguration();

        assertThatNullPointerException().isThrownBy(() -> configuration.apply((Settings) null));
        assertThatNullPointerException().isThrownBy(() -> configuration.apply((Config) null));
        assertThatNullPointerException().isThrownBy(() -> configuration.apply((RedissonClient) null));
    }

}
