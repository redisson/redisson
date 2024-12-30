package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.client.RedisException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBloomFilterTest extends RedisDockerTest {

    @Test
    public void testContainsAll() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(100, 0.03);

        List<String> list = Arrays.asList("1", "2", "3");
        assertThat(filter.contains(list)).isEqualTo(0);
        assertThat(filter.add(list)).isEqualTo(3);
        assertThat(filter.contains(list)).isEqualTo(3);
        assertThat(filter.contains(Arrays.asList("1", "5"))).isEqualTo(1);
    }

    @Test
    public void testAddAll() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(100, 0.03);

        List<String> list = Arrays.asList("1", "2", "3");
        assertThat(filter.add(list)).isEqualTo(3);
        assertThat(filter.add(list)).isZero();
        assertThat(filter.count()).isEqualTo(3);
        assertThat(filter.add(Arrays.asList("1", "5"))).isEqualTo(1);
        assertThat(filter.count()).isEqualTo(4);
        for (String s : list) {
            assertThat(filter.contains(s)).isTrue();
        }

    }

    @Test
    public void testFalseProbability1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RBloomFilter<String> filter = redisson.getBloomFilter("filter");
            filter.tryInit(1, -1);
        });
    }
    
    @Test
    public void testFalseProbability2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RBloomFilter<String> filter = redisson.getBloomFilter("filter");
            filter.tryInit(1, 2);
        });
    }
    
    @Test
    public void testSizeZero() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RBloomFilter<String> filter = redisson.getBloomFilter("filter");
            filter.tryInit(1, 1);
        });
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

    @Test
    public void testNotInitializedOnExpectedInsertions() {
        Assertions.assertThrows(RedisException.class, () -> {
            RBloomFilter<String> filter = redisson.getBloomFilter("filter");
            filter.getExpectedInsertions();
        });

    }

    @Test
    public void testExpire() throws InterruptedException {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(55000000L, 0.03);

        filter.add("test");
        filter.expire(Instant.now().plusSeconds(2));

        Thread.sleep(2100);

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testNotInitializedOnAdd() {
        Assertions.assertThrows(RedisException.class, () -> {
            RBloomFilter<String> filter = redisson.getBloomFilter("filter");
            filter.add("123");
        });
    }

    @Test
    public void testEmptyRename() {
        RBloomFilter<String> bloomFilter = redisson.getBloomFilter("test");
        bloomFilter.tryInit(1000, 0.01);
        bloomFilter.rename("test1");
        assertThat(bloomFilter.isExists()).isTrue();
        assertThat(redisson.getBloomFilter("test").isExists()).isFalse();
    }

    @Test
    public void test() {
        RBloomFilter<String> filter = redisson.getBloomFilter("filter");
        filter.tryInit(550000000L, 0.5);
        test(filter);
        filter.delete();
        assertThat(filter.tryInit(550000000L, 0.03)).isTrue();
        test(filter);
    }

    private void test(RBloomFilter<String> filter) {
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

    @Test
    public void testContainsException() {
        RBloomFilter<String> f1 = redisson.getBloomFilter("filter");
        assertThat(f1.contains("1")).isFalse();
        f1.tryInit(100, 0.03);

        RBloomFilter<String> f2 = redisson.getBloomFilter("filter");
        f2.delete();
        f2.tryInit(200, 0.03);

        assertThat(f1.contains("1")).isFalse();
    }
}
