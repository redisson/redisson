package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RQueue;

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

}
