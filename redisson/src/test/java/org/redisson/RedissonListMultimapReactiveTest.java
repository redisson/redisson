package org.redisson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RListMultimapCacheReactive;
import org.redisson.client.codec.StringCodec;

import java.util.List;

public class RedissonListMultimapReactiveTest extends BaseReactiveTest {

    @Test
    void testGet() {
        RListMultimapCacheReactive<String, String> multimap = redisson.getListMultimapCache("getListMultimapCache", StringCodec.INSTANCE);
        multimap.putAll("key", List.of("A", "B")).block();
        Assertions.assertThat(multimap.get("key").readAll().block()).containsExactlyInAnyOrder("A", "B");
    }


}
