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
import org.redisson.api.AsyncIterator;
import org.redisson.api.RArray;
import org.redisson.api.RArrayReactive;
import org.redisson.api.RArrayRx;
import org.redisson.api.array.ArrayEntry;
import org.redisson.api.array.ArrayFullInfo;
import org.redisson.api.array.ArrayGrepArgs;
import org.redisson.api.array.ArrayInfo;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author lamnt2008
 *
 */
public class RedissonArrayTest extends RedisDockerTest {

    RArray<String> array;

    @BeforeEach
    public void before() {
        array = redisson.getArray("test-array", StringCodec.INSTANCE);
        try {
            array.count();
        } catch (RedisException e) {
            Assumptions.assumeFalse(e.getMessage().contains("unknown command"), "Redis Array isn't supported");
            throw e;
        }
        array.delete();
    }

    @Test
    public void testSetGetRangeAndScan() {
        assertThat(array.set(0, "a")).isEqualTo(1);
        assertThat(array.set(3, "d")).isEqualTo(1);

        assertThat(array.get(0)).isEqualTo("a");
        assertThat(array.get(1)).isNull();
        assertThat(array.get(0, 1, 3)).containsExactly("a", null, "d");
        assertThat(array.count()).isEqualTo(2);
        assertThat(array.length()).isEqualTo(4);
        assertThat(array.range(0, 3)).containsExactly("a", null, null, "d");

        List<ArrayEntry<String>> entries = array.scan(0, 3);
        assertThat(entries).containsExactly(new ArrayEntry<>(0, "a"), new ArrayEntry<>(3, "d"));
    }

    @Test
    public void testSetRangeAndSetMap() {
        assertThat(array.set(0, "a", "b")).isEqualTo(2);

        Map<Long, String> entries = new LinkedHashMap<>();
        entries.put(4L, "e");
        entries.put(2L, "c");
        assertThat(array.set(entries)).isEqualTo(2);

        assertThat(array.range(0, 4)).containsExactly("a", "b", "c", null, "e");
    }

    @Test
    public void testDeleteRange() {
        assertThat(array.set(0, "a", "b", "c", "d")).isEqualTo(4);

        assertThat(array.deleteRange(1, 2)).isEqualTo(2);
        assertThat(array.count()).isEqualTo(2);
        assertThat(array.range(0, 3)).containsExactly("a", null, null, "d");

        assertThat(array.delete(0, 3)).isEqualTo(2);
        assertThat(array.count()).isZero();
    }

    @Test
    public void testRing() {
        assertThat(array.ring(3, "a")).isEqualTo(0);
        assertThat(array.ring(3, "b", "c", "d")).isEqualTo(0);

        assertThat(array.next()).isEqualTo(1);
        assertThat(array.lastItems(3)).containsExactly("b", "c", "d");
        assertThat(array.lastItemsReversed(3)).containsExactly("d", "c", "b");

        assertThat(array.seek(0)).isTrue();
        assertThat(array.next()).isEqualTo(0);
    }

    @Test
    public void testInsert() {
        assertThat(array.insert("a", "b")).isEqualTo(1);
        assertThat(array.insert("c")).isEqualTo(2);

        assertThat(array.next()).isEqualTo(3);
        assertThat(array.range(0, 2)).containsExactly("a", "b", "c");
    }

    @Test
    public void testGrep() {
        array.set(0, "Alpha", "beta", "Gamma");

        assertThat(array.grep(ArrayGrepArgs.match("A").noCase())).containsExactly(0L, 1L, 2L);
        assertThat(array.grep(0, 2, ArrayGrepArgs.exact("beta"))).containsExactly(1L);
        assertThat(array.grepEntries(0, 2, ArrayGrepArgs.glob("*ta")))
                .containsExactly(new ArrayEntry<>(1, "beta"));
    }

    @Test
    public void testAggregation() {
        array.set(0, "7", "3", "4", "not-number");

        assertThat(array.sum(0, 3)).isEqualTo(14D);
        assertThat(array.min(0, 3)).isEqualTo(3D);
        assertThat(array.max(0, 3)).isEqualTo(7D);
        assertThat(array.bitAnd(0, 2)).isEqualTo(0L);
        assertThat(array.bitOr(0, 2)).isEqualTo(7L);
        assertThat(array.bitXor(0, 2)).isEqualTo(0L);
        assertThat(array.count(0, 3)).isEqualTo(4L);
        assertThat(array.countMatches(0, 3, "3")).isEqualTo(1L);
    }

    @Test
    public void testInfo() {
        array.set(0, "a", "b");

        ArrayInfo info = array.getInfo();
        assertThat(info.getCount()).isEqualTo(2);
        assertThat(info.getLength()).isEqualTo(2);
        assertThat(info.getSliceSize()).isPositive();

        ArrayFullInfo fullInfo = array.getFullInfo();
        assertThat(fullInfo.getDenseSlices()).isNotNull();
        assertThat(fullInfo.getSparseSlices()).isNotNull();
    }

