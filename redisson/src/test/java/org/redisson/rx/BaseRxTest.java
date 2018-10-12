package org.redisson.rx;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.reactivestreams.Publisher;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RCollectionRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RedissonRxClient;
import org.redisson.config.Config;

import io.reactivex.Flowable;

public abstract class BaseRxTest {

    protected RedissonRxClient redisson;
    protected static RedissonRxClient defaultRedisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        defaultRedisson = createInstance();
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        defaultRedisson.shutdown();
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (redisson == null) {
            redisson = defaultRedisson;
        }
        sync(redisson.getKeys().flushall());
    }

    public static <V> Iterable<V> sync(RScoredSortedSetRx<V> list) {
        return list.iterator().toList().blockingGet();
    }

    public static <V> Iterable<V> sync(RCollectionRx<V> list) {
        return list.iterator().toList().blockingGet();
    }

    public static <V> Iterator<V> toIterator(Publisher<V> pub) {
        return Flowable.fromPublisher(pub).toList().blockingGet().iterator();
    }

    public static <V> Iterable<V> toIterable(Publisher<V> pub) {
        return Flowable.fromPublisher(pub).toList().blockingGet();
    }

    public static <V> V sync(Flowable<V> ob) {
        try {
            return ob.blockingSingle();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public static RedissonRxClient createInstance() {
        Config config = BaseTest.createConfig();
        return Redisson.createRx(config);
    }

}
