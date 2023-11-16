package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RIdGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonIdGeneratorTest extends RedisDockerTest {

    @Test
    public void testEmpty() {
        RIdGenerator generator = redisson.getIdGenerator("test");
        for (int i = 1; i <= 100103; i++) {
            assertThat(generator.nextId()).isEqualTo(i);
        }
    }

    @Test
    public void testInit() {
        RIdGenerator generator = redisson.getIdGenerator("test");
        assertThat(generator.tryInit(12, 2931)).isTrue();
        assertThat(generator.tryInit(0, 1000)).isFalse();

        for (int i = 12; i <= 5000; i++) {
            assertThat(generator.nextId()).isEqualTo(i);
        }
    }

}
