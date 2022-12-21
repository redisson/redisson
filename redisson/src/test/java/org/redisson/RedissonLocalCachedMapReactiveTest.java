package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMapReactive;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLocalCachedMapReactiveTest extends BaseReactiveTest {

    @Test
    public void test1() {
        RLocalCachedMapReactive<String, String> m1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        RLocalCachedMapReactive<String, String> m2 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());

        m1.put("1", "4").block();
        m1.put("2", "5").block();
        m1.put("3", "6").block();

        assertThat(m1.getCachedMap()).containsKeys("1", "2", "3");

        assertThat(m1.get("1").block()).isEqualTo(m2.get("1").block()).isEqualTo("4");
    }

}
