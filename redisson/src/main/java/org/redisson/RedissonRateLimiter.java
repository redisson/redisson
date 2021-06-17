/**
 * Copyright (c) 2013-2021 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapEntriesDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRateLimiter extends RedissonExpirable implements RRateLimiter {

    public RedissonRateLimiter(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    String getPermitsName() {
        return suffixName(getRawName(), "permits");
    }

    String getClientPermitsName() {
        return suffixName(getPermitsName(), commandExecutor.getConnectionManager().getId());
    }

    String getValueName() {
        return suffixName(getRawName(), "value");
    }
    
    String getClientValueName() {
        return suffixName(getValueName(), commandExecutor.getConnectionManager().getId());
    }
    
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync() {
        return tryAcquireAsync(1L);
    }
    
    @Override
    public boolean tryAcquire(long permits) {
        return get(tryAcquireAsync(RedisCommands.EVAL_NULL_BOOLEAN, permits));
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync(long permits) {
        return tryAcquireAsync(RedisCommands.EVAL_NULL_BOOLEAN, permits);
    }

    @Override
    public void acquire() {
        get(acquireAsync());
    }
    
    @Override
    public RFuture<Void> acquireAsync() {
        return acquireAsync(1);
    }

    @Override
    public void acquire(long permits) {
        get(acquireAsync(permits));
    }

    @Override
    public RFuture<Void> acquireAsync(long permits) {
        RPromise<Void> promise = new RedissonPromise<Void>();
        tryAcquireAsync(permits, -1, null).onComplete((res, e) -> {
            if (e != null) {
                promise.tryFailure(e);
                return;
            }
            
            promise.trySuccess(null);
        });
        return promise;
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return get(tryAcquireAsync(timeout, unit));
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(long timeout, TimeUnit unit) {
        return tryAcquireAsync(1, timeout, unit);
    }
    
    @Override
    public boolean tryAcquire(long permits, long timeout, TimeUnit unit) {
        return get(tryAcquireAsync(permits, timeout, unit));
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync(long permits, long timeout, TimeUnit unit) {
        RPromise<Boolean> promise = new RedissonPromise<Boolean>();
        long timeoutInMillis = -1;
        if (timeout >= 0) {
            timeoutInMillis = unit.toMillis(timeout);
        }
        tryAcquireAsync(permits, promise, timeoutInMillis);
        return promise;
    }
    
    private void tryAcquireAsync(long permits, RPromise<Boolean> promise, long timeoutInMillis) {
        long s = System.currentTimeMillis();
        RFuture<Long> future = tryAcquireAsync(RedisCommands.EVAL_LONG, permits);
        future.onComplete((delay, e) -> {
            if (e != null) {
                promise.tryFailure(e);
                return;
            }
            
            if (delay == null) {
                promise.trySuccess(true);
                return;
            }
            
            if (timeoutInMillis == -1) {
                commandExecutor.getConnectionManager().getGroup().schedule(() -> {
                    tryAcquireAsync(permits, promise, timeoutInMillis);
                }, delay, TimeUnit.MILLISECONDS);
                return;
            }
            
            long el = System.currentTimeMillis() - s;
            long remains = timeoutInMillis - el;
            if (remains <= 0) {
                promise.trySuccess(false);
                return;
            }
            if (remains < delay) {
                commandExecutor.getConnectionManager().getGroup().schedule(() -> {
                    promise.trySuccess(false);
                }, remains, TimeUnit.MILLISECONDS);
            } else {
                long start = System.currentTimeMillis();
                commandExecutor.getConnectionManager().getGroup().schedule(() -> {
                    long elapsed = System.currentTimeMillis() - start;
                    if (remains <= elapsed) {
                        promise.trySuccess(false);
                        return;
                    }
                    
                    tryAcquireAsync(permits, promise, remains - elapsed);
                }, delay, TimeUnit.MILLISECONDS);
            }
        });
    }
    
    private <T> RFuture<T> tryAcquireAsync(RedisCommand<T> command, Long value) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, command,
                "local rate = redis.call('hget', KEYS[1], 'rate');"
              + "local interval = redis.call('hget', KEYS[1], 'interval');"
              + "local type = redis.call('hget', KEYS[1], 'type');"
              + "assert(rate ~= false and interval ~= false and type ~= false, 'RateLimiter is not initialized')"
              
              + "local valueName = KEYS[2];"
              + "local permitsName = KEYS[4];"
              + "if type == '1' then "
                  + "valueName = KEYS[3];"
                  + "permitsName = KEYS[5];"
              + "end;"

              + "assert(tonumber(rate) >= tonumber(ARGV[1]), 'Requested permits amount could not exceed defined rate'); "

              + "local currentValue = redis.call('get', valueName); "
              + "if currentValue ~= false then "
                     + "local expiredValues = redis.call('zrangebyscore', permitsName, 0, tonumber(ARGV[2]) - interval); "
                     + "local released = 0; "
                     + "for i, v in ipairs(expiredValues) do "
                          + "local random, permits = struct.unpack('fI', v);"
                          + "released = released + permits;"
                     + "end; "

                     + "if released > 0 then "
                          + "redis.call('zremrangebyscore', permitsName, 0, tonumber(ARGV[2]) - interval); "
                          + "currentValue = tonumber(currentValue) + released; "
                          + "redis.call('set', valueName, currentValue);"
                     + "end;"

                     + "if tonumber(currentValue) < tonumber(ARGV[1]) then "
                         + "local nearest = redis.call('zrangebyscore', permitsName, '(' .. (tonumber(ARGV[2]) - interval), '+inf', 'withscores', 'limit', 0, 1); "
                         + "return tonumber(nearest[2]) - (tonumber(ARGV[2]) - interval);"
                     + "else "
                         + "redis.call('zadd', permitsName, ARGV[2], struct.pack('fI', ARGV[3], ARGV[1])); "
                         + "redis.call('decrby', valueName, ARGV[1]); "
                         + "return nil; "
                     + "end; "
              + "else "
                     + "redis.call('set', valueName, rate); "
                     + "redis.call('zadd', permitsName, ARGV[2], struct.pack('fI', ARGV[3], ARGV[1])); "
                     + "redis.call('decrby', valueName, ARGV[1]); "
                     + "return nil; "
              + "end;",
                Arrays.asList(getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName()),
                value, System.currentTimeMillis(), ThreadLocalRandom.current().nextLong());
    }

    @Override
    public boolean trySetRate(RateType type, long rate, long rateInterval, RateIntervalUnit unit) {
        return get(trySetRateAsync(type, rate, rateInterval, unit));
    }

    @Override
    public RFuture<Boolean> trySetRateAsync(RateType type, long rate, long rateInterval, RateIntervalUnit unit) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('hsetnx', KEYS[1], 'rate', ARGV[1]);"
              + "redis.call('hsetnx', KEYS[1], 'interval', ARGV[2]);"
              + "return redis.call('hsetnx', KEYS[1], 'type', ARGV[3]);",
                Collections.singletonList(getRawName()), rate, unit.toMillis(rateInterval), type.ordinal());
    }

    @Override
    public void setRate(RateType type, long rate, long rateInterval, RateIntervalUnit unit) {
        get(setRateAsync(type, rate, rateInterval, unit));
    }

    @Override
    public RFuture<Void> setRateAsync(RateType type, long rate, long rateInterval, RateIntervalUnit unit) {
         return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('hset', KEYS[1], 'rate', ARGV[1]);"
                        + "redis.call('hset', KEYS[1], 'interval', ARGV[2]);"
                        + "redis.call('hset', KEYS[1], 'type', ARGV[3]);"
                        + "redis.call('del', KEYS[2], KEYS[3]);",
                Arrays.asList(getRawName(), getValueName(), getPermitsName()), rate, unit.toMillis(rateInterval), type.ordinal());
    }
    
    private static final RedisCommand HGETALL = new RedisCommand("HGETALL", new MapEntriesDecoder(new MultiDecoder<RateLimiterConfig>() {

        @Override
        public RateLimiterConfig decode(List<Object> parts, State state) {
            Map<String, String> map = new HashMap<>(parts.size()/2);
            for (int i = 0; i < parts.size(); i++) {
                if (i % 2 != 0) {
                    map.put(parts.get(i-1).toString(), parts.get(i).toString());
                }
            }

            RateType type = RateType.values()[Integer.valueOf(map.get("type"))];
            Long rateInterval = Long.valueOf(map.get("interval"));
            Long rate = Long.valueOf(map.get("rate"));
            return new RateLimiterConfig(type, rateInterval, rate);
        }
        
    }));
    
    @Override
    public RateLimiterConfig getConfig() {
        return get(getConfigAsync());
    }
    
    @Override
    public RFuture<RateLimiterConfig> getConfigAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, HGETALL, getRawName());
    }

    @Override
    public long availablePermits() {
        return get(availablePermitsAsync());
    }

    @Override
    public RFuture<Long> availablePermitsAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local rate = redis.call('hget', KEYS[1], 'rate');"
              + "local interval = redis.call('hget', KEYS[1], 'interval');"
              + "local type = redis.call('hget', KEYS[1], 'type');"
              + "assert(rate ~= false and interval ~= false and type ~= false, 'RateLimiter is not initialized')"

              + "local valueName = KEYS[2];"
              + "local permitsName = KEYS[4];"
              + "if type == '1' then "
                  + "valueName = KEYS[3];"
                  + "permitsName = KEYS[5];"
              + "end;"

              + "local currentValue = redis.call('get', valueName); "
              + "if currentValue == false then "
                     + "redis.call('set', valueName, rate); "
                     + "return rate; "
              + "else "
                     + "local expiredValues = redis.call('zrangebyscore', permitsName, 0, tonumber(ARGV[1]) - interval); "
                     + "local released = 0; "
                     + "for i, v in ipairs(expiredValues) do "
                          + "local random, permits = struct.unpack('fI', v);"
                          + "released = released + permits;"
                     + "end; "

                     + "if released > 0 then "
                          + "redis.call('zremrangebyscore', permitsName, 0, tonumber(ARGV[1]) - interval); "
                          + "currentValue = tonumber(currentValue) + released; "
                          + "redis.call('set', valueName, currentValue);"
                     + "end;"

                     + "return currentValue; "
              + "end;",
                Arrays.asList(getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName()),
                System.currentTimeMillis());
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return expireAsync(timeToLive, timeUnit, getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getValueName(), getClientValueName(), getPermitsName(), getClientPermitsName());
    }

}
