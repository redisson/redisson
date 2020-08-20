package org.redisson;

import org.junit.Test;
import org.redisson.api.RBloomFilter;

import static org.assertj.core.api.Assertions.*;

public class RedissonBloomFilterTest extends BaseTest {

    @Test(expected = IllegalArgumentException.class)
    public void testFalseProbability1() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(1, -1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFalseProbability2() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(1, 2);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSizeZero() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(1, 1);
    }
    
    @Test
    public void testConfig() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(100, 0.03);
        assertThat(filter.getExpectedInsertions()).isEqualTo(100);
        assertThat(filter.getFalseProbability()).isEqualTo(0.03);
        assertThat(filter.getHashIterations()).isEqualTo(5);
        assertThat(filter.getSize()).isEqualTo(729);
    }

    @Test
    public void testInit() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        assertThat(filter.tryInit(55000000L, 0.03)).isTrue();
        assertThat(filter.tryInit(55000001L, 0.03)).isFalse();

        filter.delete();

        assertThat(redisson.getKeys().count()).isZero();
        assertThat(filter.tryInit(55000001L, 0.03)).isTrue();
    }

    @Test(expected = IllegalStateException.class)
    public void testNotInitializedOnExpectedInsertions() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");

        filter.getExpectedInsertions();
    }

    @Test(expected = IllegalStateException.class)
    public void testNotInitializedOnContains() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");

        filter.contains("32");
    }

    @Test(expected = IllegalStateException.class)
    public void testNotInitializedOnAdd() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");

        filter.add("123");
    }

    @Test
    public void test() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(550000000L, 0.03);

        assertThat(filter.contains("123")).isFalse();
        assertThat(filter.add("123")).isTrue();
        assertThat(filter.contains("123")).isTrue();
        assertThat(filter.add("123")).isFalse();
        assertThat(filter.count()).isEqualTo(1);

        assertThat(filter.contains("hflgs;jl;ao1-32471320o31803-24")).isFalse();
        assertThat(filter.add("hflgs;jl;ao1-32471320o31803-24")).isTrue();
        assertThat(filter.contains("hflgs;jl;ao1-32471320o31803-24")).isTrue();
        assertThat(filter.count()).isEqualTo(2);
    }

    @Test
    public void testRename() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(550000000L, 0.03);
        assertThat(filter.add("123")).isTrue();
        filter.rename("new_filter");

        RBloomFilter<String> filter2 = redisson.getBloomFilter("new_filter");
        assertThat(filter2.count()).isEqualTo(1);

        RBloomFilter<String> filter3 = redisson.getBloomFilter("filter");
        assertThat(filter3.isExists()).isFalse();
    }

    @Test
    public void testRenamenx() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(550000000L, 0.03);
        assertThat(filter.add("123")).isTrue();
        assertThat(filter.contains("123")).isTrue();

        RBloomFilter<String> filter2 = redisson.getBloomFilter("filter2");
        filter2.tryInit(550000000L, 0.03);
        assertThat(filter2.add("234")).isTrue();

        assertThat(filter.renamenx("filter2")).isFalse();
        assertThat(filter.count()).isEqualTo(1);

        assertThat(filter.renamenx("new_filter")).isTrue();
        RBloomFilter<String> oldFilter = redisson.getBloomFilter("filter");
        assertThat(oldFilter.isExists()).isFalse();

        RBloomFilter<String> newFilter = redisson.getBloomFilter("new_filter");
        assertThat(newFilter.count()).isEqualTo(1);
        assertThat(newFilter.contains("123")).isTrue();
    }
}
