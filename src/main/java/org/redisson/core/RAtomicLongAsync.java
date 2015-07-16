package org.redisson.core;

import io.netty.util.concurrent.Future;

public interface RAtomicLongAsync extends RExpirableAsync {

    Future<Boolean> compareAndSetAsync(long expect, long update);

    Future<Long> addAndGetAsync(long delta);

    Future<Long> decrementAndGetAsync();

    Future<Long> getAsync();

    Future<Long> getAndAddAsync(long delta);

    Future<Long> getAndSetAsync(long newValue);

    Future<Long> incrementAndGetAsync();

    Future<Long> getAndIncrementAsync();

    Future<Long> getAndDecrementAsync();

    Future<Void> setAsync(long newValue);

}
