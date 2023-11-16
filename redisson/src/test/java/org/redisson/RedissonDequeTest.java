package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RDeque;
import org.redisson.api.queue.DequeMoveArgs;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonDequeTest extends RedisDockerTest {

    @Test
    public void testAddIfExists() {
        RDeque<Integer> deque1 = redisson.getDeque("deque1");
        deque1.add(1);
        deque1.add(2);
        deque1.add(3);

        deque1.addFirstIfExists(4, 5);

        assertThat(deque1).containsExactly(5, 4, 1, 2, 3);
    }

    @Test
    public void testMove() {
        RDeque<Integer> deque1 = redisson.getDeque("deque1");
        RDeque<Integer> deque2 = redisson.getDeque("deque2");

        deque1.add(1);
        deque1.add(2);
        deque1.add(3);

        deque2.add(4);
        deque2.add(5);
        deque2.add(6);

        Integer r1 = deque1.move(DequeMoveArgs.pollFirst().addLastTo(deque2.getName()));
        assertThat(r1).isEqualTo(1);

        assertThat(deque1).containsExactly(2, 3);
        assertThat(deque2).containsExactly(4, 5, 6, 1);

        Integer r2 = deque2.move(DequeMoveArgs.pollLast().addFirstTo(deque1.getName()));
        assertThat(r2).isEqualTo(1);

        assertThat(deque1).containsExactly(1, 2, 3);
        assertThat(deque2).containsExactly(4, 5, 6);
    }

    @Test
    public void testRemoveLastOccurrence() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        queue1.addFirst(3);
        queue1.addFirst(1);
        queue1.addFirst(2);
        queue1.addFirst(3);

        queue1.removeLastOccurrence(3);

        assertThat(queue1).containsExactly(3, 2, 1);
    }

    @Test
    public void testRemoveFirstOccurrence() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        queue1.addFirst(3);
        queue1.addFirst(1);
        queue1.addFirst(2);
        queue1.addFirst(3);

        queue1.removeFirstOccurrence(3);

        assertThat(queue1).containsExactly(2, 1, 3);
    }

    @Test
    public void testRemoveLast() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        queue1.addFirst(1);
        queue1.addFirst(2);
        queue1.addFirst(3);

        Assertions.assertEquals(1, (int)queue1.removeLast());
        Assertions.assertEquals(2, (int)queue1.removeLast());
        Assertions.assertEquals(3, (int)queue1.removeLast());
    }

    @Test
    public void testRemoveFirst() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        queue1.addFirst(1);
        queue1.addFirst(2);
        queue1.addFirst(3);

        Assertions.assertEquals(3, (int)queue1.removeFirst());
        Assertions.assertEquals(2, (int)queue1.removeFirst());
        Assertions.assertEquals(1, (int)queue1.removeFirst());
    }

    @Test
    public void testPeek() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        Assertions.assertNull(queue1.peekFirst());
        Assertions.assertNull(queue1.peekLast());
        queue1.addFirst(2);
        Assertions.assertEquals(2, (int)queue1.peekFirst());
        Assertions.assertEquals(2, (int)queue1.peekLast());
    }

    @Test
    public void testPollLastAndOfferFirstTo() {
        RDeque<Integer> queue1 = redisson.getDeque("deque1");
        queue1.addFirst(3);
        queue1.addFirst(2);
        queue1.addFirst(1);

        RDeque<Integer> queue2 = redisson.getDeque("deque2");
        queue2.addFirst(6);
        queue2.addFirst(5);
        queue2.addFirst(4);

        queue1.pollLastAndOfferFirstTo(queue2.getName());
        assertThat(queue2).containsExactly(3, 4, 5, 6);
    }

    @Test
    public void testAddFirstOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.addFirst(1);
        queue.addFirst(2);
        queue.addFirst(3);

        assertThat(queue).containsExactly(3, 2, 1);
   }

    @Test
    public void testAddFirstLastMulti() {
        RDeque<Integer> queue = redisson.getDeque("deque");
        queue.addAll(Arrays.asList(1, 2, 3, 4));
        queue.addFirst(0, 1, 0);
        queue.addLast(10, 20, 10);

        assertThat(queue).containsExactly(0, 1, 0, 1, 2, 3, 4, 10, 20, 10);
    }

    @Test
    public void testAddFirst() {
        RDeque<Integer> queue = redisson.getDeque("deque");
        queue.addFirst(1);
        queue.addFirst(2);
        queue.addFirst(3);

        assertThat(queue).containsExactly(3, 2, 1);
    }

    @Test
    public void testAddLastOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.addLast(1);
        queue.addLast(2);
        queue.addLast(3);

        assertThat(queue).containsExactly(1, 2, 3);
    }

    @Test
    public void testAddLast() {
        RDeque<Integer> queue = redisson.getDeque("deque");
        queue.addLast(1);
        queue.addLast(2);
        queue.addLast(3);

        assertThat(queue).containsExactly(1, 2, 3);
    }

    @Test
    public void testOfferFirstOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.offerFirst(1);
        queue.offerFirst(2);
        queue.offerFirst(3);

        assertThat(queue).containsExactly(3, 2, 1);
    }

    @Test
    public void testOfferFirst() {
        RDeque<Integer> queue = redisson.getDeque("deque");
        queue.offerFirst(1);
        queue.offerFirst(2);
        queue.offerFirst(3);

        assertThat(queue).containsExactly(3, 2, 1);
    }

    @Test
    public void testOfferLastOrigin() {
        Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.offerLast(1);
        queue.offerLast(2);
        queue.offerLast(3);

        assertThat(queue).containsExactly(1, 2, 3);

        Assertions.assertEquals((Integer)1, queue.poll());
    }

    @Test
    public void testDescendingIteratorOrigin() {
        final Deque<Integer> queue = new ArrayDeque<Integer>();
        queue.addAll(Arrays.asList(1, 2, 3));

        assertThat(queue.descendingIterator()).toIterable().containsExactly(3, 2, 1);
    }

    @Test
    public void testDescendingIterator() {
        final RDeque<Integer> queue = redisson.getDeque("deque");
        queue.addAll(Arrays.asList(1, 2, 3));

        assertThat(queue.descendingIterator()).toIterable().containsExactly(3, 2, 1);
    }

}
