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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopK;
import org.redisson.api.TopKInfo;
import org.redisson.api.topk.TopKInitArgs;
import org.redisson.client.RedisException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTopKTest extends RedisDockerTest {

    @Test
    public void testInit() {
        RTopK<String> topK = redisson.getTopK("testInit");
        topK.init(50);

        TopKInfo info = topK.getInfo();
        assertThat(info.getTopK()).isEqualTo(50);
        assertThat(info.getWidth()).isGreaterThan(0);
        assertThat(info.getDepth()).isGreaterThan(0);
        assertThat(info.getDecay()).isGreaterThan(0);
    }

    @Test
    public void testInitWithArgs() {
        RTopK<String> topK = redisson.getTopK("testInitArgs");
        topK.init(TopKInitArgs.topK(50)
                .width(2000)
                .depth(7)
                .decay(0.925));

        TopKInfo info = topK.getInfo();
        assertThat(info.getTopK()).isEqualTo(50);
        assertThat(info.getWidth()).isEqualTo(2000);
        assertThat(info.getDepth()).isEqualTo(7);
        assertThat(info.getDecay()).isEqualTo(0.925);
    }

    @Test
    public void testInitWithPartialArgs() {
        RTopK<String> topK = redisson.getTopK("testInitPartial");
        // only decay supplied - width and depth must fall back to defaults
        topK.init(TopKInitArgs.topK(10).decay(0.8));

        TopKInfo info = topK.getInfo();
        assertThat(info.getTopK()).isEqualTo(10);
        assertThat(info.getWidth()).isEqualTo(8);
        assertThat(info.getDepth()).isEqualTo(7);
        assertThat(info.getDecay()).isEqualTo(0.8);
    }

    @Test
    public void testInitDuplicate() {
        RTopK<String> topK = redisson.getTopK("testInitDup");
        topK.init(10);

        Assertions.assertThrows(RedisException.class, () -> {
            topK.init(10);
        });
    }

    @Test
    public void testAddSingleNoDrop() {
        RTopK<String> topK = redisson.getTopK("testAddSingle");
        topK.init(50);

        String dropped = topK.add("foo");
        assertThat(dropped).isNull();
        assertThat(topK.contains("foo")).isTrue();
    }

    @Test
    public void testAddBulkNoDrop() {
        RTopK<String> topK = redisson.getTopK("testAddBulk");
        topK.init(50);

        List<String> dropped = topK.add(Arrays.asList("a", "b", "c"));
        assertThat(dropped).hasSize(3);
        assertThat(dropped).containsOnlyNulls();

        assertThat(topK.contains("a")).isTrue();
        assertThat(topK.contains("b")).isTrue();
        assertThat(topK.contains("c")).isTrue();
    }

    @Test
    public void testAddDropsItem() {
        RTopK<String> topK = redisson.getTopK("testAddDrop");
        topK.init(1);

        for (int i = 0; i < 100; i++) {
            topK.add("a");
        }

        Set<String> dropped = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String d = topK.add("b");
            if (d != null) {
                dropped.add(d);
            }
        }

        assertThat(topK.list()).containsExactly("b");
        assertThat(dropped).contains("a");
    }

    @Test
    public void testIncrementBySingle() {
        RTopK<String> topK = redisson.getTopK("testIncr");
        topK.init(50);

        topK.incrementBy("x", 5);

        Map<String, Long> counts = topK.listWithCount();
        assertThat(counts).containsEntry("x", 5L);
    }

    @Test
    public void testIncrementByBulk() {
        RTopK<String> topK = redisson.getTopK("testIncrBulk");
        topK.init(50);

        Map<String, Integer> increments = new LinkedHashMap<>();
        increments.put("a", 3);
        increments.put("b", 7);

        topK.incrementBy(increments);

        Map<String, Long> counts = topK.listWithCount();
        assertThat(counts).containsEntry("a", 3L);
        assertThat(counts).containsEntry("b", 7L);
    }

    @Test
    public void testContainsSingle() {
        RTopK<String> topK = redisson.getTopK("testContains");
        topK.init(50);

        topK.add("hello");

        assertThat(topK.contains("hello")).isTrue();
        assertThat(topK.contains("world")).isFalse();
    }

    @Test
    public void testContainsBulk() {
        RTopK<String> topK = redisson.getTopK("testContainsBulk");
        topK.init(50);

        topK.add("a");
        topK.add("b");

        List<Boolean> result = topK.contains(Arrays.asList("a", "b", "c"));
        assertThat(result).containsExactly(true, true, false);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCountSingle() {
        RTopK<String> topK = redisson.getTopK("testCount");
        topK.init(50);

        topK.add("foo");
        topK.add("foo");
        topK.add("foo");

        assertThat(topK.count("foo")).isEqualTo(3);
        assertThat(topK.count("missing")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCountBulk() {
        RTopK<String> topK = redisson.getTopK("testCountBulk");
        topK.init(50);

        topK.add("a");
        topK.add("a");
        topK.add("b");

        List<Long> counts = topK.count(Arrays.asList("a", "b", "c"));
        assertThat(counts).containsExactly(2L, 1L, 0L);
    }

    @Test
    public void testList() {
        RTopK<String> topK = redisson.getTopK("testList");
        topK.init(50);

        topK.add("a");
        topK.add("b");
        topK.add("c");

        List<String> items = topK.list();
        assertThat(items).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    public void testListFewerThanK() {
        RTopK<String> topK = redisson.getTopK("testListFewer");
        topK.init(50);

        topK.add("only");

        // fewer than k distinct items - nil slots must be filtered out
        List<String> items = topK.list();
        assertThat(items).containsExactly("only");
        assertThat(items).doesNotContainNull();
    }

    @Test
    public void testListWithCount() {
        RTopK<String> topK = redisson.getTopK("testListWithCount");
        topK.init(50);

        topK.incrementBy("a", 4);
        topK.incrementBy("b", 2);
        topK.incrementBy("c", 1);

        Map<String, Long> counts = topK.listWithCount();
        assertThat(counts).containsEntry("a", 4L);
        assertThat(counts).containsEntry("b", 2L);
        assertThat(counts).containsEntry("c", 1L);
    }

    @Test
    public void testGetInfo() {
        RTopK<String> topK = redisson.getTopK("testInfo");
        topK.init(TopKInitArgs.topK(25)
                .width(100)
                .depth(5)
                .decay(0.9));

        TopKInfo info = topK.getInfo();
        assertThat(info.getTopK()).isEqualTo(25);
        assertThat(info.getWidth()).isEqualTo(100);
        assertThat(info.getDepth()).isEqualTo(5);
        assertThat(info.getDecay()).isEqualTo(0.9);
    }

    @Test
    public void testEmptyTopK() {
        RTopK<String> topK = redisson.getTopK("testEmpty");
        topK.init(10);

        assertThat(topK.list()).isEmpty();
        assertThat(topK.listWithCount()).isEmpty();
        assertThat(topK.contains("anything")).isFalse();
    }

    @Test
    public void testAddAsync() {
        RTopK<String> topK = redisson.getTopK("testAddAsync");
        topK.init(50);

        String dropped = topK.addAsync("asyncItem")
                .toCompletableFuture().join();
        assertThat(dropped).isNull();

        Boolean contains = topK.containsAsync("asyncItem")
                .toCompletableFuture().join();
        assertThat(contains).isTrue();
    }

    @Test
    public void testIncrementByAsync() {
        RTopK<String> topK = redisson.getTopK("testIncrAsync");
        topK.init(50);

        topK.incrementByAsync("x", 9)
                .toCompletableFuture().join();

        Map<String, Long> counts = topK.listWithCountAsync()
                .toCompletableFuture().join();
        assertThat(counts).containsEntry("x", 9L);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCountAsync() {
        RTopK<String> topK = redisson.getTopK("testCountAsync");
        topK.init(50);

        topK.add("y");
        topK.add("y");

        Long count = topK.countAsync("y")
                .toCompletableFuture().join();
        assertThat(count).isEqualTo(2);
    }

    @Test
    public void testListAsync() {
        RTopK<String> topK = redisson.getTopK("testListAsync");
        topK.init(50);

        topK.add("item1");

        List<String> items = topK.listAsync()
                .toCompletableFuture().join();
        assertThat(items).containsExactly("item1");
    }

    @Test
    public void testGetInfoAsync() {
        RTopK<String> topK = redisson.getTopK("testInfoAsync");
        topK.init(TopKInitArgs.topK(7).width(50).depth(4).decay(0.9));

        TopKInfo info = topK.getInfoAsync()
                .toCompletableFuture().join();
        assertThat(info.getTopK()).isEqualTo(7);
        assertThat(info.getWidth()).isEqualTo(50);
    }

    @Test
    public void testDeleteKey() {
        RTopK<String> topK = redisson.getTopK("testDeleteKey");
        topK.init(10);
        topK.add("val");

        assertThat(topK.isExists()).isTrue();
        topK.delete();
        assertThat(topK.isExists()).isFalse();
    }

    @Test
    public void testRename() {
        RTopK<String> topK = redisson.getTopK("testRenameSrc");
        topK.init(10);
        topK.add("val");

        topK.rename("testRenameDst");

        RTopK<String> topK2 = redisson.getTopK("testRenameDst");
        assertThat(topK2.contains("val")).isTrue();
    }
}
