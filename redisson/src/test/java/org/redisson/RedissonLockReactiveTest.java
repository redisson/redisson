package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.DeletedObjectListener;
import org.redisson.api.ExpiredObjectListener;
import org.redisson.api.RLockReactive;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLockReactiveTest extends BaseReactiveTest {

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
    public void testIsHeldByThread() {
        String lockName = "lock1";
        RLockReactive lock1 = redisson.getLock(lockName);
        RLockReactive lock2 = redisson.getLock(lockName);

        int threadId1 = 1;
        Mono<Boolean> lockMono1 = lock1.tryLock(threadId1);
        int threadId2 = 2;
        Mono<Boolean> lockMono2 = lock1.tryLock(threadId2);

        assertThat(sync(lockMono1)).isTrue();
        assertThat(sync(lock1.isHeldByThread(threadId1))).isTrue();

        sync(lock1.unlock(threadId1));
        assertThat(sync(lock1.isHeldByThread(threadId1))).isFalse();
        assertThat(sync(lockMono2)).isTrue();
        assertThat(sync(lock2.isHeldByThread(threadId2))).isTrue();

        sync(lock2.unlock(threadId2));
        assertThat(sync(lock1.isHeldByThread(threadId1))).isFalse();
        assertThat(sync(lock2.isHeldByThread(threadId2))).isFalse();
    }

    @Test
    public void testLockReactiveListener() {
        testWithParams(redisson -> {
            RLockReactive lock1 = redisson.reactive().getLock("lock1");

            CountDownLatch latch = new CountDownLatch(4);
            Mono<Integer> listener1 = lock1.addListener(new ExpiredObjectListener() {
                @Override
                public void onExpired(String name) {
                    latch.countDown();
                }
            });
            Mono<Integer> listener2 = lock1.addListener(new DeletedObjectListener() {
                @Override
                public void onDeleted(String name) {
                    latch.countDown();
                }
            });
            int listenerId1 = sync(listener1);
            int listenerId2 = sync(listener2);

            sync(lock1.lock(5, TimeUnit.SECONDS));
            sync(lock1.unlock());
            assertThat(latch.getCount()).isEqualTo(3);

            try {
                sync(lock1.lock(5, TimeUnit.SECONDS));
                Thread.sleep(6000);
                assertThat(latch.getCount()).isEqualTo(2);

                sync(lock1.removeListener(listenerId1));
                sync(lock1.lock(5, TimeUnit.SECONDS));
                Thread.sleep(5100);
                assertThat(latch.getCount()).isEqualTo(2);

                sync(lock1.lock(5, TimeUnit.SECONDS));
                sync(lock1.unlock());
                assertThat(sync(lock1.isLocked())).isFalse();
                assertThat(latch.getCount()).isEqualTo(1);

                sync(lock1.removeListener(listenerId2));
                sync(lock1.lock(5, TimeUnit.SECONDS));
                sync(lock1.unlock());
                assertThat(latch.getCount()).isEqualTo(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }, NOTIFY_KEYSPACE_EVENTS, "Egx");
    }
}
