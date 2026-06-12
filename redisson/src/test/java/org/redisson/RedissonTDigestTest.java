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
import org.redisson.api.RTDigest;
import org.redisson.api.TDigestInfo;
import org.redisson.api.tdigest.TDigestMergeArgs;
import org.redisson.client.RedisException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTDigestTest extends RedisDockerTest {

    @Test
    public void testCreate() {
        RTDigest t = redisson.getTDigest("testCreate");
        t.create();

        TDigestInfo info = t.getInfo();
        assertThat(info.getCompression()).isEqualTo(100);
        assertThat(info.getObservations()).isEqualTo(0);
    }

    @Test
    public void testCreateWithCompression() {
        RTDigest t = redisson.getTDigest("testCreateCompression");
        t.create(200);

        TDigestInfo info = t.getInfo();
        assertThat(info.getCompression()).isEqualTo(200);
    }

    @Test
    public void testCreateDuplicate() {
        RTDigest t = redisson.getTDigest("testCreateDup");
        t.create();

        Assertions.assertThrows(RedisException.class, t::create);
    }

    @Test
    public void testReset() {
        RTDigest t = redisson.getTDigest("testReset");
        t.create();
        t.add(1, 2, 3, 4, 5);
        assertThat(t.getInfo().getObservations()).isEqualTo(5);

        t.reset();
        assertThat(t.getInfo().getObservations()).isEqualTo(0);
    }

    @Test
    public void testAddSingle() {
        RTDigest t = redisson.getTDigest("testAddSingle");
        t.create();

        t.add(42);

        assertThat(t.getInfo().getObservations()).isEqualTo(1);
        assertThat(t.getMin()).isEqualTo(42);
        assertThat(t.getMax()).isEqualTo(42);
    }

    @Test
    public void testAddMultiple() {
        RTDigest t = redisson.getTDigest("testAddMultiple");
        t.create();

        t.add(1, 2, 3, 4, 5);

        assertThat(t.getInfo().getObservations()).isEqualTo(5);
    }

    @Test
    public void testMinMax() {
        RTDigest t = redisson.getTDigest("testMinMax");
        t.create();
        t.add(5, 1, 3, 2, 4);

        assertThat(t.getMin()).isEqualTo(1);
        assertThat(t.getMax()).isEqualTo(5);
    }

    @Test
    public void testMinMaxEmpty() {
        RTDigest t = redisson.getTDigest("testMinMaxEmpty");
        t.create();

        assertThat(t.getMin()).isNaN();
        assertThat(t.getMax()).isNaN();
    }

    @Test
    public void testQuantile() {
        RTDigest t = redisson.getTDigest("testQuantile");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> result = t.quantile(0, 0.5, 1);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(1);
        assertThat(result.get(1)).isCloseTo(3, within(1.0));
        assertThat(result.get(2)).isEqualTo(5);
    }

    @Test
    public void testQuantileEmpty() {
        RTDigest t = redisson.getTDigest("testQuantileEmpty");
        t.create();

        List<Double> result = t.quantile(0.1, 0.9);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> Double.isNaN(r));
    }

    @Test
    public void testCdf() {
        RTDigest t = redisson.getTDigest("testCdf");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> result = t.cumulativeProbability(3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isCloseTo(0.5, within(0.2));
    }

    @Test
    public void testTrimmedMean() {
        RTDigest t = redisson.getTDigest("testTrimmedMean");
        t.create();
        t.add(1, 2, 3, 4, 5);

        assertThat(t.trimmedMean(0, 1)).isCloseTo(3, within(0.001));
        assertThat(t.trimmedMean(0.2, 0.8)).isCloseTo(3, within(0.5));
    }

    @Test
    public void testRank() {
        RTDigest t = redisson.getTDigest("testRank");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Long> ranks = t.rank(0, 3, 6);

        assertThat(ranks).hasSize(3);
        // value below every observation
        assertThat(ranks.get(0)).isEqualTo(-1);
        // observations strictly less than 3 -> {1, 2}
        assertThat(ranks.get(1)).isBetween(1L, 3L);
        // value above every observation -> total count
        assertThat(ranks.get(2)).isEqualTo(5);
    }

    @Test
    public void testRevRank() {
        RTDigest t = redisson.getTDigest("testRevRank");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Long> ranks = t.revRank(6, 3, 0);

        assertThat(ranks).hasSize(3);
        // value above every observation
        assertThat(ranks.get(0)).isEqualTo(-1);
        // observations strictly greater than 3 -> {4, 5}
        assertThat(ranks.get(1)).isBetween(1L, 3L);
        // value below every observation -> total count
        assertThat(ranks.get(2)).isEqualTo(5);
    }

    @Test
    public void testByRank() {
        RTDigest t = redisson.getTDigest("testByRank");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> values = t.byRank(0, 2, 4);

        assertThat(values).hasSize(3);
        assertThat(values.get(0)).isCloseTo(1, within(0.5));
        assertThat(values.get(1)).isCloseTo(3, within(0.5));
        assertThat(values.get(2)).isCloseTo(5, within(0.5));
    }

    @Test
    public void testByRankOutOfRange() {
        RTDigest t = redisson.getTDigest("testByRankOOR");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> values = t.byRank(100);

        assertThat(values).hasSize(1);
        assertThat(values.get(0)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    public void testByRevRank() {
        RTDigest t = redisson.getTDigest("testByRevRank");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> values = t.byRevRank(0, 2, 4);

        assertThat(values).hasSize(3);
        assertThat(values.get(0)).isCloseTo(5, within(0.5));
        assertThat(values.get(1)).isCloseTo(3, within(0.5));
        assertThat(values.get(2)).isCloseTo(1, within(0.5));
    }

    @Test
    public void testByRevRankOutOfRange() {
        RTDigest t = redisson.getTDigest("testByRevRankOOR");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> values = t.byRevRank(100);

        assertThat(values).hasSize(1);
        assertThat(values.get(0)).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    public void testMergeWith() {
        RTDigest s1 = redisson.getTDigest("testMergeS1");
        s1.create();
        s1.add(1, 2, 3, 4, 5);

        RTDigest s2 = redisson.getTDigest("testMergeS2");
        s2.create();
        s2.add(6, 7, 8, 9, 10);

        RTDigest dest = redisson.getTDigest("testMergeDest");
        dest.mergeWith("testMergeS1", "testMergeS2");

        assertThat(dest.getMin()).isEqualTo(1);
        assertThat(dest.getMax()).isEqualTo(10);
        assertThat(dest.getInfo().getObservations()).isEqualTo(10);

        List<Double> values = dest.byRank(0, 9);
        assertThat(values.get(0)).isCloseTo(1, within(0.5));
        assertThat(values.get(1)).isCloseTo(10, within(0.5));
    }

    @Test
    public void testMergeWithCompression() {
        RTDigest s1 = redisson.getTDigest("testMergeCompS1");
        s1.create();
        s1.add(1, 2, 3);

        RTDigest dest = redisson.getTDigest("testMergeCompDest");
        dest.mergeWith(TDigestMergeArgs.keys("testMergeCompS1").compression(200));

        assertThat(dest.getInfo().getCompression()).isEqualTo(200);
    }

    @Test
    public void testMergeWithOverride() {
        RTDigest s1 = redisson.getTDigest("testMergeOverS1");
        s1.create();
        s1.add(1, 2, 3, 4, 5);

        RTDigest dest = redisson.getTDigest("testMergeOverDest");
        dest.create();
        dest.add(1000);

        dest.mergeWith(TDigestMergeArgs.keys("testMergeOverS1").override());

        // override discards the pre-existing 1000 observation
        assertThat(dest.getMax()).isEqualTo(5);
    }

    @Test
    public void testMergeWithoutOverride() {
        RTDigest s1 = redisson.getTDigest("testMergeNoOverS1");
        s1.create();
        s1.add(1, 2, 3, 4, 5);

        RTDigest dest = redisson.getTDigest("testMergeNoOverDest");
        dest.create();
        dest.add(1000);

        dest.mergeWith("testMergeNoOverS1");

        // without override the destination keeps its own observations
        assertThat(dest.getMax()).isEqualTo(1000);
    }

    @Test
    public void testGetInfo() {
        RTDigest t = redisson.getTDigest("testGetInfo");
        t.create(120);
        t.add(1, 2, 3, 4, 5);

        TDigestInfo info = t.getInfo();

        assertThat(info.getCompression()).isEqualTo(120);
        assertThat(info.getCapacity()).isGreaterThan(0);
        assertThat(info.getObservations()).isEqualTo(5);
        assertThat(info.getMemoryUsage()).isGreaterThan(0);
        assertThat(info.getMergedNodes() + info.getUnmergedNodes()).isGreaterThan(0);
    }

    @Test
    public void testAddAsync() {
        RTDigest t = redisson.getTDigest("testAddAsync");
        t.create();

        t.addAsync(1, 2, 3, 4, 5).toCompletableFuture().join();

        Double max = t.getMaxAsync().toCompletableFuture().join();
        assertThat(max).isEqualTo(5);
    }

    @Test
    public void testQuantileAsync() {
        RTDigest t = redisson.getTDigest("testQuantileAsync");
        t.create();
        t.add(1, 2, 3, 4, 5);

        List<Double> result = t.quantileAsync(0, 1).toCompletableFuture().join();

        assertThat(result.get(0)).isEqualTo(1);
        assertThat(result.get(1)).isEqualTo(5);
    }

    @Test
    public void testGetInfoAsync() {
        RTDigest t = redisson.getTDigest("testInfoAsync");
        t.create();
        t.add(7);

        TDigestInfo info = t.getInfoAsync().toCompletableFuture().join();
        assertThat(info.getObservations()).isEqualTo(1);
    }

    @Test
    public void testDeleteKey() {
        RTDigest t = redisson.getTDigest("testDeleteKey");
        t.create();
        t.add(1);

        assertThat(t.isExists()).isTrue();
        t.delete();
        assertThat(t.isExists()).isFalse();
    }

    @Test
    public void testRename() {
        RTDigest t = redisson.getTDigest("testRenameSrc");
        t.create();
        t.add(1, 2, 3);

        t.rename("testRenameDst");

        RTDigest t2 = redisson.getTDigest("testRenameDst");
        assertThat(t2.getInfo().getObservations()).isEqualTo(3);
    }
}
