package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RQueue;
import org.redisson.api.queue.QueueMoveElementsArgs;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonQueueTest extends RedisDockerTest {

    <T> RQueue<T> getQueue() {
        return redisson.getQueue("queue");
    }

    @Test
    public void testPollLimited() {
        RQueue<Integer> queue = getQueue();
        queue.addAll(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        List<Integer> elements = queue.poll(3);
        assertThat(elements).containsExactly(1, 2, 3);
        List<Integer> elements2 = queue.poll(10);
        assertThat(elements2).containsExactly(4, 5, 6, 7);
        List<Integer> elements3 = queue.poll(5);
        assertThat(elements3).isEmpty();
    }

    @Test
    public void testMoveCount() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2, 3, 4, 5));
        destination.addAll(Arrays.asList(8, 9));

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .count(2));

        assertThat(elements).containsExactly(1, 2);
        assertThat(queue).containsExactly(3, 4, 5);
        assertThat(destination).containsExactly(8, 9, 1, 2);
    }

    @Test
    public void testMoveEmptyQueue() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .count(3));

        assertThat(elements).isEmpty();
        assertThat(destination).isEmpty();
    }

    @Test
    public void testMoveExactly() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2, 3, 4));

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .exactly(3));

        assertThat(elements).containsExactly(1, 2, 3);
        assertThat(queue).containsExactly(4);
        assertThat(destination).containsExactly(1, 2, 3);
    }

    @Test
    public void testMoveSingleElement() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2, 3));

        // no amount defined - a single element is moved
        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName()));

        assertThat(elements).containsExactly(1);
        assertThat(queue).containsExactly(2, 3);
        assertThat(destination).containsExactly(1);
    }

    @Test
    public void testMoveSingleElementFromEmptyQueue() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName()));

        assertThat(elements).isEmpty();
        assertThat(destination).isEmpty();
    }

    @Test
    public void testMoveBulk() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2, 3, 4));

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .count(3)
                                                                 .bulk());

        assertThat(elements).containsExactly(1, 2, 3);
        assertThat(queue).containsExactly(4);
        assertThat(destination).containsExactly(1, 2, 3);
    }

    @Test
    public void testMoveBulkAndOneByOneProduceSameResult() {
        RQueue<Integer> bulkSource = redisson.getQueue("bulkSource");
        RQueue<Integer> oboSource = redisson.getQueue("oboSource");
        RQueue<Integer> bulkDestination = redisson.getQueue("bulkDestination");
        RQueue<Integer> oboDestination = redisson.getQueue("oboDestination");

        List<Integer> input = Arrays.asList(1, 2, 3, 4, 5);
        bulkSource.addAll(input);
        oboSource.addAll(input);

        List<Integer> bulkMoved = bulkSource.move(QueueMoveElementsArgs.to(bulkDestination.getName())
                                                                       .count(3)
                                                                       .bulk());
        List<Integer> oboMoved = oboSource.move(QueueMoveElementsArgs.to(oboDestination.getName())
                                                                     .count(3)
                                                                     .oneByOne());

        // a queue always removes from the head and adds at the tail, so both
        // orderings must agree. They diverge only when elements are removed
        // and added at the same end, which this API never does.
        assertThat(oboMoved).containsExactlyElementsOf(bulkMoved);
        assertThat(oboDestination.readAll()).containsExactlyElementsOf(bulkDestination.readAll());
        assertThat(oboSource.readAll()).containsExactlyElementsOf(bulkSource.readAll());
    }

    @Test
    public void testMoveOneByOne() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2, 3, 4));

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .count(3)
                                                                 .oneByOne());

        assertThat(elements).containsExactly(1, 2, 3);
        assertThat(queue).containsExactly(4);
        assertThat(destination).containsExactly(1, 2, 3);
    }

    @Test
    public void testMoveExactlyOneByOne() {
        RQueue<Integer> queue = getQueue();
        RQueue<Integer> destination = redisson.getQueue("destination");
        queue.addAll(Arrays.asList(1, 2));

        List<Integer> elements = queue.move(QueueMoveElementsArgs.to(destination.getName())
                                                                 .exactly(2)
                                                                 .oneByOne());

        assertThat(elements).containsExactly(1, 2);
        assertThat(queue).isEmpty();
        assertThat(destination).containsExactly(1, 2);
    }

    @Test
    public void testAddOffer() {
        RQueue<Integer> queue = getQueue();
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        assertThat(queue).containsExactly(1, 2, 3, 4);
        Assertions.assertEquals((Integer)1, queue.poll());
        assertThat(queue).containsExactly(2, 3, 4);
        Assertions.assertEquals((Integer)2, queue.element());
    }

    public static class TestModel {
        private String key;
        private String traceId;
        private long createdAt;
        private UUID uuid = UUID.randomUUID();

        public TestModel() {
        }

        public TestModel(String key, String traceId, long createdAt) {
            this.key = key;
            this.traceId = traceId;
            this.createdAt = createdAt;
        }

    }

    @Test
    public void testRemoveWithCodec() {
        RQueue<TestModel> queue = redisson.getQueue("queue");

        TestModel msg = new TestModel("key", "traceId", 0L);
        queue.add(msg);
        assertThat(queue.contains(queue.peek())).isTrue();
    }

    @Test
    public void testRemove() {
        RQueue<Integer> queue = getQueue();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        assertThat(queue).containsExactly(3, 4);
        queue.remove();
        queue.remove();

        Assertions.assertTrue(queue.isEmpty());
    }

    @Test
    public void testRemoveEmpty() {
        Assertions.assertThrows(NoSuchElementException.class, () -> {
            RQueue<Integer> queue = getQueue();
            queue.remove();
        });
    }

    @Test
    public void testIndexOf() {
        RQueue<Integer> queue = getQueue();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        assertThat(queue.indexOf(4)).isEqualTo(3);
    }
}
