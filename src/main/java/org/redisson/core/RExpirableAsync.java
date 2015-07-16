package org.redisson.core;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.Future;

public interface RExpirableAsync extends RObjectAsync {

    Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit);

    Future<Boolean> expireAtAsync(Date timestamp);

    Future<Boolean> expireAtAsync(long timestamp);

    Future<Boolean> clearExpireAsync();

    Future<Long> remainTimeToLiveAsync();

}
