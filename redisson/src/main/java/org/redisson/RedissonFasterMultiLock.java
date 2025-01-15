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

import io.netty.buffer.ByteBuf;
import io.netty.util.Timeout;
import org.redisson.api.RFuture;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;
import org.redisson.pubsub.LockPubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * RedissonFasterMultiLock.<br/>
 * All lock, unlock, lockAsync unlockAsync methods only success when all values locked succeed. <br/>
 * Example:  <br/>
 * there is a class, id is 100, and three students in class, Jack(id:001),Mary(id:002) <br/>
 * <ul>
 * <li>current thread id : 1
 * <li>ServiceManager id: 71b96ce8-2746......
 * <li>current time stamp: 1727422868000
 * </ul>
 * when {@code redissonBatchLock.lock("class_100",Arrays.asList("Jack_001","Mary_002")} <br/>
 * It will be saved In redis like this:
 * <PRE>
 * -----------------------------------------------------------------------
 * | redis type: hash                                                    |
 * | redis Key: class_100                                                |
 * -----------------------------------------------------------------------
 * | field                                           | value             |
 * -----------------------------------------------------------------------
 * | Jack_001                                        | 71b96ce8-2746:1   |
 * | Mary_002                                        | 71b96ce8-2746:1   |
 * | Jack_001:71b96ce8-2746:1:expire_time            | 1,727,422,898,000 |
 * | Jack_001:71b96ce8-2746:1:lock_count             | 1                 |
 * | Mary_002:71b96ce8-2746:1:expire_time            | 1,727,422,898,000 |
 * | Mary_002:71b96ce8-2746:1:lock_count             | 1                 |
 * -----------------------------------------------------------------------
 * </PRE>
 * <strong>Attention: the value of <code>group</code> should be `smallest`, in our example above ,
 * <code>group</code> should be  'class_100' not just 'class' </strong><br/>
 * Of course the values `Jack_001`,`Mary_002` will be encoded and hashed.
 *
 * @author lyrric
 *
 */
public class RedissonFasterMultiLock extends RedissonBaseLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonFasterMultiLock.class);

    protected long internalLockLeaseTime;

    private final LockPubSub pubSub;

    private final CommandAsyncExecutor commandExecutor;

    private final String key;

    private final Collection<String> fields;

    public RedissonFasterMultiLock(CommandAsyncExecutor commandExecutor, String group, Collection<Object> values) {
        super(commandExecutor, group);
        this.commandExecutor = commandExecutor;
        this.pubSub = commandExecutor.getConnectionManager().getSubscribeService().getLockPubSub();
        this.internalLockLeaseTime = getServiceManager().getCfg().getLockWatchdogTimeout();
        this.key = group;
        fields = hashValues(values);
    }

    private Collection<String> hashValues(Collection<Object> values) {
        return values.stream()
                .map(this::hashValue)
                .collect(Collectors.toSet());

    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        lock(leaseTime, unit, true);
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return true;
        }

        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            acquireFailed(waitTime, unit, threadId);
            return false;
        }

        current = System.currentTimeMillis();
        CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
        try {
            subscribeFuture.get(time, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (!subscribeFuture.completeExceptionally(new RedisTimeoutException(
                    "Unable to acquire subscription lock after " + time + "ms. " +
                            "Try to increase 'subscriptionsPerConnection' and/or 'subscriptionConnectionPoolSize' parameters."))) {
                subscribeFuture.whenComplete((res, ex) -> {
                    if (ex == null) {
                        unsubscribe(res);
                    }
                });
            }
            acquireFailed(waitTime, unit, threadId);
            return false;
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            acquireFailed(waitTime, unit, threadId);
            return false;
        }

        try {
            time -= System.currentTimeMillis() - current;
            if (time <= 0) {
                acquireFailed(waitTime, unit, threadId);
                return false;
            }

            while (true) {
                long currentTime = System.currentTimeMillis();
                ttl = tryAcquire(leaseTime, unit, threadId);
                // lock acquired
                if (ttl == null) {
                    return true;
                }

                time -= System.currentTimeMillis() - currentTime;
                if (time <= 0) {
                    acquireFailed(waitTime, unit, threadId);
                    return false;
                }

                // waiting for message
                currentTime = System.currentTimeMillis();
                if (ttl >= 0 && ttl < time) {
                    commandExecutor.getNow(subscribeFuture).getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    commandExecutor.getNow(subscribeFuture).getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);
                }

                time -= System.currentTimeMillis() - currentTime;
                if (time <= 0) {
                    acquireFailed(waitTime, unit, threadId);
                    return false;
                }
            }
        } finally {
            unsubscribe(commandExecutor.getNow(subscribeFuture));
        }
    }
    private String hashValue(Object key) {
        ByteBuf objectState = encode(key);
        try {
            return Hash.hash128toBase64(objectState);
        } finally {
            objectState.release();
        }
    }
    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lock(leaseTime, unit, false);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }

    private void lock(long leaseTime, TimeUnit unit, boolean interruptible) throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return;
        }
        //failed first time
        CompletableFuture<RedissonLockEntry> future = subscribe();
        pubSub.timeout(future);
        RedissonLockEntry entry;
        if (interruptible) {
            entry = commandExecutor.getInterrupted(future);
        } else {
            entry = commandExecutor.get(future);
        }
        //future.cancel(true);
        try {
            while (true) {
                ttl = tryAcquire(leaseTime, unit, threadId);
                // lock acquired
                if (ttl == null) {
                    break;
                }

                // waiting for message
                if (ttl >= 0) {
                    try {
                        entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        if (interruptible) {
                            throw e;
                        }
                        entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                    }
                } else {
                    if (interruptible) {
                        entry.getLatch().acquire();
                    } else {
                        entry.getLatch().acquireUninterruptibly();
                    }
                }
            }
        } finally {
            unsubscribe(entry);
        }
    }
    protected void unsubscribe(RedissonLockEntry entry) {
        pubSub.unsubscribe(entry, getEntryName(), getChannelName());
    }
    protected CompletableFuture<RedissonLockEntry> subscribe() {
        return pubSub.subscribe(getEntryName(), getChannelName());
    }
    String getChannelName() {
        return prefixName("redisson_lock__channel", getRawName());
    }

    private RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, long threadId) {
        RFuture<Long> ttlRemainingFuture;
        if (leaseTime > 0) {
            ttlRemainingFuture = tryLockInnerAsync(leaseTime, unit, threadId);
        } else {
            ttlRemainingFuture = tryLockInnerAsync(internalLockLeaseTime, TimeUnit.MILLISECONDS, threadId);
        }
        CompletionStage<Long> s = handleNoSync(threadId, ttlRemainingFuture);
        ttlRemainingFuture = new CompletableFutureWrapper<>(s);

        CompletionStage<Long> f = ttlRemainingFuture.thenApply(ttlRemaining -> {
            // lock acquired
            if (ttlRemaining == null) {
                if (leaseTime > 0) {
                    internalLockLeaseTime = unit.toMillis(leaseTime);
                } else {
                    scheduleExpirationRenewal(threadId);
                }
            }
            return ttlRemaining;
        });
        return new CompletableFutureWrapper<>(f);
    }
    private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync0(leaseTime, unit, threadId));
    }

    private RFuture<Long> tryAcquireAsync0(long leaseTime, TimeUnit unit, long threadId) {
        return getServiceManager().execute(() -> tryAcquireAsync(leaseTime, unit, threadId));
    }

    private RFuture<Boolean> tryAcquireOnceAsync(long leaseTime, TimeUnit unit, long threadId) {
        CompletionStage<Boolean> acquiredFuture;
        if (leaseTime > 0) {
            acquiredFuture = tryLockOnceInnerAsync(leaseTime, unit, RedisCommands.EVAL_BOOLEAN, threadId);
        } else {
            acquiredFuture = tryLockOnceInnerAsync(internalLockLeaseTime,
                    TimeUnit.MILLISECONDS, RedisCommands.EVAL_BOOLEAN, threadId);
        }

        acquiredFuture = handleNoSync(threadId, acquiredFuture);

        CompletionStage<Boolean> f = acquiredFuture.thenApply(acquired -> {
            // lock acquired
            if (acquired) {
                if (leaseTime > 0) {
                    internalLockLeaseTime = unit.toMillis(leaseTime);
                } else {
                    scheduleExpirationRenewal(threadId);
                }
            }
            return acquired;
        });
        return new CompletableFutureWrapper<>(f);

    }

    @Override
    protected void cancelExpirationRenewal(Long threadId, Boolean unlockResult) {
        renewalScheduler.cancelFastMultilockRenewl(getRawName(), threadId);
    }

    @Override
    protected void scheduleExpirationRenewal(long threadId) {
        renewalScheduler.renewFastMultiLock(getRawName(), threadId, getLockName(threadId), fields);
    }

    private <T> RFuture<T> tryLockOnceInnerAsync(long leaseTime, TimeUnit unit, RedisStrictCommand<T> command, long threadId) {
        List<String> params = new ArrayList<>();
        params.add(String.valueOf(System.currentTimeMillis()));
        params.add(String.valueOf(unit.toMillis(leaseTime)));
        params.add(getLockName(threadId));
        params.addAll(fields);
        return commandExecutor.syncedEval(key, StringCodec.INSTANCE, command,
                "local currentTime = tonumber(ARGV[1]);" +
                        "local leaseTime = tonumber(ARGV[2]);" +
                        "local currentThread = ARGV[3];" +
                        "local keyExist = nil;" +
                        "if (redis.call('exists',KEYS[1]) > 0) then" +
                        "   keyExist = 1;" +
                        "   for i=4, #ARGV, 1 do " +
                        "       local lockThread = redis.call('hget', KEYS[1], ARGV[i]); " +
                        "       if(lockThread ~= false and currentThread ~= lockThread ) then " +
                        "           local expireFieldName = ARGV[i]..':'..lockThread..':expire_time'" +
                        "           local expireTime = redis.call('hget', KEYS[1], expireFieldName);" +
                        "           if( tonumber(expireTime) > currentTime and currentThread ~= lockThread) then " +
                        "               return 0;" +
                        "           end" +
                        "       end; " +
                        "   end; " +
                        "else" +
                        "   keyExist = 0;" +
                        "end;" +
                        "for i=4, #ARGV, 1 do " +
                        "   redis.call('hset', KEYS[1], ARGV[i], currentThread); " +
                        "   local expireFieldName = ARGV[i]..':'..currentThread..':expire_time'" +
                        "   local expireTime = redis.call('hget', KEYS[1], expireFieldName); " +
                        "   local newExpireTime = leaseTime + currentTime " +
                        "   if(expireTime == false or tonumber(expireTime) < newExpireTime ) then" +
                        "      redis.call('hset', KEYS[1], expireFieldName, newExpireTime); " +
                        "   end;" +
                        "   redis.call('HINCRBY', KEYS[1], ARGV[i]..':'..currentThread..':lock_count', 1); " +
                        "end; " +
                        "if(keyExist == 1) then" +
                        "   local expireTime = redis.call('pttl',KEYS[1]);" +
                        "   if(tonumber(expireTime) > 0 and leaseTime > tonumber(expireTime)) then" +
                        "       redis.call('pexpire',KEYS[1], leaseTime);" +
                        "   end;"+
                        "else" +
                        "    redis.call('pexpire',KEYS[1], leaseTime);" +
                        "end;" +
                        "return 1;",
                Collections.singletonList(getRawName()), params.toArray());
    }

    private RFuture<Long> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId) {
        List<String> params = new ArrayList<>();
        params.add(String.valueOf(unit.toMillis(leaseTime)));
        params.add(String.valueOf(System.currentTimeMillis()));
        params.add(getLockName(threadId));
        params.addAll(fields);
        return commandExecutor.syncedEval(key, StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                        "local leaseTime = tonumber(ARGV[1]);" +
                                "local currentTime = tonumber(ARGV[2]);" +
                                "local currentThread = ARGV[3];" +
                                "local maxExpireTime = -1;" +
                                "local keyExist = nil;" +
                                "if (tonumber(redis.call('exists',KEYS[1])) > 0) then" +
                                "   keyExist = 1;" +
                                "   for i=4, #ARGV, 1 do " +
                                "       local lockThread = redis.call('hget', KEYS[1], ARGV[i]); " +
                                "       if(lockThread ~= false and lockThread ~= currentThread) then " +
                                "           local expireFieldName = ARGV[i]..':'..lockThread..':expire_time';" +
                                "           local expireTime = redis.call('hget', KEYS[1], expireFieldName);" +
                                "           if(expireTime ~= false and tonumber(expireTime) > currentTime ) then " +
                                "               if(tonumber(expireTime) > maxExpireTime) then" +
                                "                   maxExpireTime = tonumber(expireTime);" +
                                "               end;" +
                                "           end" +
                                "       end; " +
                                "   end; " +
                                "else" +
                                "   keyExist = 0;" +
                                "end;" +
                                "if( maxExpireTime ~= -1) then" +
                                "   return maxExpireTime-currentTime;" +
                                "end;" +
                                "for i=4, #ARGV, 1 do " +
                                "   redis.call('hset', KEYS[1], ARGV[i], currentThread); " +
                                "   local expireFieldName = ARGV[i]..':'..currentThread..':expire_time'" +
                                "   local expireTime = redis.call('hget', KEYS[1], expireFieldName); " +
                                "   local newExpireTime = leaseTime + currentTime " +
                                "   if(expireTime == false or tonumber(expireTime) < newExpireTime ) then" +
                                "      redis.call('hset', KEYS[1], expireFieldName, newExpireTime); " +
                                "   end;" +
                                "   redis.call('HINCRBY', KEYS[1], ARGV[i]..':'..currentThread..':lock_count', 1); " +
                                "end; " +
                                "if(keyExist == 1) then" +
                                "   local expireTime = redis.call('pttl',KEYS[1]);" +
                                "   if(tonumber(expireTime) > 0 and leaseTime > tonumber(expireTime)) then" +
                                "       redis.call('pexpire',KEYS[1], leaseTime);" +
                                "   end;"+
                                "else" +
                                "    redis.call('pexpire',KEYS[1], leaseTime);" +
                                "end;" +
                                "return nil;",
                Collections.singletonList(getRawName()), params.toArray());
    }

    @Override
    public boolean forceUnlock() {
        return get(forceUnlockAsync());
    }

    @Override
    public boolean isLocked() {
        return get(isLockedAsync());
    }

    @Override
    public boolean isHeldByThread(long threadId) {
        return get(isHeldByThreadAsync(threadId));
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return isHeldByThread(Thread.currentThread().getId());
    }

    @Override
    public int getHoldCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long remainTimeToLive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lock() {
        try {
            lock(-1, null, false);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }


    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId, String requestId, int timeout) {
        List<Object> params = new ArrayList<>();
        params.add(getLockName(threadId));
        params.add(LockPubSub.UNLOCK_MESSAGE);
        params.add(internalLockLeaseTime);
        params.add(getSubscribeService().getPublishCommand());
        params.add(timeout);
        params.add(System.currentTimeMillis());
        params.addAll(fields);
        return commandExecutor.syncedEval(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local val = redis.call('get', KEYS[3]); " +
                        "if val ~= false then " +
                        "   return tonumber(val);" +
                        "end; " +
                        "if(redis.call('exists', KEYS[1]) == 0) then" +
                        "   redis.call(ARGV[4], KEYS[2], ARGV[2]); " +
                        "   return nil;" +
                        "end;" +
                        "local lockName = ARGV[1];" +
                        "local hasFailed = 0;" +
                        "local allDeleted = 1;"+
                        "local hasDeleted = 0;" +
                        "local newExpireTime = tonumber(ARGV[3]) + tonumber(ARGV[6]);"+
                        "for i = 7,#ARGV,1 do" +
                        "   local expireTime = redis.call('hget', KEYS[1], ARGV[i]..':'..lockName..':expire_time');" +
                        "   if (expireTime ~= false and tonumber(expireTime) >= tonumber(ARGV[6])) then" +
                        "       if(tonumber(redis.call('hincrby', KEYS[1], ARGV[i]..':'..lockName..':lock_count',-1)) <= 0) then" +
                        "           redis.call('hdel',KEYS[1], ARGV[i]..':'..lockName..':expire_time');" +
                        "           redis.call('hdel',KEYS[1], ARGV[i]..':'..lockName..':lock_count');" +
                        "           redis.call('hdel',KEYS[1], ARGV[i]);" +
                        "           hasDeleted = 1;" +
                        "       else" +
                        "           if(tonumber(expireTime) < newExpireTime) then " +
                        "               redis.call('hset', KEYS[1], ARGV[i]..':'..lockName..':expire_time', newExpireTime);" +
                        "           end;" +
                        "           allDeleted = 0;" +
                        "       end;" +
                        "   else" +
                        "       allDeleted = 0;" +
                        "       hasFailed = 1;" +
                        "   end;" +
                        "end;" +
                        "if(hasFailed ~= 0) then" +
                        "   return nil;" +
                        "end;" +
                        "if(hasDeleted) then" +
                        "   redis.call(ARGV[4], KEYS[2], ARGV[2]); " +
                        "end;"+
                        "if(allDeleted == 1) then" +
                        "   redis.call('set', KEYS[3], 1, 'px', ARGV[5]); " +
                        "else " +
                        "   redis.call('set', KEYS[3], 0, 'px', ARGV[5]); " +
                        "   local expireTime = redis.call('pttl',KEYS[1]);" +
                        "   if(tonumber(ARGV[3]) > tonumber(expireTime)) then" +
                        "       redis.call('pexpire',KEYS[1], ARGV[3]);" +
                        "   end;"+
                        "end;"+
                        "return allDeleted;",
                Arrays.asList(getRawName(), getChannelName(), getUnlockLatchName(requestId)),
                params.toArray());
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lock(-1, null, false);
    }

    @Override
    public boolean tryLock() {
        return get(tryLockAsync());
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }


    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal(null, null);
        List<Object> params = new ArrayList<>();
        params.add(LockPubSub.UNLOCK_MESSAGE);
        params.add(getSubscribeService().getPublishCommand());
        params.addAll(fields);
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local removeCount = 0;" +
                        "for i=3, #ARGV, 1 do " +
                        "    local lockName = redis.call('hget', KEYS[1], ARGV[i]); " +
                        "    local count = redis.call('HDEL', KEYS[1], ARGV[i]); " +
                        "    redis.call('HDEL', KEYS[1], ARGV[i]..':'..lockName..':expire_time'); " +
                        "    redis.call('HDEL', KEYS[1], ARGV[i]..':'..lockName..':lock_count'); " +
                        "    removeCount = removeCount+count;" +
                        "end; " +
                        "redis.call(ARGV[2], KEYS[2], ARGV[1]); "+
                        "return removeCount; ",
                Arrays.asList(getRawName(), getChannelName()),
                params.toArray());
    }

    @Override
    public RFuture<Void> unlockAsync() {
        return unlockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        return getServiceManager().execute(() -> unlockAsync0(threadId));
    }
    private RFuture<Void> unlockAsync0(long threadId) {
        CompletionStage<Boolean> future = unlockInnerAsync(threadId);
        CompletionStage<Void> f = future.handle((res, e) -> {
            cancelExpirationRenewal(threadId, res);

            if (e != null) {
                if (e instanceof CompletionException) {
                    throw (CompletionException) e;
                }
                throw new CompletionException(e);
            }
            if (res == null) {
                IllegalMonitorStateException cause = new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread by node id: "
                        + id + " thread-id: " + threadId);
                throw new CompletionException(cause);
            }

            return null;
        });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long currentThreadId) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        RFuture<Long> ttlFuture = tryLockInnerAsync(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                if (!result.complete(null)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            pubSub.timeout(subscribeFuture);
            subscribeFuture.whenComplete((res, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                    return;
                }

                lockAsync(leaseTime, unit, res, result, currentThreadId);
            });
        });

        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public RFuture<Boolean> tryLockAsync() {
        return getServiceManager().execute(() -> tryAcquireOnceAsync(-1, null, Thread.currentThread().getId()));
    }


    @Override
    public RFuture<Boolean> tryLockAsync(long threadId) {
        return getServiceManager().execute(() -> tryAcquireOnceAsync(-1, null, threadId));
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit) {
        return getServiceManager().execute(() -> tryLockAsync(waitTime, -1, unit, Thread.currentThread().getId()));
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        return getServiceManager().execute(() -> tryLockAsync(waitTime, leaseTime, unit, Thread.currentThread().getId()));
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long currentThreadId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        long currentTime = System.currentTimeMillis();
        RFuture<Long> ttlFuture = tryAcquireAsync0(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                if (!result.complete(true)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long el = System.currentTimeMillis() - currentTime;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                trySuccessFalse(currentThreadId, result);
                return;
            }

            long current = System.currentTimeMillis();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            pubSub.timeout(subscribeFuture, time.get());
            subscribeFuture.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                    return;
                }

                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                tryLockAsync(time, leaseTime, unit, r, result, currentThreadId);
            });
            if (!subscribeFuture.isDone()) {
                Timeout scheduledFuture = getServiceManager().newTimeout(timeout -> {
                    if (!subscribeFuture.isDone()) {
                        subscribeFuture.cancel(false);
                        trySuccessFalse(currentThreadId, result);
                    }
                }, time.get(), TimeUnit.MILLISECONDS);
                futureRef.set(scheduledFuture);
            }
        });


        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public RFuture<Boolean> isHeldByThreadAsync(long threadId) {
        List<String> params = new ArrayList<>();
        params.add(String.valueOf(System.currentTimeMillis()));
        params.add(getLockName(threadId));
        params.addAll(fields);
        return commandExecutor.syncedEval(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local currentTime = tonumber(ARGV[1]);" +
                        "local currentThread = ARGV[2];" +
                        "if (redis.call('exists',KEYS[1]) > 0) then" +
                        "   for i=4, #ARGV, 1 do " +
                        "       local expireTime = redis.call('hget', KEYS[1], ARGV[i]..':'..currentThread..':expire_time'); " +
                        "       if(expireTime == false or tonumber(expireTime) < currentTime) then " +
                        "           return 0;" +
                        "       end;" +
                        "   end; " +
                        "else" +
                        "   return 0;" +
                        "end;" +
                        "return 1;",
                Collections.singletonList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Integer> getHoldCountAsync() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * @return any one locked,return true,else false
     */
    @Override
    public RFuture<Boolean> isLockedAsync() {
        List<String> params = new ArrayList<>();
        params.add(String.valueOf(System.currentTimeMillis()));
        params.addAll(fields);
        return commandExecutor.syncedEval(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local currentTime = tonumber(ARGV[1]);" +
                        "for i = 2,#ARGV,1 do" +
                        "   local localThread = redis.call('hget', KEYS[1], ARGV[i]);" +
                        "   if(localThread ~= false) then" +
                        "       local expireTime = redis.call('hget', KEYS[1], ARGV[i]..':'..localThread..':expire_time');" +
                        "       if (expireTime ~= false and tonumber(expireTime) >= currentTime) then" +
                        "           return 1;" +
                        "       end;" +
                        "   end;" +
                        "end;" +
                        "return 0;",
                Collections.singletonList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        throw new UnsupportedOperationException();
    }


    private void tryLockAsync(AtomicLong time, long leaseTime, TimeUnit unit,
                              RedissonLockEntry entry, CompletableFuture<Boolean> result, long currentThreadId) {
        if (result.isDone()) {
            unsubscribe(entry);
            return;
        }

        if (time.get() <= 0) {
            unsubscribe(entry);
            trySuccessFalse(currentThreadId, result);
            return;
        }

        long curr = System.currentTimeMillis();
        RFuture<Long> ttlFuture = tryLockInnerAsync(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                unsubscribe(entry);
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                unsubscribe(entry);
                if (!result.complete(true)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                unsubscribe(entry);
                trySuccessFalse(currentThreadId, result);
                return;
            }

            // waiting for message
            long current = System.currentTimeMillis();
            if (entry.getLatch().tryAcquire()) {
                tryLockAsync(time, leaseTime, unit, entry, result, currentThreadId);
            } else {
                AtomicBoolean executed = new AtomicBoolean();
                AtomicReference<Timeout> futureRef = new AtomicReference<>();
                Runnable listener = () -> {
                    executed.set(true);
                    if (futureRef.get() != null) {
                        futureRef.get().cancel();
                    }

                    long elapsed = System.currentTimeMillis() - current;
                    time.addAndGet(-elapsed);

                    tryLockAsync(time, leaseTime, unit, entry, result, currentThreadId);
                };
                entry.addListener(listener);

                long t = time.get();
                if (ttl >= 0 && ttl < time.get()) {
                    t = ttl;
                }
                if (!executed.get()) {
                    Timeout scheduledFuture = getServiceManager().newTimeout(timeout -> {
                        if (entry.removeListener(listener)) {
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            tryLockAsync(time, leaseTime, unit, entry, result, currentThreadId);
                        }
                    }, t, TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }
        });
    }
    private void lockAsync(long leaseTime, TimeUnit unit,
                           RedissonLockEntry entry, CompletableFuture<Void> result, long currentThreadId) {
        RFuture<Long> ttlFuture = tryLockInnerAsync(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                unsubscribe(entry);
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                unsubscribe(entry);
                if (!result.complete(null)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            if (entry.getLatch().tryAcquire()) {
                lockAsync(leaseTime, unit, entry, result, currentThreadId);
            } else {
                // waiting for message
                AtomicReference<Timeout> futureRef = new AtomicReference<>();
                Runnable listener = () -> {
                    if (futureRef.get() != null) {
                        futureRef.get().cancel();
                    }
                    lockAsync(leaseTime, unit, entry, result, currentThreadId);
                };

                entry.addListener(listener);

                if (ttl >= 0) {
                    Timeout scheduledFuture = getServiceManager().newTimeout(timeout -> {
                        if (entry.removeListener(listener)) {
                            lockAsync(leaseTime, unit, entry, result, currentThreadId);
                        }
                    }, ttl, TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }
        });
    }

}
