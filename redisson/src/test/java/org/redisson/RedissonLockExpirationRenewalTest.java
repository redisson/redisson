package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonLockExpirationRenewalTest {

    private static final String LOCK_KEY = "LOCK_KEY";
    public static final long LOCK_WATCHDOG_TIMEOUT = 1_000L;

    private RedissonClient redisson;

    @BeforeEach
    public void before() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        redisson = createInstance();
    }

    @AfterEach
    public void after() throws InterruptedException {
        redisson.shutdown();
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @Test
    public void testExpirationRenewalIsWorkingAfterTimeout() throws IOException, InterruptedException {
        {
            RLock lock = redisson.getLock(LOCK_KEY);
            lock.lock();
            try {
                // force expiration renewal error
                restartRedisServer();
                // wait for timeout
                Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
            } finally {
                assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
            }
        }

        {
            RLock lock = redisson.getLock(LOCK_KEY);
            lock.lock();
            try {
                // wait for timeout
                Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
            } finally {
                lock.unlock();
            }
        }
    }

    private void restartRedisServer() throws InterruptedException, IOException {
        int currentPort = RedisRunner.defaultRedisInstance.getRedisServerPort();
        RedisRunner.shutDownDefaultRedisServerInstance();
        RedisRunner.defaultRedisInstance = new RedisRunner().nosave().randomDir().port(currentPort).run();
    }

    public static Config createConfig() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        config.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        return config;
    }

    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }
}
