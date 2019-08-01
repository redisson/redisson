package org.redisson;

import java.io.IOException;
import java.util.Iterator;

import org.junit.After;
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

public abstract class BaseReactiveTest {

    protected RedissonReactiveClient redisson;
    protected static RedissonReactiveClient defaultRedisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            defaultRedisson = createInstance();
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
            defaultRedisson.shutdown();
        }
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            redisson = createInstance();
        } else {
            if (redisson == null) {
                redisson = defaultRedisson;
            }
            sync(redisson.getKeys().flushall());
        }
    }

    @After
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            redisson.shutdown();
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
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
