/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RObject;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 */
public class RedissonMultimapCache<K> {

    private final CommandAsyncExecutor commandExecutor;
    private final RObject object;
    private final String timeoutSetName;
    private final String prefix;
    
    public RedissonMultimapCache(CommandAsyncExecutor commandExecutor, RObject object, String timeoutSetName, String prefix) {
        this.commandExecutor = commandExecutor;
        this.object = object;
        this.timeoutSetName = timeoutSetName;
        this.prefix = prefix;
    }

    public RFuture<Boolean> expireKeyAsync(K key, long timeToLive, TimeUnit timeUnit) {
        long ttlTimeout = System.currentTimeMillis() + timeUnit.toMillis(timeToLive);

        return commandExecutor.evalWriteAsync(object.getName(), object.getCodec(), RedisCommands.EVAL_BOOLEAN,
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
            Arrays.<Object>asList(object.getName(), timeoutSetName), 
            ttlTimeout, ((RedissonObject) object).encodeMapKey(key));
    }
    
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(object.getName(), timeoutSetName);
        return ((RedissonObject) object).sizeInMemoryAsync(keys);
    }
    
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(object.getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {KEYS[1], KEYS[2]}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " + 
                        "table.insert(keys, name); " +
                    "end;" +
                "end; " +
                
                "local n = 0 "
                + "for i=1, #keys,5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end; "
                + "return n;",
                Arrays.<Object>asList(object.getName(), timeoutSetName), 
                prefix);
    }

    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(object.getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag'); " +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; " + 
                        "redis.call('pexpire', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(object.getName(), timeoutSetName), 
                timeUnit.toMillis(timeToLive), prefix);
    }

    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(object.getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; " + 
                        "redis.call('pexpireat', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "redis.call('pexpireat', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(object.getName(), timeoutSetName),
                timestamp, prefix);
    }

    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(object.getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zrem', KEYS[2], 'redisson__expiretag'); " +
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " + 
                        "redis.call('persist', name); " +
                    "end;" +
                "end; " +
                "redis.call('persist', KEYS[2]); " +
                "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(object.getName(), timeoutSetName),
                prefix);
    }

    
}
