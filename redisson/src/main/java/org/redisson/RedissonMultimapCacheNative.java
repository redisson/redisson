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

import io.netty.buffer.ByteBuf;
import org.redisson.api.RFuture;
import org.redisson.api.RObject;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 */
class RedissonMultimapCacheNative<K> {

    private final CommandAsyncExecutor commandExecutor;
    private final RedissonMultimap<K, ?> object;
    private final String prefix;

    RedissonMultimapCacheNative(CommandAsyncExecutor commandExecutor, RObject object, String prefix) {
        this.commandExecutor = commandExecutor;
        this.object = (RedissonMultimap<K, ?>) object;
        this.prefix = prefix;
    }

    public RFuture<Boolean> expireKeyAsync(K key, long timeToLive, TimeUnit timeUnit) {
        ByteBuf keyState = object.encodeMapKey(key);
        String keyHash = object.hash(keyState);
        String setName = object.getValuesName(keyHash);

        return commandExecutor.evalWriteAsync(object.getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local res = redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2])" +
                      "if res[1] == 1 then " +
                         "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                         "return 1;" +
                      "end; "
                    + "return 0; ",
            Arrays.asList(object.getRawName(), setName),
            timeUnit.toMillis(timeToLive), object.encodeMapKey(key));
    }
    
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param) {
        return commandExecutor.evalWriteAsync(object.getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; "
                      + "if ARGV[3] ~= '' then "
                          + "redis.call('pexpire', name, ARGV[1], ARGV[3]); "
                      + "else "
                          + "redis.call('pexpire', name, ARGV[1]); "
                      + "end; " +
                    "end;" +
                "end; " +
                "if ARGV[3] ~= '' then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[1], ARGV[3]); "
              + "end; " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.asList(object.getRawName()),
                timeUnit.toMillis(timeToLive), prefix, param);
    }

    public RFuture<Boolean> expireAtAsync(long timestamp, String param) {
        return commandExecutor.evalWriteAsync(object.getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; "
                      + "if ARGV[3] ~= '' then "
                          + "redis.call('pexpireat', name, ARGV[1], ARGV[3]); "
                      + "else "
                          + "redis.call('pexpireat', name, ARGV[1]); "
                      + "end; " +
                    "end;" +
                "end; " +
                "if ARGV[3] ~= '' then "
                    + "return redis.call('pexpireat', KEYS[1], ARGV[1], ARGV[3]); "
              + "end; " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.asList(object.getRawName()),
                timestamp, prefix, param);
    }

    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(object.getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " + 
                        "redis.call('persist', name); " +
                    "end;" +
                "end; " +
                "return redis.call('persist', KEYS[1]); ",
                Arrays.asList(object.getRawName()),
                prefix);
    }

}
