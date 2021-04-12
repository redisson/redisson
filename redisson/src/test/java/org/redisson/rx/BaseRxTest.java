package org.redisson.rx;

import java.io.IOException;
import java.util.Iterator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RCollectionRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RedissonRxClient;
import org.redisson.config.Config;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public abstract class BaseRxTest {

    protected RedissonRxClient redisson;
    protected static RedissonRxClient defaultRedisson;

    @BeforeAll
    public static void beforeClass() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        defaultRedisson = createInstance();
    }

    @AfterAll
    public static void afterClass() throws IOException, InterruptedException {
        defaultRedisson.shutdown();
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @BeforeEach
    public void before() throws IOException, InterruptedException {
        if (redisson == null) {
            redisson = defaultRedisson;
        }
        sync(redisson.getKeys().flushall());
    }

    public static <V> V sync(Maybe<V> maybe) {
        return maybe.blockingGet();
    }
    
    public static void sync(Completable completable) {
        completable.blockingAwait();
    }

    public static <V> V sync(Single<V> single) {
        return single.blockingGet();
    }
    
    public static <V> Iterable<V> sync(RScoredSortedSetRx<V> list) {
        return list.iterator().toList().blockingGet();
    }

    public static <V> Iterable<V> sync(RCollectionRx<V> list) {
        return list.iterator().toList().blockingGet();
    }

    public static <V> Iterator<V> toIterator(Flowable<V> flowable) {
        return flowable.toList().blockingGet().iterator();
    }

    public static RedissonRxClient createInstance() {
        Config config = BaseTest.createConfig();
        return Redisson.create(config).rxJava();
    }

}
