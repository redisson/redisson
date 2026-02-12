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
package org.redisson.transaction;

import org.redisson.RedissonMultiLock;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BaseTransactionalObject {

    final String transactionId;
    final String lockName;
    final CommandAsyncExecutor commandExecutor;

    public BaseTransactionalObject(String transactionId, String lockName, CommandAsyncExecutor commandExecutor) {
        this.transactionId = transactionId;
        this.lockName = lockName;
        this.commandExecutor = commandExecutor;
    }

    public RFuture<Boolean> moveAsync(int database) {
        throw new UnsupportedOperationException("move method is not supported in transaction");
    }
    
    public RFuture<Void> migrateAsync(String host, int port, int database) {
        throw new UnsupportedOperationException("migrate method is not supported in transaction");
    }

    protected RLock getWriteLock() {
        return new RedissonTransactionalWriteLock(commandExecutor, lockName, transactionId);
    }

    protected RLock getReadLock() {
        return new RedissonTransactionalReadLock(commandExecutor, lockName, transactionId);
    }

    protected static String getLockName(String name) {
        return name + ":transaction_lock";
    }

    protected <R> RFuture<R> executeLocked(long timeout, Supplier<CompletionStage<R>> runnable, RLock lock) {
        return executeLocked(Thread.currentThread().getId(), timeout, runnable, lock);
    }

    protected <R> RFuture<R> executeLocked(long threadId, long timeout, Supplier<CompletionStage<R>> runnable, RLock lock) {
        CompletionStage<R> f = lock.lockAsync(timeout, TimeUnit.MILLISECONDS, threadId).thenCompose(res -> runnable.get());
        return new CompletableFutureWrapper<>(f);
    }

    protected <R> RFuture<R> executeLocked(long timeout, Supplier<CompletionStage<R>> runnable, List<RLock> locks) {
        RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[0]));
        long threadId = Thread.currentThread().getId();
        CompletionStage<R> f = multiLock.lockAsync(timeout, TimeUnit.MILLISECONDS)
                .thenCompose(res -> runnable.get())
                .whenComplete((r, e) -> {
                    if (e != null) {
                        multiLock.unlockAsync(threadId);
                    }
                });
        return new CompletableFutureWrapper<>(f);
    }

}
