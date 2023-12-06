package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RFencedLock;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonFencedLockTest extends RedisDockerTest {

    @Test
    public void testTokenIncrease() {
        RFencedLock lock = redisson.getFencedLock("lock");
        Long token1 = lock.lockAndGetToken();
        assertThat(token1).isEqualTo(1);
        lock.unlock();
        assertThat(token1).isEqualTo(1);

        Long token2 = lock.lockAndGetToken();
        assertThat(token2).isEqualTo(2);
        lock.unlock();

        lock.lock();
        assertThat(lock.getToken()).isEqualTo(3);
        lock.unlock();

        lock.lock(10, TimeUnit.SECONDS);
        assertThat(lock.getToken()).isEqualTo(4);
        lock.unlock();

        Long token4 = lock.tryLockAndGetToken();
        assertThat(token4).isEqualTo(5);
    }

}
