package org.redisson.rx;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeAll;
import org.redisson.RedisDockerTest;
import org.redisson.api.RCollectionRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RedissonRxClient;

import java.io.IOException;
import java.util.Iterator;

public abstract class BaseRxTest extends RedisDockerTest {

    protected static RedissonRxClient redisson;

    @BeforeAll
    public static void beforeClass() throws IOException, InterruptedException {
        redisson = RedisDockerTest.redisson.rxJava();
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

}
