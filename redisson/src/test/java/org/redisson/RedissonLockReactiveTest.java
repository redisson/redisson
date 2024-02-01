package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLockReactive;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLockReactiveTest extends BaseReactiveTest {

    private static final int MAX_DURATION = 5;

    @Test
    public void testMultiLock() {
        RLockReactive l1 = redisson.getLock("test1");
        RLockReactive l2 = redisson.getLock("test2");

        RLockReactive m = redisson.getMultiLock(l1, l2);
        sync(m.lock());
        assertThat(sync(l1.isLocked())).isTrue();
        assertThat(sync(l2.isLocked())).isTrue();
    }

    @Test
    void testIsHeldByThread() {
        String lockName = "lock1";
        RLockReactive lock1 = redisson.getLock(lockName);
        RLockReactive lock2 = redisson.getLock(lockName);

        int threadId1 = 1;
        Mono<Boolean> lockMono1 = lock1.tryLock(threadId1);
        int threadId2 = 2;
        Mono<Boolean> lockMono2 = lock1.tryLock(threadId2);

        assertThat(lockMono1.block(Duration.ofSeconds(MAX_DURATION))).isTrue();
        assertThat(lock1.isHeldByThread(threadId1).block(Duration.ofSeconds(MAX_DURATION))).isTrue();

        lock1.unlock(threadId1).block(Duration.ofSeconds(MAX_DURATION));
        assertThat(lock1.isHeldByThread(threadId1).block(Duration.ofSeconds(MAX_DURATION))).isFalse();
        assertThat(lockMono2.block(Duration.ofSeconds(MAX_DURATION))).isTrue();
        assertThat(lock2.isHeldByThread(threadId2).block(Duration.ofSeconds(MAX_DURATION))).isTrue();

        lock2.unlock(threadId2).block(Duration.ofSeconds(MAX_DURATION));
        assertThat(lock1.isHeldByThread(threadId1).block(Duration.ofSeconds(MAX_DURATION))).isFalse();
        assertThat(lock2.isHeldByThread(threadId2).block(Duration.ofSeconds(MAX_DURATION))).isFalse();
    }
}
