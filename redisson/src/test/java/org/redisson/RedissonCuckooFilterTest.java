/**
 * Copyright (c) 2013-2025 Nikita Koksharov
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
import org.redisson.api.CuckooFilterInfo;
import org.redisson.api.RCuckooFilter;
import org.redisson.api.cuckoofilter.CuckooFilterAddArgs;
import org.redisson.api.cuckoofilter.CuckooFilterInitArgs;
import org.redisson.client.RedisException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCuckooFilterTest extends RedisDockerTest {

    @Test
    public void testInit() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInit");
        cf.init(1000);

        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(0);
        assertThat(info.getNumberOfBuckets()).isGreaterThan(0);
    }

    @Test
    public void testInitWithArgs() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInitArgs");
        cf.init(CuckooFilterInitArgs.capacity(1000)
                .bucketSize(4)
                .maxIterations(500)
                .expansion(2));

        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(0);
        assertThat(info.getBucketSize()).isEqualTo(4);
        assertThat(info.getMaxIterations()).isEqualTo(500);
        assertThat(info.getExpansionRate()).isEqualTo(2);
    }

    @Test
    public void testInitWithBucketSizeOnly() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInitBucket");
        cf.init(CuckooFilterInitArgs.capacity(5000)
                .bucketSize(8));

        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getBucketSize()).isEqualTo(8);
    }

    @Test
    public void testInitDuplicate() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInitDup");
        cf.init(100);

        Assertions.assertThrows(RedisException.class, () -> {
            cf.init(100);
        });
    }

    @Test
    public void testAdd() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAdd");
        cf.init(1000);

        boolean result = cf.add("foo");
        assertThat(result).isTrue();

        assertThat(cf.exists("foo")).isTrue();
    }

    @Test
    public void testAddDuplicate() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddDup");
        cf.init(1000);

        cf.add("foo");
        boolean result = cf.add("foo");
        assertThat(result).isTrue();

        assertThat(cf.count("foo")).isEqualTo(2);
    }

    @Test
    public void testAddMultipleElements() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddMulti");
        cf.init(10000);

        for (int i = 0; i < 100; i++) {
            cf.add("item" + i);
        }

        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(100);
    }

    @Test
    public void testAddBulk() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddBulk");
        cf.init(1000);

        Set<String> added = cf.add(
                CuckooFilterAddArgs.<String>items(Arrays.asList("a", "b", "c")));

        assertThat(added).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(cf.exists("a")).isTrue();
        assertThat(cf.exists("b")).isTrue();
        assertThat(cf.exists("c")).isTrue();
    }

    @Test
    public void testAddBulkWithCapacity() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddBulkCap");

        Set<String> added = cf.add(
                CuckooFilterAddArgs.<String>items(Arrays.asList("x", "y"))
                        .capacity(5000));

        assertThat(added).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    public void testAddBulkNoCreate() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddBulkNC");

        Assertions.assertThrows(RedisException.class, () -> {
            cf.add(CuckooFilterAddArgs.<String>items(Arrays.asList("a", "b"))
                    .noCreate());
        });
    }

    @Test
    public void testAddBulkNoCreateExistingFilter() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddBulkNCExist");
        cf.init(1000);

        Set<String> added = cf.add(
                CuckooFilterAddArgs.items(Arrays.asList("a", "b"))
                        .noCreate());

        assertThat(added).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    public void testAddIfAbsent() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNX");
        cf.init(1000);

        boolean result = cf.addIfAbsent("foo");
        assertThat(result).isTrue();

        boolean duplicate = cf.addIfAbsent("foo");
        assertThat(duplicate).isFalse();

        assertThat(cf.count("foo")).isEqualTo(1);
    }

    @Test
    public void testAddIfAbsentNewElement() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNXNew");
        cf.init(1000);

        assertThat(cf.addIfAbsent("alpha")).isTrue();
        assertThat(cf.addIfAbsent("beta")).isTrue();
        assertThat(cf.addIfAbsent("alpha")).isFalse();
    }

    @Test
    public void testAddIfAbsentBulk() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNXBulk");
        cf.init(1000);

        cf.add("a");

        Set<String> added = cf.addIfAbsent(
                CuckooFilterAddArgs.<String>items(Arrays.asList("a", "b", "c")));

        assertThat(added).containsExactlyInAnyOrder("b", "c");
        assertThat(added).doesNotContain("a");
    }

    @Test
    public void testAddIfAbsentBulkAllNew() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNXBulkAll");
        cf.init(1000);

        Set<String> added = cf.addIfAbsent(
                CuckooFilterAddArgs.<String>items(Arrays.asList("x", "y", "z")));

        assertThat(added).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    public void testAddIfAbsentBulkNoCreate() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNXBulkNC");

        Assertions.assertThrows(RedisException.class, () -> {
            cf.addIfAbsent(
                    CuckooFilterAddArgs.<String>items(Arrays.asList("a"))
                            .noCreate());
        });
    }

    @Test
    public void testExists() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testExists");
        cf.init(1000);

        cf.add("hello");

        assertThat(cf.exists("hello")).isTrue();
        assertThat(cf.exists("world")).isFalse();
    }

    @Test
    public void testExistsAfterRemove() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testExistsRm");
        cf.init(1000);

        cf.add("item");
        assertThat(cf.exists("item")).isTrue();

        cf.remove("item");
        assertThat(cf.exists("item")).isFalse();
    }

    @Test
    public void testExistsBulk() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testMExists");
        cf.init(1000);

        cf.add("a");
        cf.add("b");

        Set<String> found = cf.exists(Arrays.asList("a", "b", "c", "d"));

        assertThat(found).containsExactlyInAnyOrder("a", "b");
        assertThat(found).doesNotContain("c", "d");
    }

    @Test
    public void testExistsBulkNoneExist() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testMExistsNone");
        cf.init(1000);

        Set<String> found = cf.exists(Arrays.asList("x", "y", "z"));

        assertThat(found).isEmpty();
    }

    @Test
    public void testExistsBulkAllExist() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testMExistsAll");
        cf.init(1000);

        cf.add("a");
        cf.add("b");
        cf.add("c");

        Set<String> found = cf.exists(Arrays.asList("a", "b", "c"));

        assertThat(found).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    public void testRemove() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testRemove");
        cf.init(1000);

        cf.add("foo");
        assertThat(cf.remove("foo")).isTrue();
        assertThat(cf.exists("foo")).isFalse();
    }

    @Test
    public void testRemoveNonExistent() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testRemoveNE");
        cf.init(1000);

        assertThat(cf.remove("doesNotExist")).isFalse();
    }

    @Test
    public void testRemoveOneOfDuplicates() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testRemoveDup");
        cf.init(1000);

        cf.add("foo");
        cf.add("foo");
        assertThat(cf.count("foo")).isEqualTo(2);

        cf.remove("foo");
        assertThat(cf.count("foo")).isEqualTo(1);
        assertThat(cf.exists("foo")).isTrue();
    }

    @Test
    public void testCount() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testCount");
        cf.init(1000);

        assertThat(cf.count("foo")).isEqualTo(0);

        cf.add("foo");
        assertThat(cf.count("foo")).isEqualTo(1);

        cf.add("foo");
        assertThat(cf.count("foo")).isEqualTo(2);

        cf.add("foo");
        assertThat(cf.count("foo")).isEqualTo(3);
    }

    @Test
    public void testCountAfterDelete() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testCountDel");
        cf.init(1000);

        cf.add("bar");
        cf.add("bar");
        cf.remove("bar");

        assertThat(cf.count("bar")).isEqualTo(1);
    }

    @Test
    public void testGetInfo() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInfo");
        cf.init(CuckooFilterInitArgs.capacity(1000)
                .bucketSize(4)
                .maxIterations(100)
                .expansion(2));

        cf.add("a");
        cf.add("b");
        cf.add("c");

        CuckooFilterInfo info = cf.getInfo();

        assertThat(info.getSize()).isGreaterThan(0);
        assertThat(info.getNumberOfBuckets()).isGreaterThan(0);
        assertThat(info.getNumberOfFilters()).isGreaterThanOrEqualTo(1);
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(3);
        assertThat(info.getNumberOfDeletedItems()).isEqualTo(0);
        assertThat(info.getBucketSize()).isEqualTo(4);
        assertThat(info.getExpansionRate()).isEqualTo(2);
        assertThat(info.getMaxIterations()).isEqualTo(100);
    }

    @Test
    public void testGetInfoAfterDelete() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInfoDel");
        cf.init(1000);

        cf.add("a");
        cf.add("b");
        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(2);

        cf.remove("a");

        info = cf.getInfo();
        assertThat(info.getNumberOfDeletedItems()).isEqualTo(1);
    }

    @Test
    public void testGetInfoDefaults() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInfoDefaults");
        cf.init(1000);

        CuckooFilterInfo info = cf.getInfo();

        assertThat(info.getBucketSize()).isEqualTo(2);
        assertThat(info.getMaxIterations()).isEqualTo(20);
        assertThat(info.getExpansionRate()).isEqualTo(1);
    }

    @Test
    public void testAddAsync() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddAsync");
        cf.init(1000);

        Boolean result = cf.addAsync("asyncItem")
                .toCompletableFuture().join();
        assertThat(result).isTrue();

        Boolean exists = cf.existsAsync("asyncItem")
                .toCompletableFuture().join();
        assertThat(exists).isTrue();
    }

    @Test
    public void testAddIfAbsentAsync() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testAddNXAsync");
        cf.init(1000);

        Boolean first = cf.addIfAbsentAsync("asyncNX")
                .toCompletableFuture().join();
        assertThat(first).isTrue();

        Boolean second = cf.addIfAbsentAsync("asyncNX")
                .toCompletableFuture().join();
        assertThat(second).isFalse();
    }

    @Test
    public void testCountAsync() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testCountAsync");
        cf.init(1000);

        cf.add("x");
        cf.add("x");

        Long count = cf.countAsync("x")
                .toCompletableFuture().join();
        assertThat(count).isEqualTo(2);
    }

    @Test
    public void testRemoveAsync() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testRemoveAsync");
        cf.init(1000);

        cf.add("rm");

        Boolean removed = cf.removeAsync("rm")
                .toCompletableFuture().join();
        assertThat(removed).isTrue();

        Boolean exists = cf.existsAsync("rm")
                .toCompletableFuture().join();
        assertThat(exists).isFalse();
    }

    @Test
    public void testGetInfoAsync() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testInfoAsync");
        cf.init(CuckooFilterInitArgs.capacity(500).bucketSize(4));

        cf.add("item1");

        CuckooFilterInfo info = cf.getInfoAsync()
                .toCompletableFuture().join();

        assertThat(info.getNumberOfInsertedItems()).isEqualTo(1);
        assertThat(info.getBucketSize()).isEqualTo(4);
    }

    @Test
    public void testEmptyFilter() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testEmpty");
        cf.init(100);

        assertThat(cf.exists("anything")).isFalse();
        assertThat(cf.count("anything")).isEqualTo(0);
        assertThat(cf.remove("anything")).isFalse();
    }

    @Test
    public void testLargeCapacity() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testLargeCap");
        cf.init(100000);

        for (int i = 0; i < 1000; i++) {
            cf.add("element-" + i);
        }

        for (int i = 0; i < 1000; i++) {
            assertThat(cf.exists("element-" + i)).isTrue();
        }

        CuckooFilterInfo info = cf.getInfo();
        assertThat(info.getNumberOfInsertedItems()).isEqualTo(1000);
    }

    @Test
    public void testSingleItemBulkInsert() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testSingleBulk");
        cf.init(1000);

        Set<String> added = cf.add(
                CuckooFilterAddArgs.<String>items(List.of("only")));

        assertThat(added).containsExactly("only");
    }

    @Test
    public void testBulkExistsSingleItem() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testSingleMExists");
        cf.init(1000);

        cf.add("present");

        Set<String> found = cf.exists(List.of("present"));
        assertThat(found).containsExactly("present");

        Set<String> notFound = cf.exists(List.of("absent"));
        assertThat(notFound).isEmpty();
    }

    @Test
    public void testDeleteKey() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testDeleteKey");
        cf.init(1000);
        cf.add("val");

        assertThat(cf.isExists()).isTrue();
        cf.delete();
        assertThat(cf.isExists()).isFalse();
    }

    @Test
    public void testRename() {
        RCuckooFilter<String> cf = redisson.getCuckooFilter("testRenameSrc");
        cf.init(1000);
        cf.add("val");

        cf.rename("testRenameDst");

        RCuckooFilter<String> cf2 = redisson.getCuckooFilter("testRenameDst");
        assertThat(cf2.exists("val")).isTrue();
    }
}
