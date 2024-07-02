package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonDelayedQueueTest extends RedisDockerTest {

    @Test
    public void testRemove() throws InterruptedException {
        RBlockingQueue<String> blockingFairQueue = redisson.getBlockingQueue("delay_queue");
        RDelayedQueue<String> delayedQueue = redisson.getDelayedQueue(blockingFairQueue);
        
        delayedQueue.offer("1_1_1", 3, TimeUnit.SECONDS);
        delayedQueue.offer("1_1_2", 7, TimeUnit.SECONDS);
        assertThat(delayedQueue.contains("1_1_1")).isTrue();
        assertThat(delayedQueue.remove("1_1_1")).isTrue();
        assertThat(delayedQueue.contains("1_1_1")).isFalse();

        Thread.sleep(9000);
        
        assertThat(blockingFairQueue).containsOnly("1_1_2");
    }
    
    @Test
    public void testRemoveAll() throws InterruptedException {
        RBlockingQueue<String> blockingFairQueue = redisson.getBlockingQueue("delay_queue");
        RDelayedQueue<String> delayedQueue = redisson.getDelayedQueue(blockingFairQueue);
        
        delayedQueue.offer("1_1_1", 3, TimeUnit.SECONDS);
        delayedQueue.offer("1_1_2", 7, TimeUnit.SECONDS);
        assertThat(delayedQueue.contains("1_1_1")).isTrue();
        assertThat(delayedQueue.contains("1_1_2")).isTrue();
        assertThat(delayedQueue.removeAll(Arrays.asList("1_1_1", "1_1_2"))).isTrue();
        assertThat(delayedQueue.contains("1_1_1")).isFalse();
        assertThat(delayedQueue.contains("1_1_2")).isFalse();
        
        Thread.sleep(9000);
        
        assertThat(blockingFairQueue.isEmpty()).isTrue();
    }

    
    @Test
    public void testDealyedQueueRetainAll() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);

        assertThat(dealyedQueue.retainAll(Arrays.asList(1, 2, 3))).isFalse();
        assertThat(dealyedQueue.retainAll(Arrays.asList(3, 1, 2, 8))).isFalse();
        assertThat(dealyedQueue.readAll()).containsExactly(3, 1, 2);
        
        assertThat(dealyedQueue.retainAll(Arrays.asList(1, 2))).isTrue();
        assertThat(dealyedQueue.readAll()).containsExactly(1, 2);
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueueReadAll() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);

        assertThat(dealyedQueue.readAll()).containsExactly(3, 1, 2);
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueueRemoveAll() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.removeAll(Arrays.asList(1, 2))).isTrue();
        assertThat(dealyedQueue).containsExactly(3);
        assertThat(dealyedQueue.removeAll(Arrays.asList(3, 4))).isTrue();
        assertThat(dealyedQueue).isEmpty();
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueueContainsAll() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.containsAll(Arrays.asList(1, 2))).isTrue();
        assertThat(dealyedQueue.containsAll(Arrays.asList(1, 2, 4))).isFalse();

        assertThat(dealyedQueue.containsAll(Arrays.asList(1, 1))).isTrue();
        assertThat(dealyedQueue.containsAll(Arrays.asList(1, 2, 1))).isTrue();

        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueueContains() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.contains(1)).isTrue();
        assertThat(dealyedQueue.contains(4)).isFalse();
        
        dealyedQueue.destroy();
    }

    @Test
    public void testDealyedQueueRemove() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.remove(4)).isFalse();
        assertThat(dealyedQueue.remove(3)).isTrue();
        assertThat(dealyedQueue).containsExactly(1, 2);
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueuePeek() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 1, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.peek()).isEqualTo(3);
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueuePollLastAndOfferFirstTo() {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("test");
        RDelayedQueue<Integer> dealyedQueue = redisson.getDelayedQueue(queue1);
        
        dealyedQueue.offer(3, 5, TimeUnit.SECONDS);
        dealyedQueue.offer(2, 2, TimeUnit.SECONDS);
        dealyedQueue.offer(1, 1, TimeUnit.SECONDS);

        RQueue<Integer> queue2 = redisson.getQueue("deque2");
        queue2.offer(6);
        queue2.offer(5);
        queue2.offer(4);

        assertThat(dealyedQueue.pollLastAndOfferFirstTo(queue2.getName())).isEqualTo(1);
        assertThat(queue2).containsExactly(1, 6, 5, 4);
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDelayedQueueOrder() {
        RBlockingQueue<String> queue = redisson.getBlockingQueue("test");
        RDelayedQueue<String> dealyedQueue = redisson.getDelayedQueue(queue);
        
        dealyedQueue.offer("1", 1, TimeUnit.SECONDS);
        dealyedQueue.offer("4", 4, TimeUnit.SECONDS);
        dealyedQueue.offer("3", 3, TimeUnit.SECONDS);
        dealyedQueue.offer("2", 2, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue).containsExactly("1", "4", "3", "2");
        
        assertThat(dealyedQueue.poll()).isEqualTo("1");
        assertThat(dealyedQueue.poll()).isEqualTo("4");
        assertThat(dealyedQueue.poll()).isEqualTo("3");
        assertThat(dealyedQueue.poll()).isEqualTo("2");
        
        assertThat(queue.isEmpty()).isTrue();
        
        assertThat(queue.poll()).isNull();
        
        dealyedQueue.destroy();
    }

    @Test
    public void testPollLimited() throws InterruptedException {
        RBlockingQueue<String> queue = redisson.getBlockingQueue("test");
        RDelayedQueue<String> dealyedQueue = redisson.getDelayedQueue(queue);

        dealyedQueue.offer("1", 1, TimeUnit.SECONDS);
        dealyedQueue.offer("2", 2, TimeUnit.SECONDS);
        dealyedQueue.offer("3", 3, TimeUnit.SECONDS);
        dealyedQueue.offer("4", 4, TimeUnit.SECONDS);

        assertThat(dealyedQueue.poll(3)).containsExactly("1", "2", "3");
        assertThat(dealyedQueue.poll(2)).containsExactly("4");
        assertThat(dealyedQueue.poll(2)).isEmpty();

        Thread.sleep(3000);
        assertThat(queue.isEmpty()).isTrue();

        assertThat(queue.poll()).isNull();
        assertThat(queue.poll()).isNull();

        dealyedQueue.destroy();
    }

    
    @Test
    public void testPoll() throws InterruptedException {
        RBlockingQueue<String> queue = redisson.getBlockingQueue("test");
        RDelayedQueue<String> dealyedQueue = redisson.getDelayedQueue(queue);
        
        dealyedQueue.offer("1", 1, TimeUnit.SECONDS);
        dealyedQueue.offer("2", 2, TimeUnit.SECONDS);
        dealyedQueue.offer("3", 3, TimeUnit.SECONDS);
        dealyedQueue.offer("4", 4, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue.poll()).isEqualTo("1");
        assertThat(dealyedQueue.poll()).isEqualTo("2");
        assertThat(dealyedQueue.poll()).isEqualTo("3");
        assertThat(dealyedQueue.poll()).isEqualTo("4");
        
        Thread.sleep(3000);
        assertThat(queue.isEmpty()).isTrue();
        
        assertThat(queue.poll()).isNull();
        assertThat(queue.poll()).isNull();
        
        dealyedQueue.destroy();
    }
    
    @Test
    public void testDealyedQueue() throws InterruptedException {
        RBlockingQueue<String> queue = redisson.getBlockingQueue("test");
        RDelayedQueue<String> dealyedQueue = redisson.getDelayedQueue(queue);
        
        dealyedQueue.offer("1", 1, TimeUnit.SECONDS);
        dealyedQueue.offer("2", 5, TimeUnit.SECONDS);
        dealyedQueue.offer("4", 4, TimeUnit.SECONDS);
        dealyedQueue.offer("2", 2, TimeUnit.SECONDS);
        dealyedQueue.offer("3", 3, TimeUnit.SECONDS);
        
        assertThat(dealyedQueue).containsExactly("1", "2", "4", "2", "3");
        
        Thread.sleep(500);
        assertThat(queue.isEmpty()).isTrue();
        Thread.sleep(600);
        assertThat(queue).containsExactly("1");
        assertThat(dealyedQueue).containsExactly("2", "4", "2", "3");
        
        Thread.sleep(500);
        assertThat(queue).containsExactly("1");

        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2");
        assertThat(dealyedQueue).containsExactly("2", "4", "3");
        
        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2");

        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2", "3");
        assertThat(dealyedQueue).containsExactly("2", "4");

        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2", "3");

        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2", "3", "4");
        
        assertThat(dealyedQueue).containsExactly("2");
        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2", "3", "4");
        Thread.sleep(500);
        assertThat(queue).containsExactly("1", "2", "3", "4", "2");

        assertThat(dealyedQueue).isEmpty();
        
        assertThat(queue.poll()).isEqualTo("1");
        assertThat(queue.poll()).isEqualTo("2");
        assertThat(queue.poll()).isEqualTo("3");
        assertThat(queue.poll()).isEqualTo("4");
        assertThat(queue.poll()).isEqualTo("2");
        
        dealyedQueue.destroy();
    }
    

    
}
