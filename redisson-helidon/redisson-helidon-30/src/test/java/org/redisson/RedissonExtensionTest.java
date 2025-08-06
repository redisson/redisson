package org.redisson;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
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
@ApplicationScoped
@Testcontainers
public class RedissonExtensionTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
            .withFixedExposedPort(6379, 6379);

    @BeforeEach
    void startCdiContainer() {
        System.setProperty("org.redisson.Redisson.simple.singleServerConfig.address", "redis://127.0.0.1:6379");
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.initialize();
    }

    private void onInit(@Observes @Initialized(ApplicationScoped.class) Object event,
                           @Named("simple") RedissonClient client) {
        assertThat(client).isNotNull();
    }

    @Test
    public void test() {
        Instance<RedissonClient> instance = CDI.current().select(RedissonClient.class, NamedLiteral.of("simple"));
        RedissonClient redissonClient = instance.get();

        RBucket<String> b = redissonClient.getBucket("test");
        b.set("1");
        assertThat(b.get()).isEqualTo("1");
        assertThat(b.delete()).isTrue();
    }


}
