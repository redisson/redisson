package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilterNative;
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.api.bloomfilter.BloomFilterInfoOption;
import org.redisson.api.bloomfilter.BloomFilterInitArgs;
import org.redisson.api.bloomfilter.BloomFilterInitParams;
import org.redisson.api.bloomfilter.BloomFilterInsertArgs;
import org.redisson.api.bloomfilter.BloomFilterScanDumpInfo;
import org.redisson.client.RedisException;

public class RedissonBloomFilterNativeTest extends RedisDockerTest {

    @Test
    public void testInit() {
        RBloomFilterNative<Object> initFilter = redisson.getBloomFilterNative("init");
        initFilter.init(0.01, 1000);

        RBloomFilterNative<Object> optionFilter = redisson.getBloomFilterNative("option");
        BloomFilterInitParams options = (BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0.01).capacity(1000).expansionRate(2L);
        optionFilter.init(options);
        assertThat(optionFilter.delete()).isTrue();

        options = (BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0.01).capacity(1000).nonScaling(true);
        optionFilter.init(options);
        assertThat(optionFilter.delete()).isTrue();

        options = (BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0.01).capacity(1000);
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
            optionFilter.init((BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0).capacity(1000).expansionRate(2L));
        });
        assertThat(optionFilter.delete()).isFalse();

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            optionFilter.init((BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(1).capacity(1000).expansionRate(2L));
        });
        assertThat(optionFilter.delete()).isFalse();

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            optionFilter.init((BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(2).capacity(1000).expansionRate(1L));
        });
        assertThat(optionFilter.delete()).isFalse();
    }

    @Test
    public void testInfo(){
        RBloomFilterNative<Object> bloomFilterNative = redisson.getBloomFilterNative("info");
        bloomFilterNative.init((BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0.1).capacity(1000).expansionRate(2L));

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
        bloomFilterNative.init((BloomFilterInitParams) BloomFilterInitArgs.create().errorRate(0.1).capacity(1000).expansionRate(2L));
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
        assertThat(bf.add(List.of("1","2","3"))).contains("1","2","3");
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.delete()).isTrue();
        assertThat(bf.count()).isEqualTo(0);

        bf.init(0.01, 100);
        assertThat(bf.add(List.of("1","2","3"))).contains("1","2","3");
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.add(List.of("4","2","6"))).contains("4","6").doesNotContain("2");
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

        assertThat(bf.add(List.of("1","2","3"))).contains("1","2","3");
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.exists(List.of("4","2","6"))).contains("2").doesNotContain("4","6");
    }

    @Test
    public void testInsert(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("insert");
        bf.init(0.001, 10);
        BloomFilterInsertArgs args = (BloomFilterInsertArgs) BloomFilterInsertArgs
                .elements(List.of("1", "2", "3"))
                .expansionRate(2L)
                .nonScaling(false)
                .noCreate(false);

        bf.insert(args);
        assertThat(bf.count()).isEqualTo(3);
        assertThat(bf.getInfo(BloomFilterInfoOption.EXPANSION)).isEqualTo(2);

        // insert is only creating new filter if filter is not existing
        args = (BloomFilterInsertArgs) BloomFilterInsertArgs
                .elements(List.of("4", "5", "6"))
                .expansionRate(4L)
                .nonScaling(false)
                .noCreate(false);
        bf.insert(args);
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

    @Test
    public void testScanDumpAndLoadChunk(){
        RBloomFilterNative<String> bf = redisson.getBloomFilterNative("dump_and_chunk");
        bf.add("item1");
        BloomFilterScanDumpInfo bloomFilterScanDumpInfo = bf.scanDump(0);
        assertThat(bloomFilterScanDumpInfo.getIterator()).isGreaterThanOrEqualTo(0);
        assertThat(bloomFilterScanDumpInfo.getData()).isNotEmpty();

        BloomFilterScanDumpInfo bloomFilterScanDumpInfo2 = bf.scanDump(bloomFilterScanDumpInfo.getIterator());
        assertThat(bloomFilterScanDumpInfo2.getIterator()).isGreaterThanOrEqualTo(0);
        assertThat(bloomFilterScanDumpInfo2.getData()).isNotEmpty();

        assertThat(bf.delete()).isTrue();
        assertThat(bf.count()).isEqualTo(0);

        bf.loadChunk(bloomFilterScanDumpInfo.getIterator(), bloomFilterScanDumpInfo.getData());
        bf.loadChunk(bloomFilterScanDumpInfo2.getIterator(), bloomFilterScanDumpInfo2.getData());

        assertThat(bf.count()).isEqualTo(1);
    }
}
