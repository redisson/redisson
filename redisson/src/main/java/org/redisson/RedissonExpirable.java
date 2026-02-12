/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RExpirable;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class RedissonExpirable extends RedissonObject implements RExpirable {

    RedissonExpirable(CommandAsyncExecutor commandAsyncExecutor, String name) {
        super(commandAsyncExecutor, name);
    }

    RedissonExpirable(Codec codec, CommandAsyncExecutor commandAsyncExecutor, String name) {
        super(codec, commandAsyncExecutor, name);
    }

    @Override
    public boolean expire(long timeToLive, TimeUnit timeUnit) {
        return get(expireAsync(timeToLive, timeUnit));
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return expireAsync(timeToLive, timeUnit, "", getRawName());
    }

    @Override
    public boolean expireAt(long timestamp) {
        return commandExecutor.get(expireAtAsync(timestamp));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return expireAtAsync(timestamp, "", getRawName());
    }

    @Override
    public boolean expire(Instant instant) {
        return get(expireAsync(instant));
    }

    @Override
    public boolean expireIfSet(Instant time) {
        return get(expireIfSetAsync(time));
    }

    @Override
    public RFuture<Boolean> expireIfSetAsync(Instant time) {
        return expireAtAsync(time.toEpochMilli(), "XX", getRawName());
    }

    @Override
    public boolean expireIfNotSet(Instant time) {
        return get(expireIfNotSetAsync(time));
    }

    @Override
    public RFuture<Boolean> expireIfNotSetAsync(Instant time) {
        return expireAtAsync(time.toEpochMilli(), "NX", getRawName());
    }

    @Override
    public boolean expireIfGreater(Instant time) {
        return get(expireIfGreaterAsync(time));
    }

    @Override
    public RFuture<Boolean> expireIfGreaterAsync(Instant time) {
        return expireAtAsync(time.toEpochMilli(), "GT", getRawName());
    }

    @Override
    public boolean expireIfLess(Instant time) {
        return get(expireIfLessAsync(time));
    }

    @Override
    public RFuture<Boolean> expireIfLessAsync(Instant time) {
        return expireAtAsync(time.toEpochMilli(), "LT", getRawName());
    }

    @Override
    public RFuture<Boolean> expireAsync(Instant instant) {
        return expireAtAsync(instant.toEpochMilli(), "", getRawName());
    }

    @Override
    public boolean expire(Duration duration) {
        return get(expireAsync(duration));
    }

    @Override
    public RFuture<Boolean> expireAsync(Duration duration) {
        return expireAsync(duration.toMillis(), TimeUnit.MILLISECONDS, "", getRawName());
    }

    @Override
    public boolean expireAt(Date timestamp) {
        return expireAt(timestamp.getTime());
    }

    @Override
    public RFuture<Boolean> expireAtAsync(Date timestamp) {
        return expireAtAsync(timestamp.getTime());
    }

    @Override
    public boolean expireIfSet(Duration duration) {
        return get(expireIfSetAsync(duration));
    }

    @Override
    public RFuture<Boolean> expireIfSetAsync(Duration duration) {
        return expireAsync(duration.toMillis(), TimeUnit.MILLISECONDS, "XX", getRawName());
    }

    @Override
    public boolean expireIfNotSet(Duration duration) {
        return get(expireIfNotSetAsync(duration));
    }

    @Override
    public RFuture<Boolean> expireIfNotSetAsync(Duration duration) {
        return expireAsync(duration.toMillis(), TimeUnit.MILLISECONDS, "NX", getRawName());
    }

    @Override
    public boolean expireIfGreater(Duration duration) {
        return get(expireIfGreaterAsync(duration));
    }

    @Override
    public RFuture<Boolean> expireIfGreaterAsync(Duration duration) {
        return expireAsync(duration.toMillis(), TimeUnit.MILLISECONDS, "GT", getRawName());
    }

    @Override
    public boolean expireIfLess(Duration duration) {
        return get(expireIfLessAsync(duration));
    }

    @Override
    public RFuture<Boolean> expireIfLessAsync(Duration duration) {
        return expireAsync(duration.toMillis(), TimeUnit.MILLISECONDS, "LT", getRawName());
    }

    @Override
    public boolean clearExpire() {
        return commandExecutor.get(clearExpireAsync());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.PERSIST, getRawName());
    }

    @Override
    public long remainTimeToLive() {
        return commandExecutor.get(remainTimeToLiveAsync());
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.PTTL, getRawName());
    }

    @Override
    public long getExpireTime() {
        return get(getExpireTimeAsync());
    }

    @Override
    public RFuture<Long> getExpireTimeAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.PEXPIRETIME, getRawName());
    }

    protected RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local result = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local expireSet; "
                    + "if ARGV[2] ~= '' then "
                        + "expireSet = redis.call('pexpire', KEYS[j], ARGV[1], ARGV[2]); "
                    + "else "
                        + "expireSet = redis.call('pexpire', KEYS[j], ARGV[1]); "
                    + "end; "
                    + "if expireSet == 1 then "
                        + "result = expireSet;"
                    + "end; "
                + "end; "
                + "return result; ", Arrays.asList(keys), timeUnit.toMillis(timeToLive), param);
    }

    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local result = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local expireSet; "
                    + "if ARGV[2] ~= '' then "
                        + "expireSet = redis.call('pexpireat', KEYS[j], ARGV[1], ARGV[2]); "
                    + "else "
                        + "expireSet = redis.call('pexpireat', KEYS[j], ARGV[1]); "
                    + "end; "
                    + "if expireSet == 1 then "
                        + "result = expireSet;"
                    + "end; "
                + "end; "
                + "return result; ", Arrays.asList(keys), timestamp, param);
    }

    protected RFuture<Boolean> clearExpireAsync(String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local result = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local expireSet = redis.call('persist', KEYS[j]); "
                    + "if expireSet == 1 then "
                        + "result = expireSet;"
                    + "end; "
                + "end; "
                + "return result; ", Arrays.asList(keys));
    }

}
