package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

public class RedissonConnectionFactoryTest extends BaseConnectionTest {

    @Test
    public void testFactoryConfig() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(createConfig());
        AtomicLong counter = new AtomicLong();
        factory.getReactiveConnection().ping().subscribe(p -> counter.incrementAndGet());
        Awaitility.await().atMost(Duration.ONE_SECOND)
                .until(() -> counter.get() == 1);
    }

}
