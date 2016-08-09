package org.redisson;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RQueueReactive;

public class RedissonQueueReactiveTest extends BaseReactiveTest {

    @Test
    public void testAddOffer() {
        RQueueReactive<Integer> queue = redisson.getQueue("queue");
        sync(queue.add(1));
        sync(queue.offer(2));
        sync(queue.add(3));
        sync(queue.offer(4));

        MatcherAssert.assertThat(sync(queue), Matchers.contains(1, 2, 3, 4));
        Assert.assertEquals((Integer)1, sync(queue.poll()));
        MatcherAssert.assertThat(sync(queue), Matchers.contains(2, 3, 4));
        Assert.assertEquals((Integer)2, sync(queue.peek()));
    }

    @Test
    public void testRemove() {
        RQueueReactive<Integer> queue = redisson.getQueue("queue");
        sync(queue.add(1));
        sync(queue.add(2));
        sync(queue.add(3));
        sync(queue.add(4));

        sync(queue.poll());
        sync(queue.poll());

        MatcherAssert.assertThat(sync(queue), Matchers.contains(3, 4));
        sync(queue.poll());
        sync(queue.poll());

        Assert.assertEquals(0, sync(queue.size()).intValue());
    }

    @Test
    public void testRemoveEmpty() {
        RQueueReactive<Integer> queue = redisson.getQueue("queue");
        Assert.assertNull(sync(queue.poll()));
    }

}
