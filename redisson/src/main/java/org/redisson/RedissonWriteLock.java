/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.LockPubSub;

/**
 * Lock will be removed automatically if client disconnects.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonWriteLock extends RedissonLock implements RLock {

    protected RedissonWriteLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    String getChannelName() {
        return prefixName("redisson_rwlock", getRawName());
    }

    @Override
    protected String getLockName(long threadId) {
        return super.getLockName(threadId) + ":write";
    }
    
    @Override
    <T> RFuture<T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        return commandExecutor.syncedEval(getRawName(), LongCodec.INSTANCE, command,
                            "local mode = redis.call('hget', KEYS[1], 'mode'); " +
                            "if (mode == false) then " +
                                  "redis.call('hset', KEYS[1], 'mode', 'write'); " +
                                  "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                                  "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                                  "return nil; " +
                              "end; " +
                              "if (mode == 'write') then " +
                                  "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                                      "redis.call('hincrby', KEYS[1], ARGV[2], 1); " + 
                                      "local currentExpire = redis.call('pttl', KEYS[1]); " +
                                      "redis.call('pexpire', KEYS[1], currentExpire + ARGV[1]); " +
                                      "return nil; " +
                                  "end; " +
                                "end;" +
                                "return redis.call('pttl', KEYS[1]);",
                        Arrays.<Object>asList(getRawName()),
                        unit.toMillis(leaseTime), getLockName(threadId));
    }

    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId, String requestId, int timeout) {
        return evalWriteSyncedAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local val = redis.call('get', KEYS[3]); " +
                "if val ~= false then " +
                    "return tonumber(val);" +
                "end; " +

                "local mode = redis.call('hget', KEYS[1], 'mode'); " +
                "if (mode == false) then " +
                    "redis.call(ARGV[4], KEYS[2], ARGV[1]); " +
                    "redis.call('set', KEYS[3], 1, 'px', ARGV[5]); " +
                    "return 1; " +
                "end;" +
                "if (mode == 'write') then " +
                    "local lockExists = redis.call('hexists', KEYS[1], ARGV[3]); " +
                    "if (lockExists == 0) then " +
                        "return nil;" +
                    "else " +
                        "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                        "if (counter > 0) then " +
                            "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                            "redis.call('set', KEYS[3], 0, 'px', ARGV[5]); " +
                            "return 0; " +
                        "else " +
                            "redis.call('hdel', KEYS[1], ARGV[3]); " +
                            "if (redis.call('hlen', KEYS[1]) == 1) then " +
                                "redis.call('del', KEYS[1]); " +
                                "redis.call(ARGV[4], KEYS[2], ARGV[1]); " +
                            "else " +
                                // has unlocked read-locks
                                "redis.call('hset', KEYS[1], 'mode', 'read'); " +
                            "end; " +
                            "redis.call('set', KEYS[3], 1, 'px', ARGV[5]); " +
                            "return 1; "+
                        "end; " +
                    "end; " +
                "end; "
                + "return nil;",
        Arrays.<Object>asList(getRawName(), getChannelName(), getUnlockLatchName(requestId)),
        LockPubSub.READ_UNLOCK_MESSAGE, internalLockLeaseTime, getLockName(threadId), getSubscribeService().getPublishCommand(), timeout);
    }
    
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected CompletionStage<Boolean> renewExpirationAsync(long threadId) {
        CompletionStage<Boolean> f = super.renewExpirationAsync(threadId);
        return f.thenCompose(r -> {
            if (!r) {
                RedissonReadLock lock = new RedissonReadLock(commandExecutor, getRawName());
                return lock.renewExpirationAsync(threadId);
            }
            return CompletableFuture.completedFuture(r);
        });
    }

    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal(null, null);
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
              "if (redis.call('hget', KEYS[1], 'mode') == 'write') then " +
                  "redis.call('del', KEYS[1]); " +
                  "redis.call(ARGV[2], KEYS[2], ARGV[1]); " +
                  "return 1; " +
              "end; " +
              "return 0; ",
              Arrays.asList(getRawName(), getChannelName()),
                LockPubSub.READ_UNLOCK_MESSAGE, getSubscribeService().getPublishCommand());
    }

    @Override
    public boolean isLocked() {
        RFuture<String> future = commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.HGET, getRawName(), "mode");
        String res = get(future);
        return "write".equals(res);
    }

}
