package org.redisson.misc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SpinLockTest {

    @Test
    public void test() throws InterruptedException {
        SpinLock l = new SpinLock();
        CountDownLatch latch = new CountDownLatch(1);
        l.execute(() -> {
            try {
                l.execute(() -> {
                    latch.countDown();
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        latch.await(2, TimeUnit.SECONDS);
    }

}
