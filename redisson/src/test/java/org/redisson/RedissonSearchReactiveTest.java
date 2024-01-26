package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RSearchReactive;
import org.redisson.api.search.index.FieldIndex;
import org.redisson.api.search.index.IndexOptions;
import org.redisson.api.search.index.IndexType;
import reactor.test.StepVerifier;

import java.util.Arrays;

public class RedissonSearchReactiveTest extends DockerRedisStackTest {

    @Test
    public void testDropIndex() {
        RSearchReactive s = redisson.reactive().getSearch();

        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"))
                .as(StepVerifier::create)
                .verifyComplete();

        s.dropIndex("idx")
                .as(StepVerifier::create)
                .verifyComplete();
    }

}
