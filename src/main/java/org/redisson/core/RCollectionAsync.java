package org.redisson.core;

import java.util.Collection;

import io.netty.util.concurrent.Future;

public interface RCollectionAsync<V> extends RExpirableAsync {

    Future<Boolean> retainAllAsync(Collection<?> c);

    Future<Boolean> removeAllAsync(Collection<?> c);

    Future<Boolean> containsAsync(Object o);

    Future<Boolean> containsAllAsync(Collection<?> c);

    Future<Boolean> removeAsync(Object o);

    Future<Collection<V>> readAllAsync();

    Future<Integer> sizeAsync();

    Future<Boolean> addAsync(V e);

    Future<Boolean> addAllAsync(Collection<? extends V> c);

}
