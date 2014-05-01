package org.redisson;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RDeque;

public class RedissonDequeTest extends BaseTest {

    @Test
    public void testOfferFirstOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.offerFirst(1);
        queue.offerFirst(2);
        queue.offerFirst(3);

        MatcherAssert.assertThat(queue, Matchers.contains(3, 2, 1));
    }

    @Test
    public void testOfferFirst() {
        RDeque<Integer> queue = redisson.getDeque("deque");
        queue.offerFirst(1);
        queue.offerFirst(2);
        queue.offerFirst(3);

        MatcherAssert.assertThat(queue, Matchers.contains(3, 2, 1));
    }

    @Test
    public void testOfferLastOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.offerLast(1);
        queue.offerLast(2);
        queue.offerLast(3);

        MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3));

        Assert.assertEquals((Integer)1, queue.poll());
    }

    @Test
    public void testDescendingIteratorOrigin() {
        final Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.addAll(Arrays.asList(1, 2, 3));

        MatcherAssert.assertThat(new Iterable<Integer>() {

            @Override
            public Iterator<Integer> iterator() {
                return queue.descendingIterator();
            }

        }, Matchers.contains(3, 2, 1));
    }

    @Test
    public void testDescendingIterator() {
        final RDeque<Integer> queue = redisson.getDeque("deque");
        queue.addAll(Arrays.asList(1, 2, 3));

        MatcherAssert.assertThat(new Iterable<Integer>() {

            @Override
            public Iterator<Integer> iterator() {
                return queue.descendingIterator();
            }

        }, Matchers.contains(3, 2, 1));
    }

}
