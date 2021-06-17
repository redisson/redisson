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

    RedissonExpirable(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonExpirable(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public boolean expire(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.get(expireAsync(timeToLive, timeUnit));
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.PEXPIRE, getRawName(), timeUnit.toMillis(timeToLive));
    }

    @Override
    public boolean expireAt(long timestamp) {
        return commandExecutor.get(expireAtAsync(timestamp));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return expireAsync(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public boolean expire(Instant instant) {
        return get(expireAsync(instant));
    }

    @Override
    public RFuture<Boolean> expireAsync(Instant instant) {
        return expireAtAsync(instant.toEpochMilli(), getRawName());
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

    protected RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local result = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local expireSet = redis.call('pexpire', KEYS[j], ARGV[1]); "
                    + "if expireSet == 1 then "
                        + "result = expireSet;"
                    + "end; "
                + "end; "
                + "return result; ", Arrays.asList(keys), timeUnit.toMillis(timeToLive));
    }

    protected RFuture<Boolean> expireAtAsync(long timestamp, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local result = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local expireSet = redis.call('pexpireat', KEYS[j], ARGV[1]); "
                    + "if expireSet == 1 then "
                        + "result = expireSet;"
                    + "end; "
                + "end; "
                + "return result; ", Arrays.asList(keys), timestamp);
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
