package org.redisson;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.reactivestreams.Publisher;
import org.redisson.api.RCollectionReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Iterator;

public abstract class BaseReactiveTest {

    protected static RedissonReactiveClient redisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        redisson = createInstance();
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        redisson.shutdown();
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @Before
    public void before() throws IOException, InterruptedException {
        redisson.getKeys().flushall();
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

    public static RedissonReactiveClient createInstance() {
        Config config = BaseTest.createConfig();
        return Redisson.createReactive(config);
    }

}
