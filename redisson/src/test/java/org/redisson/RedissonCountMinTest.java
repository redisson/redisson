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
import org.redisson.api.CountMinInfo;
import org.redisson.api.RBatch;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RCountMin;
import org.redisson.api.RCountMinAsync;
import org.redisson.api.RCountMinReactive;
import org.redisson.api.RCountMinRx;
import org.redisson.api.RFuture;
import org.redisson.api.countmin.CountMinInitArgs;
import org.redisson.api.countmin.CountMinMergeArgs;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCountMinTest extends RedisDockerTest {

    @Test
    public void testInitByDimensions() {
        RCountMin<String> cms = redisson.getCountMin("testInitByDim");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        CountMinInfo info = cms.getInfo();
        assertThat(info.getWidth()).isEqualTo(2000);
        assertThat(info.getDepth()).isEqualTo(5);
        assertThat(info.getCount()).isEqualTo(0);
    }

    @Test
    public void testInitByProbability() {
        RCountMin<String> cms = redisson.getCountMin("testInitByProb");
        cms.init(CountMinInitArgs.probability(0.001, 0.01));

        CountMinInfo info = cms.getInfo();
        assertThat(info.getWidth()).isGreaterThan(0);
        assertThat(info.getDepth()).isGreaterThan(0);
        assertThat(info.getCount()).isEqualTo(0);
    }

    @Test
    public void testInitDuplicate() {
        RCountMin<String> cms = redisson.getCountMin("testInitDup");
        cms.init(CountMinInitArgs.dimensions(100, 5));

        Assertions.assertThrows(RedisException.class, () -> {
            cms.init(CountMinInitArgs.dimensions(100, 5));
        });
    }

    @Test
    public void testAddWithoutInit() {
        RCountMin<String> cms = redisson.getCountMin("testAddNoInit");

        Assertions.assertThrows(RedisException.class, () -> {
            cms.add("element");
        });
    }

    @Test
    public void testCountWithoutInit() {
        RCountMin<String> cms = redisson.getCountMin("testCountNoInit");

        Assertions.assertThrows(RedisException.class, () -> {
            cms.count("element");
        });
    }

    @Test
    public void testAdd() {
        RCountMin<String> cms = redisson.getCountMin("testAdd");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        assertThat(cms.add("foo")).isEqualTo(1);
        assertThat(cms.add("foo")).isEqualTo(2);
        assertThat(cms.add("bar")).isEqualTo(1);

        assertThat(cms.count("foo")).isEqualTo(2);
        assertThat(cms.count("bar")).isEqualTo(1);
    }

    @Test
    public void testAddWithIncrement() {
        RCountMin<String> cms = redisson.getCountMin("testAddIncr");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        assertThat(cms.add("foo", 10)).isEqualTo(10);
        assertThat(cms.add("foo", 5)).isEqualTo(15);

        assertThat(cms.count("foo")).isEqualTo(15);
    }

    @Test
    public void testAddBulk() {
        RCountMin<String> cms = redisson.getCountMin("testAddBulk");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        Map<String, Long> increments = new LinkedHashMap<>();
        increments.put("foo", 10L);
        increments.put("bar", 42L);

        Map<String, Long> counts = cms.add(increments);

        assertThat(counts).containsExactly(
                Map.entry("foo", 10L),
                Map.entry("bar", 42L));
    }

    @Test
    public void testAddBulkAccumulates() {
        RCountMin<String> cms = redisson.getCountMin("testAddBulkAcc");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        cms.add(Map.of("foo", 3L));
        Map<String, Long> counts = cms.add(Map.of("foo", 4L));

        assertThat(counts).containsEntry("foo", 7L);
        assertThat(cms.count("foo")).isEqualTo(7);
    }

    @Test
    public void testAddBulkSingleElement() {
        RCountMin<String> cms = redisson.getCountMin("testAddBulkSingle");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        Map<String, Long> counts = cms.add(Map.of("only", 7L));

        assertThat(counts).containsExactly(Map.entry("only", 7L));
    }

    @Test
    public void testCountUnknownElement() {
        RCountMin<String> cms = redisson.getCountMin("testCountUnknown");
        cms.init(CountMinInitArgs.dimensions(2000, 5));
        cms.add("foo");

        assertThat(cms.count("never-added")).isEqualTo(0);
    }

    @Test
    public void testCountBulk() {
        RCountMin<String> cms = redisson.getCountMin("testCountBulk");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        cms.add("foo", 10);
        cms.add("bar", 42);

        Map<String, Long> counts = cms.count(List.of("foo", "bar", "absent"));

        assertThat(counts).containsExactly(
                Map.entry("foo", 10L),
                Map.entry("bar", 42L),
                Map.entry("absent", 0L));
    }

    @Test
    public void testCountBulkSingleElement() {
        RCountMin<String> cms = redisson.getCountMin("testCountBulkSingle");
        cms.init(CountMinInitArgs.dimensions(2000, 5));
        cms.add("only", 3);

        assertThat(cms.count(List.of("only"))).containsExactly(Map.entry("only", 3L));
    }

    @Test
    public void testCountNeverUnderestimates() {
        RCountMin<String> cms = redisson.getCountMin("testNoUnderestimate");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        for (int i = 0; i < 500; i++) {
            cms.add("element-" + i, i);
        }

        for (int i = 0; i < 500; i++) {
            assertThat(cms.count("element-" + i)).isGreaterThanOrEqualTo(i);
        }
    }

    @Test
    public void testInfoCount() {
        RCountMin<String> cms = redisson.getCountMin("testInfoCount");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        cms.add("foo", 10);
        cms.add("bar", 32);

        assertThat(cms.getInfo().getCount()).isEqualTo(42);
    }

    @Test
    public void testMerge() {
        RCountMin<String> source1 = redisson.getCountMin("testMergeSrc1");
        RCountMin<String> source2 = redisson.getCountMin("testMergeSrc2");
        RCountMin<String> dest = redisson.getCountMin("testMergeDest");

        source1.init(CountMinInitArgs.dimensions(2000, 5));
        source2.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        source1.add("foo", 10);
        source2.add("foo", 5);
        source2.add("bar", 3);

        dest.mergeWith(CountMinMergeArgs.sources("testMergeSrc1", "testMergeSrc2"));

        assertThat(dest.count("foo")).isEqualTo(15);
        assertThat(dest.count("bar")).isEqualTo(3);
    }

    @Test
    public void testMergeWithWeights() {
        RCountMin<String> source1 = redisson.getCountMin("testMergeWSrc1");
        RCountMin<String> source2 = redisson.getCountMin("testMergeWSrc2");
        RCountMin<String> dest = redisson.getCountMin("testMergeWDest");

        source1.init(CountMinInitArgs.dimensions(2000, 5));
        source2.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        source1.add("foo", 10);
        source2.add("foo", 5);

        dest.mergeWith(CountMinMergeArgs.sources("testMergeWSrc1", "testMergeWSrc2")
                .weights(2, 3));

        assertThat(dest.count("foo")).isEqualTo(10 * 2 + 5 * 3);
    }

    @Test
    public void testMergeSingleSource() {
        RCountMin<String> source = redisson.getCountMin("testMergeOneSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeOneDest");

        source.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        source.add("foo", 7);

        dest.mergeWith(CountMinMergeArgs.sources(List.of("testMergeOneSrc")));

        assertThat(dest.count("foo")).isEqualTo(7);
    }

    @Test
    public void testMergeDiscardsExistingCounts() {
        RCountMin<String> source = redisson.getCountMin("testMergeOverSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeOverDest");

        source.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        dest.add("foo", 4);
        source.add("foo", 6);

        dest.mergeWith(CountMinMergeArgs.sources("testMergeOverSrc"));

        // CMS_Merge assigns into the destination array rather than adding to it,
        // so the 4 counted directly into dest is gone, not summed.
        assertThat(dest.count("foo")).isEqualTo(6);
        assertThat(dest.getInfo().getCount()).isEqualTo(6);
    }

    @Test
    public void testMergeAccumulatesWhenDestinationIsAlsoSource() {
        RCountMin<String> source = redisson.getCountMin("testMergeSelfSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeSelfDest");

        source.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        dest.add("foo", 4);
        source.add("foo", 6);

        dest.mergeWith(CountMinMergeArgs.sources("testMergeSelfDest", "testMergeSelfSrc"));

        assertThat(dest.count("foo")).isEqualTo(10);
    }

    @Test
    public void testMergeIsIdempotentOnRepeat() {
        RCountMin<String> source = redisson.getCountMin("testMergeIdemSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeIdemDest");

        source.init(CountMinInitArgs.dimensions(2000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        source.add("foo", 6);

        dest.mergeWith(CountMinMergeArgs.sources("testMergeIdemSrc"));
        dest.mergeWith(CountMinMergeArgs.sources("testMergeIdemSrc"));

        assertThat(dest.count("foo")).isEqualTo(6);
    }

    @Test
    public void testMergeDimensionMismatch() {
        RCountMin<String> source = redisson.getCountMin("testMergeBadSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeBadDest");

        source.init(CountMinInitArgs.dimensions(1000, 5));
        dest.init(CountMinInitArgs.dimensions(2000, 5));

        Assertions.assertThrows(RedisException.class, () -> {
            dest.mergeWith(CountMinMergeArgs.sources("testMergeBadSrc"));
        });
    }

    @Test
    public void testMergeIntoNotInitializedSketch() {
        RCountMin<String> source = redisson.getCountMin("testMergeNoInitSrc");
        RCountMin<String> dest = redisson.getCountMin("testMergeNoInitDest");

        source.init(CountMinInitArgs.dimensions(2000, 5));

        Assertions.assertThrows(RedisException.class, () -> {
            dest.mergeWith(CountMinMergeArgs.sources("testMergeNoInitSrc"));
        });
    }

    @Test
    public void testMergeArgsRejectsEmptySources() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CountMinMergeArgs.sources();
        });
    }

    @Test
    public void testMergeArgsRejectsNullSources() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            CountMinMergeArgs.sources((String[]) null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            CountMinMergeArgs.sources((java.util.Collection<String>) null);
        });
    }

    @Test
    public void testMergeArgsRejectsWeightsMismatch() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CountMinMergeArgs.sources("a", "b").weights(1);
        });
    }

    @Test
    public void testCustomCodec() {
        RCountMin<String> cms = redisson.getCountMin("testCodec", StringCodec.INSTANCE);
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        cms.add("foo", 9);

        assertThat(cms.count("foo")).isEqualTo(9);
    }

    @Test
    public void testNonStringElements() {
        RCountMin<Integer> cms = redisson.getCountMin("testIntElements");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        cms.add(1, 5);
        cms.add(2, 7);

        assertThat(cms.count(1)).isEqualTo(5);
        assertThat(cms.count(List.of(1, 2))).containsExactly(
                Map.entry(1, 5L),
                Map.entry(2, 7L));
    }

    @Test
    public void testExpire() {
        RCountMin<String> cms = redisson.getCountMin("testExpire");
        cms.init(CountMinInitArgs.dimensions(2000, 5));
        cms.add("foo");

        assertThat(cms.expire(Duration.ofSeconds(10))).isTrue();
        assertThat(cms.remainTimeToLive()).isGreaterThan(0);
    }

    @Test
    public void testDeleteKey() {
        RCountMin<String> cms = redisson.getCountMin("testDeleteKey");
        cms.init(CountMinInitArgs.dimensions(2000, 5));
        cms.add("foo");

        assertThat(cms.isExists()).isTrue();
        cms.delete();
        assertThat(cms.isExists()).isFalse();
    }

    @Test
    public void testRename() {
        RCountMin<String> cms = redisson.getCountMin("testRenameSrc");
        cms.init(CountMinInitArgs.dimensions(2000, 5));
        cms.add("foo", 6);

        cms.rename("testRenameDst");

        RCountMin<String> renamed = redisson.getCountMin("testRenameDst");
        assertThat(renamed.count("foo")).isEqualTo(6);
    }

    @Test
    public void testInitArgsRejectsBadDimensions() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CountMinInitArgs.dimensions(0, 5);
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CountMinInitArgs.dimensions(2000, 0);
        });
    }

    @Test
    public void testInitArgsRejectsBadProbability() {
        // the module excludes both bounds, so 0 and 1 are rejected too
        for (double bad : new double[]{0.0, 1.0, -0.1, 1.5}) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                CountMinInitArgs.probability(bad, 0.01);
            });
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                CountMinInitArgs.probability(0.001, bad);
            });
        }
    }

    @Test
    public void testAddRejectsNegativeIncrement() {
        RCountMin<String> cms = redisson.getCountMin("testNegIncr");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            cms.add("foo", -1);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            cms.add(Map.of("foo", -1L));
        });

        // nothing was sent, so the sketch is untouched
        assertThat(cms.count("foo")).isEqualTo(0);
    }

    @Test
    public void testBulkAddRejectsNullIncrement() {
        RCountMin<String> cms = redisson.getCountMin("testNullIncr");
        cms.init(CountMinInitArgs.dimensions(2000, 5));

        Map<String, Long> increments = new LinkedHashMap<>();
        increments.put("foo", null);

        Assertions.assertThrows(NullPointerException.class, () -> {
            cms.add(increments);
        });
    }

    @Test
    public void testBatch() {
        RBatch batch = redisson.createBatch();
        RCountMinAsync<String> cms = batch.getCountMin("testBatch");

        cms.initAsync(CountMinInitArgs.dimensions(2000, 5));
        cms.addAsync("foo", 10);
        cms.addAsync("bar", 4);
        RFuture<Long> foo = cms.countAsync("foo");
        RFuture<Map<String, Long>> both = cms.countAsync(List.of("foo", "bar"));
        RFuture<CountMinInfo> info = cms.getInfoAsync();

        batch.execute();

        assertThat(foo.toCompletableFuture().join()).isEqualTo(10);
        assertThat(both.toCompletableFuture().join()).containsExactly(
                Map.entry("foo", 10L),
                Map.entry("bar", 4L));
        assertThat(info.toCompletableFuture().join().getCount()).isEqualTo(14);
    }

    @Test
    public void testBatchReactive() {
        RBatchReactive batch = redisson.reactive().createBatch();
        RCountMinReactive<String> cms = batch.getCountMin("testBatchReactive", StringCodec.INSTANCE);

        cms.init(CountMinInitArgs.dimensions(2000, 5)).subscribe();
        cms.add("foo", 7).subscribe();
        Mono<Long> foo = cms.count("foo");
        foo.subscribe();

        batch.execute().block();

        assertThat(foo.block()).isEqualTo(7);
    }

    @Test
    public void testReactive() {
        RCountMinReactive<String> cms =
                redisson.reactive().getCountMin("testReactive", StringCodec.INSTANCE);

        cms.init(CountMinInitArgs.dimensions(2000, 5)).block();

        assertThat(cms.add("foo", 10).block()).isEqualTo(10);
        assertThat(cms.count("foo").block()).isEqualTo(10);
        assertThat(cms.count(List.of("foo", "absent")).block()).containsExactly(
                Map.entry("foo", 10L),
                Map.entry("absent", 0L));
        assertThat(cms.getInfo().block().getCount()).isEqualTo(10);
    }

    @Test
    public void testRx() {
        RCountMinRx<String> cms =
                redisson.rxJava().getCountMin("testRx", StringCodec.INSTANCE);

        cms.init(CountMinInitArgs.dimensions(2000, 5)).blockingAwait();

        assertThat(cms.add("foo", 10).blockingGet()).isEqualTo(10);
        assertThat(cms.count("foo").blockingGet()).isEqualTo(10);
        assertThat(cms.count(List.of("foo", "absent")).blockingGet()).containsExactly(
                Map.entry("foo", 10L),
                Map.entry("absent", 0L));
        assertThat(cms.getInfo().blockingGet().getCount()).isEqualTo(10);
    }
}
