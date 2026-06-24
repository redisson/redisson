/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RCircularBuffer;
import org.redisson.api.RCircularBufferReactive;
import org.redisson.api.RCircularBufferRx;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCircularBufferTest extends RedisDockerTest {

    RCircularBuffer<String> buffer;

    @BeforeEach
    public void before() {
        buffer = redisson.getCircularBuffer("test-circular-buffer", StringCodec.INSTANCE);
        try {
            buffer.size();
        } catch (RedisException e) {
            Assumptions.assumeFalse(e.getMessage().contains("unknown command"), "Redis Array isn't supported");
            throw e;
        }
        buffer.delete();
    }

    @Test
    public void testCapacity() {
        assertThat(buffer.capacity()).isZero();
        assertThat(buffer.trySetCapacity(5)).isTrue();
        assertThat(buffer.trySetCapacity(7)).isFalse();
        assertThat(buffer.capacity()).isEqualTo(5);

        buffer.setCapacity(3);
        assertThat(buffer.capacity()).isEqualTo(3);
    }

    @Test
    public void testAddWithWraparound() {
        buffer.trySetCapacity(3);

        buffer.add("a");
        buffer.add("b");
        buffer.add("c");
        buffer.add("d");

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.remainingCapacity()).isZero();

        // stable ring indexes: "d" overwrote "a" at index 0
        assertThat(buffer.get(0)).isEqualTo("d");
        assertThat(buffer.get(1)).isEqualTo("b");
        assertThat(buffer.get(2)).isEqualTo("c");

        // newest window in insertion order and reversed
        assertThat(buffer.lastItems(3, false)).containsExactly("b", "c", "d");
        assertThat(buffer.lastItems(3, true)).containsExactly("d", "c", "b");
        assertThat(buffer.lastItems(2, false)).containsExactly("c", "d");

        assertThat(buffer.range(0, 2)).containsExactly("d", "b", "c");
        assertThat(buffer.readAll()).containsExactly("b", "c", "d");
    }

    @Test
    public void testAddAll() {
        buffer.trySetCapacity(3);

        assertThat(buffer.addAll(Arrays.asList("a", "b", "c", "d", "e"))).isTrue();

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.readAll()).containsExactly("c", "d", "e");
        assertThat(buffer.lastItems(3, true)).containsExactly("e", "d", "c");
    }

    @Test
    public void testAddAllEmpty() {
        buffer.trySetCapacity(3);
        assertThat(buffer.addAll(Arrays.asList())).isFalse();
        assertThat(buffer.size()).isZero();
    }

    @Test
    public void testSet() {
        // direct ARRING mapping, also (re)configures capacity
        assertThat(buffer.set(3, "a", "b", "c", "d")).isEqualTo(0);

        assertThat(buffer.capacity()).isEqualTo(3);
        assertThat(buffer.get(0)).isEqualTo("d");
        assertThat(buffer.lastItems(3, false)).containsExactly("b", "c", "d");
        assertThat(buffer.lastItems(3, true)).containsExactly("d", "c", "b");

        // subsequent add() reuses the configured capacity
        buffer.add("e");
        assertThat(buffer.readAll()).containsExactly("c", "d", "e");
    }

    @Test
    public void testAddRequiresCapacity() {
        assertThatThrownBy(() -> buffer.add("a")).isInstanceOf(RedisException.class);
    }

    @Test
    public void testRemainingCapacity() {
        buffer.trySetCapacity(5);
        assertThat(buffer.remainingCapacity()).isEqualTo(5);

        buffer.addAll(Arrays.asList("a", "b"));
        assertThat(buffer.remainingCapacity()).isEqualTo(3);

        buffer.addAll(Arrays.asList("c", "d", "e", "f", "g"));
        assertThat(buffer.remainingCapacity()).isZero();
        assertThat(buffer.size()).isEqualTo(5);
    }

    @Test
    public void testAggregation() {
        buffer.trySetCapacity(10);
        buffer.addAll(Arrays.asList("7", "3", "4"));

        assertThat(buffer.sum()).isEqualTo(14D);
        assertThat(buffer.min()).isEqualTo(3D);
        assertThat(buffer.max()).isEqualTo(7D);

        assertThat(buffer.sum(0, 2)).isEqualTo(14D);
        assertThat(buffer.min(0, 2)).isEqualTo(3D);
        assertThat(buffer.max(0, 2)).isEqualTo(7D);
    }

    @Test
    public void testAggregationEmpty() {
        buffer.trySetCapacity(5);
        assertThat(buffer.sum()).isNull();
        assertThat(buffer.min()).isNull();
        assertThat(buffer.max()).isNull();
    }

    @Test
    public void testReSetCapacity() {
        buffer.trySetCapacity(5);
        buffer.addAll(Arrays.asList("0", "1", "2", "3", "4"));
        assertThat(buffer.readAll()).containsExactly("0", "1", "2", "3", "4");

        // shrink: the new capacity is applied on the next write
        buffer.setCapacity(3);
        buffer.add("5");

        assertThat(buffer.capacity()).isEqualTo(3);
        assertThat(buffer.size()).isLessThanOrEqualTo(3);
    }

    @Test
    public void testDeleteRemovesSettings() {
        assertThat(buffer.trySetCapacity(3)).isTrue();
        assertThat(buffer.trySetCapacity(5)).isFalse();
        buffer.add("a");

        assertThat(buffer.delete()).isTrue();
        assertThat(buffer.size()).isZero();
        assertThat(buffer.capacity()).isZero();

        // capacity settings key was removed alongside the data key
        assertThat(buffer.trySetCapacity(7)).isTrue();
    }

    @Test
    public void testExpire() {
        buffer.trySetCapacity(3);
        buffer.add("a");

        buffer.expire(Duration.ofSeconds(100));
        assertThat(buffer.remainTimeToLive()).isGreaterThan(0);

        buffer.clearExpire();
        assertThat(buffer.remainTimeToLive()).isEqualTo(-1);
    }

    @Test
    public void testDefaultCodec() {
        RCircularBuffer<String> b = redisson.getCircularBuffer("test-circular-buffer-default");
        b.delete();

        b.trySetCapacity(2);
        b.add("value");

        assertThat(b.get(0)).isEqualTo("value");
        assertThat(b.size()).isEqualTo(1);
    }

    @Test
    public void testClearKeepsCapacity() {
        buffer.trySetCapacity(3);
        buffer.addAll(Arrays.asList("a", "b"));

        buffer.clear();

        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.size()).isZero();
        assertThat(buffer.capacity()).isEqualTo(3);

        // capacity is still defined, so add() keeps working without reconfiguring
        buffer.add("c");
        assertThat(buffer.readAll()).containsExactly("c");
    }

    @Test
    public void testIsEmptyIsFull() {
        buffer.trySetCapacity(3);
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.isFull()).isFalse();

        buffer.addAll(Arrays.asList("a", "b"));
        assertThat(buffer.isEmpty()).isFalse();
        assertThat(buffer.isFull()).isFalse();

        buffer.add("c");
        assertThat(buffer.isFull()).isTrue();

        buffer.add("d");
        assertThat(buffer.isFull()).isTrue();
    }

    @Test
    public void testPeek() {
        buffer.trySetCapacity(3);

        assertThat(buffer.peekLast()).isNull();
        assertThat(buffer.peekFirst()).isNull();

        buffer.addAll(Arrays.asList("a", "b", "c", "d"));

        // newest is "d"; oldest retained is "b" ("a" was evicted)
        assertThat(buffer.peekLast()).isEqualTo("d");
        assertThat(buffer.peekFirst()).isEqualTo("b");
    }

    @Test
    public void testGetIndexes() {
        buffer.trySetCapacity(3);
        buffer.addAll(Arrays.asList("a", "b", "c", "d"));

        // ring indexes after wraparound: 0="d", 1="b", 2="c"
        assertThat(buffer.get(0L, 1L, 2L)).containsExactly("d", "b", "c");
        assertThat(buffer.get(0L, 2L)).containsExactly("d", "c");
    }

    @Test
    public void testCountAndContains() {
        buffer.trySetCapacity(5);
        buffer.addAll(Arrays.asList("x", "y", "x", "z", "x"));

        assertThat(buffer.count("x")).isEqualTo(3);
        assertThat(buffer.count("y")).isEqualTo(1);
        assertThat(buffer.count("w")).isZero();

        assertThat(buffer.contains("x")).isTrue();
        assertThat(buffer.contains("w")).isFalse();
    }

    @Test
    public void testAverage() {
        buffer.trySetCapacity(5);
        assertThat(buffer.average()).isNull();

        buffer.addAll(Arrays.asList("2", "4", "6"));
        assertThat(buffer.average()).isEqualTo(4.0);
    }

    @Test
    public void testBitwise() {
        buffer.trySetCapacity(5);
        buffer.addAll(Arrays.asList("6", "3"));

        assertThat(buffer.bitAnd()).isEqualTo(2L);
        assertThat(buffer.bitOr()).isEqualTo(7L);
        assertThat(buffer.bitXor()).isEqualTo(5L);

        assertThat(buffer.bitAnd(0, 1)).isEqualTo(2L);
        assertThat(buffer.bitOr(0, 1)).isEqualTo(7L);
        assertThat(buffer.bitXor(0, 1)).isEqualTo(5L);
    }

    @Test
    public void testReactive() {
        RCircularBufferReactive<String> reactiveBuffer =
                redisson.reactive().getCircularBuffer("test-circular-buffer", StringCodec.INSTANCE);

        assertThat(reactiveBuffer.trySetCapacity(3).block()).isTrue();
        assertThat(reactiveBuffer.add("a").block()).isTrue();
        assertThat(reactiveBuffer.add("b").block()).isTrue();

        assertThat(reactiveBuffer.size().block()).isEqualTo(2);
        assertThat(reactiveBuffer.lastItems(2, false).block()).containsExactly("a", "b");
        assertThat(reactiveBuffer.get(1).block()).isEqualTo("b");
    }

    @Test
    public void testRx() {
        RCircularBufferRx<String> rxBuffer =
                redisson.rxJava().getCircularBuffer("test-circular-buffer", StringCodec.INSTANCE);

        assertThat(rxBuffer.trySetCapacity(3).blockingGet()).isTrue();
        assertThat(rxBuffer.add("a").blockingGet()).isTrue();
        assertThat(rxBuffer.add("b").blockingGet()).isTrue();

        assertThat(rxBuffer.size().blockingGet()).isEqualTo(2);
        assertThat(rxBuffer.lastItems(2, true).blockingGet()).containsExactly("b", "a");
        assertThat(rxBuffer.get(0).blockingGet()).isEqualTo("a");
    }

}
