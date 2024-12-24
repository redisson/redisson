package org.redisson;

import org.junit.jupiter.api.BeforeAll;
import org.reactivestreams.Publisher;
import org.redisson.api.RCollectionReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.function.Consumer;

public abstract class BaseReactiveTest extends RedisDockerTest {

    protected static RedissonReactiveClient redisson;

    @BeforeAll
    public static void beforeClass() {
        redisson = RedisDockerTest.redisson.reactive();
    }

    public static <V> Iterable<V> sync(RScoredSortedSetReactive<V> list) {
        return toIterable(list.iterator());
    }

    public static <V> Iterable<V> sync(RCollectionReactive<V> list) {
        return toIterable(list.iterator());
    }

    public static <V> Iterator<V> toIterator(Publisher<V> pub) {
        return Flux.from(pub).toIterable().iterator();
    }

    public static <V> Iterable<V> toIterable(Publisher<V> pub) {
        return Flux.from(pub).toIterable();
    }

    public static <V> V sync(Mono<V> mono) {
        return mono.block();
    }

    public static <V> V sync(Flux<V> flux) {
        return flux.single().block();
    }

    protected void testWithParamsReactive(Consumer<RedissonReactiveClient> redissonCallback, String... params) {
        GenericContainer<?> redis = createRedis(params);
        redis.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer().setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);
        RedissonReactiveClient redissonReactiveClient = redisson.reactive();

        try {
            redissonCallback.accept(redissonReactiveClient);
        } finally {
            redisson.shutdown();
            redis.stop();
        }
    }

}
