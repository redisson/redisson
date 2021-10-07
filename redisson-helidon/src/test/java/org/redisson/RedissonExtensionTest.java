package org.redisson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ApplicationScoped
public class RedissonExtensionTest {

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
