package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLockReactive;

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

}
