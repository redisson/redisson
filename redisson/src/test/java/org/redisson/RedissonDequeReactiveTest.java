package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RDequeReactive;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonDequeReactiveTest extends BaseReactiveTest {

    @Test
    public void testRemoveLastOccurrence() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        sync(queue1.removeLastOccurrence(3));

        assertThat(sync(queue1)).containsExactly(3, 2, 1);
    }

    @Test
    public void testRemoveFirstOccurrence() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        sync(queue1.removeFirstOccurrence(3));

        assertThat(sync(queue1)).containsExactly(2, 1, 3);
    }

    @Test
    public void testRemoveLast() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        Assertions.assertEquals(1, (int)sync(queue1.removeLast()));
        Assertions.assertEquals(2, (int)sync(queue1.removeLast()));
        Assertions.assertEquals(3, (int)sync(queue1.removeLast()));
    }

    @Test
    public void testRemoveFirst() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(1));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(3));

        Assertions.assertEquals(3, (int)sync(queue1.removeFirst()));
        Assertions.assertEquals(2, (int)sync(queue1.removeFirst()));
        Assertions.assertEquals(1, (int)sync(queue1.removeFirst()));
    }

    @Test
    public void testPeek() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        Assertions.assertNull(sync(queue1.peekFirst()));
        Assertions.assertNull(sync(queue1.peekLast()));
        sync(queue1.addFirst(2));
        Assertions.assertEquals(2, (int)sync(queue1.peekFirst()));
        Assertions.assertEquals(2, (int)sync(queue1.peekLast()));
    }

    @Test
    public void testPollLastAndOfferFirstTo() {
        RDequeReactive<Integer> queue1 = redisson.getDeque("deque1");
        sync(queue1.addFirst(3));
        sync(queue1.addFirst(2));
        sync(queue1.addFirst(1));

        RDequeReactive<Integer> queue2 = redisson.getDeque("deque2");
        sync(queue2.addFirst(6));
        sync(queue2.addFirst(5));
        sync(queue2.addFirst(4));

        sync(queue1.pollLastAndOfferFirstTo(queue2.getName()));
        assertThat(sync(queue2)).containsExactly(3, 4, 5, 6);
    }

    @Test
    public void testAddFirst() {
        RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.addFirst(1));
        sync(queue.addFirst(2));
        sync(queue.addFirst(3));

        assertThat(sync(queue)).containsExactly(3, 2, 1);
    }

    @Test
    public void testAddLast() {
        RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.addLast(1));
        sync(queue.addLast(2));
        sync(queue.addLast(3));

        assertThat(sync(queue)).containsExactly(1, 2, 3);
    }

    @Test
    public void testOfferFirst() {
        RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.offerFirst(1));
        sync(queue.offerFirst(2));
        sync(queue.offerFirst(3));

        assertThat(sync(queue)).containsExactly(3, 2, 1);
    }

    @Test
    public void testDescendingIterator() {
        final RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.addAll(Arrays.asList(1, 2, 3)));

        assertThat(toIterator(queue.descendingIterator())).toIterable().containsExactly(3, 2, 1);
    }

    @Test
    public void testPollLast() {
        final RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.addAll(Arrays.asList(1, 2, 3)));

        assertThat(toIterator(queue.pollLast(2))).toIterable().containsExactly(3, 2);
    }

    @Test
    public void testPollFirst() {
        final RDequeReactive<Integer> queue = redisson.getDeque("deque");
        sync(queue.addAll(Arrays.asList(1, 2, 3)));

        assertThat(toIterator(queue.pollFirst(2))).toIterable().containsExactly(1, 2);
    }


}
