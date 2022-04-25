package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLockRx;
import org.redisson.rx.BaseRxTest;

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

}
