package org.redisson;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.reactivestreams.Publisher;
import org.redisson.api.RCollectionReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RedissonReactiveClient;

import reactor.rx.Promise;
import reactor.rx.Stream;
import reactor.rx.Streams;

public abstract class BaseReactiveTest {

    protected static RedissonReactiveClient redisson;

    @BeforeClass
    public static void beforeClass() {
        redisson = createInstance();
    }

    @AfterClass
    public static void afterClass() {
        redisson.shutdown();
    }

    public <V> Iterable<V> sync(RScoredSortedSetReactive<V> list) {
        return Streams.create(list.iterator()).toList().poll();
    }

    public <V> Iterable<V> sync(RCollectionReactive<V> list) {
        return Streams.create(list.iterator()).toList().poll();
    }

    public <V> Iterator<V> toIterator(Publisher<V> pub) {
        return Streams.create(pub).toList().poll().iterator();
    }

    public <V> Iterable<V> toIterable(Publisher<V> pub) {
        return Streams.create(pub).toList().poll();
    }

    public <V> V sync(Publisher<V> ob) {
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

    public static Config createConfig() {
        String redisAddress = System.getProperty("redisAddress");
        if (redisAddress == null) {
            redisAddress = "127.0.0.1:6379";
        }
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        return config;
    }

    public static RedissonReactiveClient createInstance() {
        Config config = createConfig();
        return Redisson.createReactive(config);
    }

    @After
    public void after() {
        sync(redisson.getKeys().flushdb());
    }

}