    @Test
    public void testDefaultCodec() {
        RArray<String> array = redisson.getArray("test-array-default");
        array.delete();

        array.set(0, "value");

        assertThat(array.get(0)).isEqualTo("value");
    }

    @Test
    public void testIsSet() {
        array.set(0, "a");
        array.set(3, "d");

        assertThat(array.isSet(0)).isTrue();
        assertThat(array.isSet(1)).isFalse();
        assertThat(array.isSet(2)).isFalse();
        assertThat(array.isSet(3)).isTrue();
        assertThat(array.isSet(100)).isFalse();
    }

    @Test
    public void testIterator() {
        array.set(0, "a");
        array.set(3, "d");
        array.set(5, "f");

        List<ArrayEntry<String>> entries = new ArrayList<>();
        array.iterator().forEachRemaining(entries::add);

        assertThat(entries).containsExactly(
                new ArrayEntry<>(0, "a"), new ArrayEntry<>(3, "d"), new ArrayEntry<>(5, "f"));
    }

    @Test
    public void testIteratorEmpty() {
        assertThat(array.iterator().hasNext()).isFalse();
    }

    @Test
    public void testIteratorWithCount() {
        Map<Long, String> values = new LinkedHashMap<>();
        for (long i = 0; i < 25; i++) {
            values.put(i, "v" + i);
        }
        array.set(values);

        // page size smaller than the number of stored entries forces multiple ARSCAN pages
        List<ArrayEntry<String>> entries = new ArrayList<>();
        array.iterator(10).forEachRemaining(entries::add);

        assertThat(entries).hasSize(25);
        for (int i = 0; i < 25; i++) {
            assertThat(entries.get(i)).isEqualTo(new ArrayEntry<>(i, "v" + i));
        }
    }

    @Test
    public void testIteratorWithCountSparse() {
        array.set(0, "a");
        array.set(7, "h");
        array.set(99, "x");

        List<ArrayEntry<String>> entries = new ArrayList<>();
        array.iterator(2).forEachRemaining(entries::add);

        assertThat(entries).containsExactly(
                new ArrayEntry<>(0, "a"), new ArrayEntry<>(7, "h"), new ArrayEntry<>(99, "x"));
    }

    @Test
    public void testStream() {
        array.set(0, "a", "b", "c");
        array.set(10, "k");

        List<String> values = array.stream().map(ArrayEntry::getValue).collect(Collectors.toList());

        assertThat(values).containsExactly("a", "b", "c", "k");
    }

    @Test
    public void testIsSetAsync() {
        array.set(2, "c");

        assertThat(array.isSetAsync(2).toCompletableFuture().join()).isTrue();
        assertThat(array.isSetAsync(1).toCompletableFuture().join()).isFalse();
    }

    @Test
    public void testIteratorAsync() {
        array.set(0, "a");
        array.set(4, "e");
        array.set(8, "i");

        AsyncIterator<ArrayEntry<String>> iterator = array.iteratorAsync(2);

        List<ArrayEntry<String>> entries = new ArrayList<>();
        while (iterator.hasNext().toCompletableFuture().join()) {
            entries.add(iterator.next().toCompletableFuture().join());
        }

        assertThat(entries).containsExactly(
                new ArrayEntry<>(0, "a"), new ArrayEntry<>(4, "e"), new ArrayEntry<>(8, "i"));
    }

    @Test
    public void testIteratorReactive() {
        array.set(0, "a");
        array.set(3, "d");
        array.set(6, "g");

        RArrayReactive<String> reactiveArray = redisson.reactive().getArray("test-array", StringCodec.INSTANCE);

        List<ArrayEntry<String>> entries = reactiveArray.iterator().collectList().block();
        assertThat(entries).containsExactly(
                new ArrayEntry<>(0, "a"), new ArrayEntry<>(3, "d"), new ArrayEntry<>(6, "g"));

        assertThat(reactiveArray.isSet(3).block()).isTrue();
        assertThat(reactiveArray.isSet(1).block()).isFalse();
    }

    @Test
    public void testIteratorRx() {
        array.set(0, "a");
        array.set(3, "d");
        array.set(6, "g");

        RArrayRx<String> rxArray = redisson.rxJava().getArray("test-array", StringCodec.INSTANCE);

        List<ArrayEntry<String>> entries = rxArray.iterator().toList().blockingGet();
        assertThat(entries).containsExactly(
                new ArrayEntry<>(0, "a"), new ArrayEntry<>(3, "d"), new ArrayEntry<>(6, "g"));

        assertThat(rxArray.isSet(3).blockingGet()).isTrue();
        assertThat(rxArray.isSet(1).blockingGet()).isFalse();
    }

}
