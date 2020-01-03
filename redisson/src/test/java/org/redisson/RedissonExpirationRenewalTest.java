package org.redisson;

import org.junit.Test;
import org.redisson.api.RLock;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonExpirationRenewalTest extends BaseTest {

    private static final String LOCK_KEY = "LOCK_KEY";

    @Test
    public void testExpirationRenewal() throws IOException, InterruptedException {
        redisson.getConfig().setLockWatchdogTimeout(3_000L);

        {
            RLock lock = redisson.getLock(LOCK_KEY);
            lock.lock();
            try {
                Thread.sleep(5_000L);
                int port = RedisRunner.defaultRedisInstance.getRedisServerPort();
                // force expiration renewal error
                RedisRunner.shutDownDefaultRedisServerInstance();
                RedisRunner.defaultRedisInstance = new RedisRunner().nosave().randomDir().port(port).run();
                Thread.sleep(10_000L);
            } finally {
                assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
            }
        }

        {
            RLock lock = redisson.getLock(LOCK_KEY);
            lock.lock();
            try {
                // wait for timeout
                Thread.sleep(30_000L);
            } finally {
                lock.unlock();
            }
        }
    }

}
