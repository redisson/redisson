package org.redisson;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RQueue;

public class RedissonQueueTest extends BaseTest {

    @Test
    public void testAddOfferOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
        Assert.assertEquals((Integer)1, queue.poll());
        MatcherAssert.assertThat(queue, Matchers.contains(2, 3, 4));
        Assert.assertEquals((Integer)2, queue.element());
    }

    @Test
    public void testAddOffer() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
        Assert.assertEquals((Integer)1, queue.poll());
        MatcherAssert.assertThat(queue, Matchers.contains(2, 3, 4));
        Assert.assertEquals((Integer)2, queue.element());
    }

    @Test
    public void testRemoveOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        MatcherAssert.assertThat(queue, Matchers.contains(3, 4));
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testRemove() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        MatcherAssert.assertThat(queue, Matchers.contains(3, 4));
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test(expected = NoSuchElementException.class)
    public void testRemoveEmpty() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.remove();
    }

}
