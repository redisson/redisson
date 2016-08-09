/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.util.concurrent.Future;

public class RedissonMultimapCache<K> {

    private static final RedisCommand<Boolean> EVAL_EXPIRE_KEY = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 6, ValueType.MAP_KEY);
    
    private final CommandAsyncExecutor commandExecutor;
    private final String name;
    private final Codec codec;
    private final String timeoutSetName;
    
    public RedissonMultimapCache(CommandAsyncExecutor commandExecutor, String name, Codec codec, String timeoutSetName) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
        this.timeoutSetName = timeoutSetName;
    }

    public Future<Boolean> expireKeyAsync(K key, long timeToLive, TimeUnit timeUnit) {
        long ttlTimeout = System.currentTimeMillis() + timeUnit.toMillis(timeToLive);

        return commandExecutor.evalWriteAsync(name, codec, EVAL_EXPIRE_KEY,
                "if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then "
                    + "if tonumber(ARGV[1]) > 0 then "
                        + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[2]); " +
                      "else " +
                          "redis.call('zrem', KEYS[2], ARGV[2]); "
                    + "end; "
                    + "return 1; "
              + "else "
                + "return 0; "
              + "end",
            Arrays.<Object>asList(name, timeoutSetName), ttlTimeout, key);
    }
    
    public Future<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {KEYS[1], KEYS[2]}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "table.insert(keys, name); " +
                    "end;" +
                "end; " +
                
                "local n = 0 "
                + "for i=1, #keys,5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end; "
                + "return n;",
                Arrays.<Object>asList(name, timeoutSetName));
    }

    public Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag'); " +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('pexpire', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(name, timeoutSetName), timeUnit.toMillis(timeToLive));
    }

    public Future<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('pexpireat', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "redis.call('pexpireat', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(name, timeoutSetName), timestamp);
    }

    public Future<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zrem', KEYS[2], 'redisson__expiretag'); " +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('persist', name); " +
                    "end;" +
                "end; " +
                "redis.call('persist', KEYS[2]); " +
                "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(name, timeoutSetName));
    }

    
}
