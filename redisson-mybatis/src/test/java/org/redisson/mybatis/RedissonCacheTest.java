package org.redisson.mybatis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
public class RedissonCacheTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                        .withFixedExposedPort(6379, 6379);


    private RedissonCache cache;

    @BeforeEach
    public void before() {
        cache = new RedissonCache("redisson");
        cache.setTimeToLive(1000);
        cache.setRedissonConfig("redisson.yaml");
    }

    @Test
    public void testPut() throws InterruptedException {
        cache.putObject("1", "2");
        assertThat(cache.getObject("1")).isEqualTo("2");
        assertThat(cache.getSize()).isEqualTo(1);
        Thread.sleep(1000);
        assertThat(cache.getObject("1")).isNull();
    }

}
