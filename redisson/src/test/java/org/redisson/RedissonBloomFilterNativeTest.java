package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilterNative;
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.api.bloomfilter.BloomFilterInfoOption;
import org.redisson.api.bloomfilter.BloomFilterInitOptions;
import org.redisson.api.bloomfilter.BloomFilterInsertOptions;
import org.redisson.client.RedisException;

public class RedissonBloomFilterNativeTest extends RedisDockerTest {

    @Test
    public void testTryInit() {
        RBloomFilterNative<Object> initFilter = redisson.getBloomFilterNative("init");
        initFilter.init(0.01, 1000);

        RBloomFilterNative<Object> optionFilter = redisson.getBloomFilterNative("option");
        BloomFilterInitOptions options = new BloomFilterInitOptions(0.01, 1000, 2L);
        optionFilter.init(options);
        assertThat(optionFilter.delete()).isTrue();

        options = new BloomFilterInitOptions(0.01, 1000, true);
        optionFilter.init(options);
        assertThat(optionFilter.delete()).isTrue();

        options = new BloomFilterInitOptions(0.01, 1000);
        optionFilter.init(options);
        assertThat(optionFilter.delete()).isTrue();
    }

    @Test
    public void testInitFailure() {
        RBloomFilterNative<Object> bloomFilterNative = redisson.getBloomFilterNative("init_failure");

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            bloomFilterNative.init(0, 1000);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            bloomFilterNative.init(1, 1000);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            bloomFilterNative.init(2, 0);
        });

        bloomFilterNative.init(0.5, 14);
        Assertions.assertThrows(RedisException.class, () -> {
            bloomFilterNative.init(0.5, 14);
        });
    }

    @Test
    public void testInitFailure2() {
        RBloomFilterNative<Object> optionFilter = redisson.getBloomFilterNative("option_failure");

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            optionFilter.init(new BloomFilterInitOptions(0, 1000, 2L));
        });
        assertThat(optionFilter.delete()).isFalse();

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            optionFilter.init(new BloomFilterInitOptions(1, 1000, 2L));
        });
        assertThat(optionFilter.delete()).isFalse();

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            optionFilter.init(new BloomFilterInitOptions(2, 1000, 1L));
        });
        assertThat(optionFilter.delete()).isFalse();
    }

    @Test
    public void testInfo(){
        RBloomFilterNative<Object> bloomFilterNative = redisson.getBloomFilterNative("info");
        bloomFilterNative.init(new BloomFilterInitOptions(0.1, 1000, 2L));

        BloomFilterInfo info = bloomFilterNative.getInfo();

        assertThat(info.getCapacity()).isEqualTo(1000);
        assertThat(info.getSize()).isGreaterThan(0);
        assertThat(info.getSubFilterCount()).isEqualTo(1);
        assertThat(info.getExpansionRate()).isEqualTo(2L);
        assertThat(info.getItemCount()).isEqualTo(0);

        assertThat(bloomFilterNative.getInfo(BloomFilterInfoOption.CAPACITY)).isEqualTo(1000);
        assertThat(bloomFilterNative.getInfo(BloomFilterInfoOption.SIZE)).isGreaterThan(0);
        assertThat(bloomFilterNative.getInfo(BloomFilterInfoOption.FILTERS)).isEqualTo(1);
        assertThat(bloomFilterNative.getInfo(BloomFilterInfoOption.EXPANSION)).isEqualTo(2L);
        assertThat(bloomFilterNative.getInfo(BloomFilterInfoOption.ITEMS)).isEqualTo(0);
    }

    @Test
    public void testInfoFailure(){
        RBloomFilterNative<Object> bloomFilterNative = redisson.getBloomFilterNative("info_failure");

        Assertions.assertThrows(RedisException.class, bloomFilterNative::getInfo);
        Assertions.assertThrows(RedisException.class, () -> bloomFilterNative.getInfo(BloomFilterInfoOption.CAPACITY));
    }

    @Test
    public void testCount(){
        RBloomFilterNative<Object> bloomFilterNative = redisson.getBloomFilterNative("count");
        assertThat(bloomFilterNative.count()).isEqualTo(0L);
        bloomFilterNative.init(new BloomFilterInitOptions(0.1, 1000, 2L));
        assertThat(bloomFilterNative.count()).isEqualTo(0L);
    }

    @Test
    public void testAdd(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("add");

        // if not initialized, it will be initialized with default filter
        assertThat(bf.add("1")).isTrue();
        assertThat(bf.count()).isEqualTo(1);
        assertThat(bf.delete()).isTrue();
        assertThat(bf.count()).isEqualTo(0);

        bf.init(0.5, 10);
        assertThat(bf.add("1")).isTrue();
        assertThat(bf.count()).isEqualTo(1);
        assertThat(bf.add("1")).isFalse();
    }

    @Test
    public void testMultiAdd(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("multi_dd");

        // if not initialized, it will be initialized with default filter
        assertThat(bf.add(List.of("1","2","3"))).contains(true, true, true);
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.delete()).isTrue();
        assertThat(bf.count()).isEqualTo(0);

        bf.init(0.01, 100);
        assertThat(bf.add(List.of("1","2","3"))).contains(true, true, true);
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.add(List.of("4","2","6"))).contains(true, false, true);
    }

    @Test
    public void testExists(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("exists");
        bf.init(0.5, 10);

        assertThat(bf.exists("1")).isFalse();
        assertThat(bf.add("1")).isTrue();
        assertThat(bf.exists("1")).isTrue();
    }

    @Test
    public void testMultiExists(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("multi_exists");
        bf.init(0.5, 10);

        assertThat(bf.add(List.of("1","2","3"))).contains(true, true, true);
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.exists(List.of("4","2","6"))).contains(false, true, false);
    }

    @Test
    public void testInsert(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("insert");
        bf.init(0.001, 10);

        bf.insert(new BloomFilterInsertOptions(2L, false, false), List.of("1","2","3"));
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.getInfo(BloomFilterInfoOption.EXPANSION)).isEqualTo(2);

        // insert is only creating new filter if filter is not existing
        bf.insert(new BloomFilterInsertOptions(4L, false, false), List.of("4","5","6"));
        assertThat(bf.count()).isEqualTo(6);
        assertThat(bf.getInfo(BloomFilterInfoOption.EXPANSION)).isEqualTo(2);
    }

    @Test
    public void testOverCapacity(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("over_capacity");
        bf.init(0.01, 3);

        assertThat(bf.add("1")).isTrue();
        assertThat(bf.add("2")).isTrue();
        assertThat(bf.add("3")).isTrue();
        assertThat(bf.count()).isEqualTo(3);

        // adding 4th element should create new sub-filter
        assertThat(bf.add("4")).isTrue();
        assertThat(bf.count()).isEqualTo(4);
        assertThat(bf.getInfo(BloomFilterInfoOption.FILTERS)).isEqualTo(2);
    }
}
