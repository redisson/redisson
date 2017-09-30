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

import reactor.rx.Promise;
import reactor.rx.Streams;

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
        return Streams.create(list.iterator()).toList().poll();
    }

    public static <V> Iterable<V> sync(RCollectionReactive<V> list) {
        return Streams.create(list.iterator()).toList().poll();
    }

    public static <V> Iterator<V> toIterator(Publisher<V> pub) {
        return Streams.create(pub).toList().poll().iterator();
    }

    public static <V> Iterable<V> toIterable(Publisher<V> pub) {
        return Streams.create(pub).toList().poll();
    }

    public static <V> V sync(Publisher<V> ob) {
        Promise<V> promise;
        if (Promise.class.isAssignableFrom(ob.getClass())) {
            promise = (Promise<V>) ob;
        } else {
            promise = Streams.wrap(ob).next();
        }

        V val = promise.poll();
        if (promise.isError()) {
            throw new RuntimeException(promise.reason());
        }
        return val;
    }

    public static RedissonReactiveClient createInstance() {
        Config config = BaseTest.createConfig();
        return Redisson.createReactive(config);
    }

}
