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
import java.util.concurrent.locks.Condition;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.LockPubSub;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 * <p>
 * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonFairLock extends RedissonLock implements RLock {

    private final long threadWaitTime = 5000;
    private final CommandAsyncExecutor commandExecutor;
    private final String threadsQueueName;
    private final String timeoutSetName;

    public RedissonFairLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        threadsQueueName = prefixName("redisson_lock_queue", name);
        timeoutSetName = prefixName("redisson_lock_timeout", name);
    }
    
    @Override
    protected RedissonLockEntry getEntry(long threadId) {
        return PUBSUB.getEntry(getEntryName() + ":" + threadId);
    }

    @Override
    protected RFuture<RedissonLockEntry> subscribe(long threadId) {
        return PUBSUB.subscribe(getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager().getSubscribeService());
    }

    @Override
    protected void unsubscribe(RFuture<RedissonLockEntry> future, long threadId) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager().getSubscribeService());
    }

    @Override
    protected RFuture<Void> acquireFailedAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                    "local firstThreadId = redis.call('lindex', KEYS[1], 0); " + 
                    "if firstThreadId == ARGV[1] then " + 
                        "local keys = redis.call('zrange', KEYS[2], 0, -1); " + 
                        "for i = 1, #keys, 1 do " + 
                            "redis.call('zincrby', KEYS[2], -tonumber(ARGV[2]), keys[i]);" + 
                        "end;" + 
                    "end;" +
                    "redis.call('zrem', KEYS[2], ARGV[1]); " +
                    "redis.call('lrem', KEYS[1], 0, ARGV[1]); ",
                    Arrays.<Object>asList(threadsQueueName, timeoutSetName), 
                    getLockName(threadId), threadWaitTime);
    }
    
    @Override
    <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        long currentTime = System.currentTimeMillis();
        if (command == RedisCommands.EVAL_NULL_BOOLEAN) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                    + "if timeout <= tonumber(ARGV[3]) then "
                        + "redis.call('zrem', KEYS[3], firstThreadId2); "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"
                    + 
                    
                    "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('zrem', KEYS[3], ARGV[2]); " +
                            "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "return 1;", 
                    Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName), 
                    internalLockLeaseTime, getLockName(threadId), currentTime);
        }
        
        if (command == RedisCommands.EVAL_LONG) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                    + "if timeout <= tonumber(ARGV[4]) then "
                        + "redis.call('zrem', KEYS[3], firstThreadId2); "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"
                    
                      + "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('zrem', KEYS[3], ARGV[2]); " +
                            "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                            
                        "local firstThreadId = redis.call('lindex', KEYS[2], 0); " +
                        "local ttl; " + 
                        "if firstThreadId ~= false and firstThreadId ~= ARGV[2] then " + 
                            "ttl = tonumber(redis.call('zscore', KEYS[3], firstThreadId)) - tonumber(ARGV[4]);" + 
                        "else "
                          + "ttl = redis.call('pttl', KEYS[1]);" + 
                        "end; " + 
                            
                        "local timeout = ttl + tonumber(ARGV[3]);" + 
                        "if redis.call('zadd', KEYS[3], timeout, ARGV[2]) == 1 then " +
                            "redis.call('rpush', KEYS[2], ARGV[2]);" +
                        "end; " +
                        "return ttl;", 
                        Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName), 
                                    internalLockLeaseTime, getLockName(threadId), currentTime + threadWaitTime, currentTime);
        }
        
        throw new IllegalArgumentException();
    }
    
    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                + "if timeout <= tonumber(ARGV[4]) then "
                    + "redis.call('zrem', KEYS[3], firstThreadId2); "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"
                
              + "if (redis.call('exists', KEYS[1]) == 0) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " +
                    "return 1; " +
                "end;" +
                "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                    "return nil;" +
                "end; " +
                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                "if (counter > 0) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                    "return 0; " +
                "end; " +
                    
                "redis.call('del', KEYS[1]); " +
                "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                "if nextThreadId ~= false then " +
                    "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                "end; " +
                "return 1; ",
                Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName, getChannelName()), 
                LockPubSub.unlockMessage, internalLockLeaseTime, getLockName(threadId), System.currentTimeMillis());
    }
    
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), threadsQueueName, timeoutSetName);
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName);
        return super.sizeInMemoryAsync(keys);
    }
    
    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                        "return redis.call('pexpire', KEYS[3], ARGV[1]); ",
                Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName),
                timeUnit.toMillis(timeToLive));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpireat', KEYS[1], ARGV[1]); " +
                        "redis.call('pexpireat', KEYS[2], ARGV[1]); " +
                        "return redis.call('pexpireat', KEYS[3], ARGV[1]); ",
                Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName),
                timestamp);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('persist', KEYS[1]); " +
                        "redis.call('persist', KEYS[2]); " +
                        "return redis.call('persist', KEYS[3]); ",
                Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName));
    }

    
    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal(null);
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "local timeout = tonumber(redis.call('zscore', KEYS[3], firstThreadId2));"
                + "if timeout <= tonumber(ARGV[2]) then "
                    + "redis.call('zrem', KEYS[3], firstThreadId2); "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"
                + 
                
                "if (redis.call('del', KEYS[1]) == 1) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[2], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " + 
                    "return 1; " + 
                "end; " + 
                "return 0;",
                Arrays.<Object>asList(getName(), threadsQueueName, timeoutSetName, getChannelName()), 
                LockPubSub.unlockMessage, System.currentTimeMillis());
    }

}
