package org.redisson;

import org.junit.Test;
import org.redisson.core.RCountDownLatch;

public class RedissonCountDownLatchTest {

    @Test
    public void testCountDown() throws InterruptedException {
        Redisson redisson = Redisson.create();
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(1);
        latch.countDown();
        latch.await();
        latch.countDown();
        latch.await();
        latch.countDown();
        latch.await();

        RCountDownLatch latch1 = redisson.getCountDownLatch("latch1");
        latch1.trySetCount(1);
        latch1.countDown();
        latch1.countDown();
        latch1.await();

        RCountDownLatch latch2 = redisson.getCountDownLatch("latch2");
        latch2.trySetCount(1);
        latch2.countDown();
        latch2.await();
        latch2.await();

        RCountDownLatch latch3 = redisson.getCountDownLatch("latch3");
        latch3.await();

        RCountDownLatch latch4 = redisson.getCountDownLatch("latch4");
        latch4.countDown();
        latch4.await();

        redisson.shutdown();
    }

}
