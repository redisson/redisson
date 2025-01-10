package org.redisson;

import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.redisson.api.DeletedObjectListener;
import org.redisson.api.ExpiredObjectListener;
import org.redisson.api.RLockRx;
import org.redisson.rx.BaseRxTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLockRxTest extends BaseRxTest {

    @Test
    public void testMultiLock() {
        RLockRx l1 = redisson.getLock("test1");
        RLockRx l2 = redisson.getLock("test2");

        RLockRx m = redisson.getMultiLock(l1, l2);
        sync(m.lock());
        assertThat(sync(l1.isLocked())).isTrue();
        assertThat(sync(l2.isLocked())).isTrue();
    }

    @Test
    public void testIsHeldByThread() {
        String lockName = "lock1";
        RLockRx lock1 = redisson.getLock(lockName);
        RLockRx lock2 = redisson.getLock(lockName);

        int threadId1 = 1;
        Single<Boolean> lockMono1 = lock1.tryLock(threadId1);
        int threadId2 = 2;
        Single<Boolean> lockMono2 = lock1.tryLock(threadId2);

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
    public void testLockRxListener() {
        testWithParams(redisson -> {
            RLockRx lock1 = redisson.rxJava().getLock("lock1");

            CountDownLatch latch = new CountDownLatch(4);
            Single<Integer> listener1 = lock1.addListener(new ExpiredObjectListener() {
                @Override
                public void onExpired(String name) {
                    latch.countDown();
                }
            });
            Single<Integer> listener2 = lock1.addListener(new DeletedObjectListener() {
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
