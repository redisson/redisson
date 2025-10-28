package org.redisson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSetMultimapCacheReactive;
import org.redisson.client.codec.StringCodec;

import java.util.Set;

public class RedissonSetMultimapReactiveTest extends BaseReactiveTest {

    @Test
    void testGet() {
        RSetMultimapCacheReactive<String, String> multimap = redisson.getSetMultimapCache("getSetMultimapCache", StringCodec.INSTANCE);
        multimap.putAll("key", Set.of("A", "B")).block();
        Assertions.assertThat(multimap.get("key").readAll().block()).containsExactlyInAnyOrder("A", "B");
    }

}
