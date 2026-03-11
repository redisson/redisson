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

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.decoder.MapValueDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.renewal.LockRenewalScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Base class for implementing distributed locks
 *
 * @author Danila Varatyntsev
 * @author Nikita Koksharov
 */
public abstract class RedissonBaseLock extends RedissonExpirable implements RLock {

    private static final Logger log = LoggerFactory.getLogger(RedissonBaseLock.class);

    final String id;
    final String entryName;
    final String namespace;

    final LockRenewalScheduler renewalScheduler;

    public RedissonBaseLock(CommandAsyncExecutor commandExecutor, String name, String namespace) {
        super(commandExecutor, addNamespaceIfExist(namespace, name));
        this.id = getServiceManager().getId();
        this.entryName = id + ":" + name;
        this.renewalScheduler = getServiceManager().getRenewalScheduler();
        this.namespace = namespace;
    }

    public RedissonBaseLock(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor, name, null);
    }

    protected String getEntryName() {
        return entryName;
    }

    protected String getLockName(long threadId) {
        return id + ":" + threadId;
    }

    protected void scheduleExpirationRenewal(long threadId) {
        renewalScheduler.renewLock(getRawName(), threadId, getLockName(threadId));
    }

    protected void cancelExpirationRenewal(Long threadId, Boolean unlockResult) {
        renewalScheduler.cancelLockRenewal(getRawName(), threadId);
    }

    protected final <T> RFuture<T> evalWriteSyncedNoRetryAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return commandExecutor.syncedEvalNoRetry(key, codec, evalCommandType, script, keys, params);
    }

    protected void acquireFailed(long waitTime, TimeUnit unit, long threadId) {
        commandExecutor.get(acquireFailedAsync(waitTime, unit, threadId));
    }

    protected void trySuccessFalse(long currentThreadId, CompletableFuture<Boolean> result) {
        acquireFailedAsync(-1, null, currentThreadId).whenComplete((res, e) -> {
            if (e == null) {
                result.complete(false);
            } else {
                result.completeExceptionally(e);
            }
        });
    }

    protected CompletableFuture<Void> acquireFailedAsync(long waitTime, TimeUnit unit, long threadId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Condition newCondition() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() {
        return isExists();
    }
    
    @Override
    public RFuture<Boolean> isLockedAsync() {
        return isExistsAsync();
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return isHeldByThread(Thread.currentThread().getId());
    }

    @Override
    public boolean isHeldByThread(long threadId) {
        return get(isHeldByThreadAsync(threadId));
    }

    @Override
    public RFuture<Boolean> isHeldByThreadAsync(long threadId) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.HEXISTS, getRawName(), getLockName(threadId));
    }

    private static final RedisCommand<Integer> HGET = new RedisCommand<Integer>("HGET", new MapValueDecoder(), new IntegerReplayConvertor(0));
    
    public RFuture<Integer> getHoldCountAsync() {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, HGET, getRawName(), getLockName(Thread.currentThread().getId()));
    }
    
    @Override
    public int getHoldCount() {
        return get(getHoldCountAsync());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return forceUnlockAsync();
    }

    @Override
    public RFuture<Void> unlockAsync() {
        long threadId = Thread.currentThread().getId();
        return unlockAsync(threadId);
    }

    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        String requestId = getServiceManager().generateId();
        return getServiceManager().execute(() -> unlockAsync0(threadId, requestId));
    }

    private RFuture<Void> unlockAsync0(long threadId, String requestId) {
        CompletionStage<Boolean> future = unlockInnerAsync(threadId, requestId);
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
    public void unlock() {
        try {
            get(unlockAsync(Thread.currentThread().getId()));
        } catch (RedisException e) {
            if (e.getCause() instanceof IllegalMonitorStateException) {
                throw (IllegalMonitorStateException) e.getCause();
            } else {
                throw e;
            }
        }

//        Future<Void> future = unlockAsync();
//        future.awaitUninterruptibly();
//        if (future.isSuccess()) {
//            return;
//        }
//        if (future.cause() instanceof IllegalMonitorStateException) {
//            throw (IllegalMonitorStateException)future.cause();
//        }
//        throw commandExecutor.convertException(future);
    }

    @Override
    public boolean forceUnlock() {
        return get(forceUnlockAsync());
    }

    String getUnlockLatchName(String requestId) {
        String prefix = addNamespaceIfExist(this.namespace, "redisson_unlock_latch");
        return prefixName(prefix, getRawName()) + ":" + requestId;
    }

    static String addNamespaceIfExist(String namespace, String name) {
        if (namespace == null || namespace.trim().isEmpty()) {
            return name;
        } else {
            return prefixName(namespace, name);
        }
    }

    protected abstract RFuture<Boolean> unlockInnerAsync(long threadId, String requestId, int timeout);

    protected final RFuture<Boolean> unlockInnerAsync(long threadId, String requestId) {
        if (requestId == null) {
            requestId = getServiceManager().generateId();
        }
        MasterSlaveServersConfig config = getServiceManager().getConfig();
        long timeout = (config.getTimeout() + config.getRetryDelay().calcDelay(config.getRetryAttempts()).toMillis()) * config.getRetryAttempts();
        timeout = Math.max(timeout, 1);
        RFuture<Boolean> r = unlockInnerAsync(threadId, requestId, (int) timeout);
        String id = requestId;
        CompletionStage<Boolean> ff = r.thenApply(v -> {
            CommandAsyncExecutor ce = commandExecutor;
            if (ce instanceof CommandBatchService) {
                ce = new CommandBatchService(commandExecutor);
            }
            ce.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.DEL, getUnlockLatchName(id));
            if (ce instanceof CommandBatchService) {
                ((CommandBatchService) ce).executeAsync();
            }
            return v;
        });
        return new CompletableFutureWrapper<>(ff);
    }

    @Override
    public RFuture<Void> lockAsync() {
        return lockAsync(-1, null);
    }

    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit) {
        long currentThreadId = Thread.currentThread().getId();
        return lockAsync(leaseTime, unit, currentThreadId);
    }

    @Override
    public RFuture<Void> lockAsync(long currentThreadId) {
        return lockAsync(-1, null, currentThreadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync() {
        return tryLockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit) {
        return tryLockAsync(waitTime, -1, unit);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        long currentThreadId = Thread.currentThread().getId();
        return tryLockAsync(waitTime, leaseTime, unit, currentThreadId);
    }

    protected final <T> CompletionStage<T> handleNoSync(long threadId, CompletionStage<T> ttlRemainingFuture) {
        return commandExecutor.handleNoSync(ttlRemainingFuture, e -> unlockInnerAsync(threadId, null));
    }

    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        throw new UnsupportedOperationException();
    }

}
