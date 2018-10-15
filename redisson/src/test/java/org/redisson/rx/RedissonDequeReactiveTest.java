package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RDequeRx;

public class RedissonDequeReactiveTest extends BaseRxTest {

    @Test
    public void testRemoveLastOccurrence() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        sync(queue1.removeLastOccurrence(3));

        assertThat(sync(queue1)).containsExactly(3, 2, 1);
    }

    @Test
    public void testRemoveFirstOccurrence() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        sync(queue1.removeFirstOccurrence(3));

        assertThat(sync(queue1)).containsExactly(2, 1, 3);
    }

    @Test
    public void testRemoveLast() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        Assert.assertEquals(1, (int)sync(queue1.removeLast()));
        Assert.assertEquals(2, (int)sync(queue1.removeLast()));
        Assert.assertEquals(3, (int)sync(queue1.removeLast()));
    }

    @Test
    public void testRemoveFirst() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        Assert.assertEquals(3, (int)sync(queue1.removeFirst()));
        Assert.assertEquals(2, (int)sync(queue1.removeFirst()));
        Assert.assertEquals(1, (int)sync(queue1.removeFirst()));
    }

    @Test
    public void testPeek() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        Assert.assertNull(sync(queue1.peekFirst()));
        Assert.assertNull(sync(queue1.peekLast()));
        sync(queue1.addFirst(2));
        Assert.assertEquals(2, (int)sync(queue1.peekFirst()));
        Assert.assertEquals(2, (int)sync(queue1.peekLast()));
    }

    @Test
    public void testPollLastAndOfferFirstTo() {
        RDequeRx<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(1));

        RDequeRx<Integer> queue2 = redisson.getDeque("deque2");
        sync(queue2.addFirst(6));
        sync(queue2.addFirst(5));
        sync(queue2.addFirst(4));

        sync(queue1.pollLastAndOfferFirstTo(queue2.getName()));
        assertThat(sync(queue2)).containsExactly(3, 4, 5, 6);
    }

    @Test
    public void testAddFirst() {
        RDequeRx<Integer> queue = redisson.getDeque("deque");
        sync(queue.addFirst(1));
        sync(queue.addFirst(2));
        sync(queue.addFirst(3));

        assertThat(sync(queue)).containsExactly(3, 2, 1);
    }

    @Test
    public void testAddLast() {
        RDequeRx<Integer> queue = redisson.getDeque("deque");
        sync(queue.addLast(1));
        sync(queue.addLast(2));
        sync(queue.addLast(3));

        assertThat(sync(queue)).containsExactly(1, 2, 3);
    }

    @Test
    public void testOfferFirst() {
        RDequeRx<Integer> queue = redisson.getDeque("deque");
        sync(queue.offerFirst(1));
        sync(queue.offerFirst(2));
        sync(queue.offerFirst(3));

        assertThat(sync(queue)).containsExactly(3, 2, 1);
    }

    @Test
    public void testDescendingIterator() {
        final RDequeRx<Integer> queue = redisson.getDeque("deque");
        sync(queue.addAll(Arrays.asList(1, 2, 3)));

        assertThat(toIterator(queue.descendingIterator())).containsExactly(3, 2, 1);
    }

}
