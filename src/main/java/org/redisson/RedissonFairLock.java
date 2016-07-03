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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandExecutor;
import org.redisson.core.RLock;
import org.redisson.pubsub.LockPubSub;

import io.netty.util.concurrent.Future;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 * <p/>
 * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonFairLock extends RedissonLock implements RLock {

    private final CommandExecutor commandExecutor;

    protected RedissonFairLock(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name, id);
        this.commandExecutor = commandExecutor;
    }
    
    String getThreadsQueueName() {
        return "redisson_lock_queue:{" + getName() + "}";
    }
    
    String getThreadElementName(long threadId) {
        return "redisson_lock_thread:{" + getName() + "}:" + getLockName(threadId);
    }
    
    @Override
    protected RedissonLockEntry getEntry(long threadId) {
        return PUBSUB.getEntry(getEntryName() + ":" + threadId);
    }

    @Override
    protected Future<RedissonLockEntry> subscribe(long threadId) {
        return PUBSUB.subscribe(getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager());
    }

    @Override
    protected void unsubscribe(Future<RedissonLockEntry> future, long threadId) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName() + ":" + threadId, 
                getChannelName() + ":" + getLockName(threadId), commandExecutor.getConnectionManager());
    }

    @Override
    <T> Future<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);
        long threadWaitTime = 5000;

        if (command == RedisCommands.EVAL_NULL_BOOLEAN) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "if redis.call('exists', 'redisson_lock_thread:{' .. KEYS[1] .. '}:' .. firstThreadId2) == 0 then "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"
                    + 
                    
                    "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('del', KEYS[3]); " +
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
                    Arrays.<Object>asList(getName(), getThreadsQueueName(), getThreadElementName(threadId)), internalLockLeaseTime, getLockName(threadId));
        }
        
        if (command == RedisCommands.EVAL_LONG) {
            return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                    // remove stale threads
                    "while true do "
                    + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                    + "if firstThreadId2 == false then "
                        + "break;"
                    + "end; "
                    + "if redis.call('exists', 'redisson_lock_thread:{' .. KEYS[1] .. '}:' .. firstThreadId2) == 0 then "
                        + "redis.call('lpop', KEYS[2]); "
                    + "else "
                        + "break;"
                    + "end; "
                  + "end;"
                    + 
                        "if (redis.call('exists', KEYS[1]) == 0) and ((redis.call('exists', KEYS[2]) == 0) "
                            + "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                            "redis.call('lpop', KEYS[2]); " +
                            "redis.call('del', KEYS[3]); " +
                            "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "local firstThreadId = redis.call('lindex', KEYS[2], 0)" +
                        "local ttl = redis.call('pttl', KEYS[1]); " + 
                        "if firstThreadId ~= false and firstThreadId ~= ARGV[2] then " + 
                            "ttl = redis.call('pttl', 'redisson_lock_thread:{' .. KEYS[1] .. '}:' .. firstThreadId);" + 
                        "end; " + 
                        "if redis.call('exists', KEYS[3]) == 0 then " +
                            "redis.call('rpush', KEYS[2], ARGV[2]);" +
                            "redis.call('set', KEYS[3], 1);" +
                        "end; " +
                        "redis.call('pexpire', KEYS[3], ttl + tonumber(ARGV[3]));" +
                        "return ttl;", 
                        Arrays.<Object>asList(getName(), getThreadsQueueName(), getThreadElementName(threadId)), 
                                    internalLockLeaseTime, getLockName(threadId), threadWaitTime);
        }
        
        throw new IllegalArgumentException();
    }
    
    @Override
    public void unlock() {
        Boolean opStatus = commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "if redis.call('exists', 'redisson_lock_thread:{' .. KEYS[1] .. '}:' .. firstThreadId2) == 0 then "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"
                + 
                
                "if (redis.call('exists', KEYS[1]) == 0) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[3], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[2] .. ':' .. nextThreadId, ARGV[1]); " +
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
                "else " +
                    "redis.call('del', KEYS[1]); " +
                    "local nextThreadId = redis.call('lindex', KEYS[3], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[2] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " +
                    "return 1; "+
                "end; " +
                "return nil;",
                Arrays.<Object>asList(getName(), getChannelName(), getThreadsQueueName()), LockPubSub.unlockMessage, internalLockLeaseTime, getLockName(Thread.currentThread().getId()));
        
        if (opStatus == null) {
            throw new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread by node id: "
                    + id + " thread-id: " + Thread.currentThread().getId());
        }
        if (opStatus) {
            cancelExpirationRenewal();
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // remove stale threads
                "while true do "
                + "local firstThreadId2 = redis.call('lindex', KEYS[2], 0);"
                + "if firstThreadId2 == false then "
                    + "break;"
                + "end; "
                + "if redis.call('exists', 'redisson_lock_thread:{' .. KEYS[1] .. '}:' .. firstThreadId2) == 0 then "
                    + "redis.call('lpop', KEYS[2]); "
                + "else "
                    + "break;"
                + "end; "
              + "end;"
                + 
                
                "if (redis.call('del', KEYS[1]) == 1) then " + 
                    "local nextThreadId = redis.call('lindex', KEYS[3], 0); " + 
                    "if nextThreadId ~= false then " +
                        "redis.call('publish', KEYS[2] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " + 
                    "return 1 " + 
                "end " + 
                "return 0;",
                Arrays.<Object>asList(getName(), getChannelName(), getThreadsQueueName()), LockPubSub.unlockMessage);
    }

}
