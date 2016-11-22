package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.rule.TestUtil.sync;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RQueueReactive;

public class RedissonQueueReactiveTest extends AbstractBaseTest {

    @Test
    public void testAddOffer() {
        RQueueReactive<Integer> queue = redissonRule.getSharedReactiveClient().getQueue("queue");
        sync(queue.add(1));
        sync(queue.offer(2));
        sync(queue.add(3));
        sync(queue.offer(4));

        assertThat(sync(queue)).containsExactly(1, 2, 3, 4);
        Assert.assertEquals((Integer)1, sync(queue.poll()));
        assertThat(sync(queue)).containsExactly(2, 3, 4);
        Assert.assertEquals((Integer)2, sync(queue.peek()));
    }

    @Test
    public void testRemove() {
        RQueueReactive<Integer> queue = redissonRule.getSharedReactiveClient().getQueue("queue");
        sync(queue.add(1));
        sync(queue.add(2));
        sync(queue.add(3));
        sync(queue.add(4));

        sync(queue.poll());
        sync(queue.poll());

        assertThat(sync(queue)).containsExactly(3, 4);
        sync(queue.poll());
        sync(queue.poll());

        Assert.assertEquals(0, sync(queue.size()).intValue());
    }

    @Test
    public void testRemoveEmpty() {
        RQueueReactive<Integer> queue = redissonRule.getSharedReactiveClient().getQueue("queue");
        Assert.assertNull(sync(queue.poll()));
    }

}
