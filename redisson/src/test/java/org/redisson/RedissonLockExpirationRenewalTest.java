package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonLockExpirationRenewalTest extends RedisDockerTest {

    private static final String LOCK_KEY = "LOCK_KEY";
    public static final long LOCK_WATCHDOG_TIMEOUT = 1_000L;

    @Test
    public void testExpirationRenewalIsWorkingAfterTimeout() throws InterruptedException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config c = createConfig(redis);
        c.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        RedissonClient redisson = Redisson.create(c);

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.lock();
        try {
            // force expiration renewal error
            restart(redis);
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
        }

        RLock lock2 = redisson.getLock(LOCK_KEY);
        lock2.lock();
        try {
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            lock2.unlock();
        }

        redisson.shutdown();
        redis.stop();
    }

}
