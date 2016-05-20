/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.redisson.RedissonLock;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

/**
 * Groups multiple independent locks and handles them as one lock.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonMultiLock implements Lock {

    final List<RLock> locks = new ArrayList<RLock>();

    /**
     * Creates instance with multiple {@link RLock} objects.
     * Each RLock object could be created by own Redisson instance.
     *
     * @param locks
     */
    public RedissonMultiLock(RLock... locks) {
        if (locks.length == 0) {
            throw new IllegalArgumentException("Lock objects are not defined");
        }
        this.locks.addAll(Arrays.asList(locks));
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        Promise<Void> promise = ImmediateEventExecutor.INSTANCE.newPromise();

        long currentThreadId = Thread.currentThread().getId();
        lock(promise, 0, leaseTime, unit, locks, currentThreadId);

        promise.sync();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    private void lock(final Promise<Void> promise, final long waitTime, final long leaseTime, final TimeUnit unit, final List<RLock> locks, final long currentThreadId) throws InterruptedException {
        final AtomicInteger tryLockRequestsAmount = new AtomicInteger();
        final Map<Future<Boolean>, RLock> tryLockFutures = new HashMap<Future<Boolean>, RLock>(locks.size());

        FutureListener<Boolean> listener = new FutureListener<Boolean>() {

            AtomicReference<RLock> lockedLockHolder = new AtomicReference<RLock>();
            AtomicReference<Throwable> failed = new AtomicReference<Throwable>();

            @Override
            public void operationComplete(final Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    failed.compareAndSet(null, future.cause());
                }

                Boolean res = future.getNow();
                if (res != null && !res) {
                    RLock lock = tryLockFutures.get(future);
                    lockedLockHolder.compareAndSet(null, lock);
                }

                if (tryLockRequestsAmount.decrementAndGet() == 0) {
                    if (lockedLockHolder.get() == null && failed.get() == null) {
                        promise.setSuccess(null);
                        return;
                    }

                    tryLockRequestsAmount.set(tryLockFutures.size());
                    for (RLock lock : tryLockFutures.values()) {
                        lock.unlockAsync().addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (tryLockRequestsAmount.decrementAndGet() == 0) {
                                    if (failed.get() != null) {
                                        promise.setFailure(failed.get());
                                    } else if (lockedLockHolder.get() != null) {
                                        final RedissonLock lockedLock = (RedissonLock) lockedLockHolder.get();
                                        lockedLock.lockAsync(leaseTime, unit, currentThreadId).addListener(new FutureListener<Void>() {
                                            @Override
                                            public void operationComplete(Future<Void> future) throws Exception {
                                                if (!future.isSuccess()) {
                                                    promise.setFailure(future.cause());
                                                    return;
                                                }
                                                
                                                List<RLock> newLocks = new ArrayList<RLock>(tryLockFutures.values());
                                                newLocks.remove(lockedLock);
                                                lock(promise, waitTime, leaseTime, unit, newLocks, currentThreadId);
                                            }
                                        });
                                    }
                                }
                            }
                        });
                    }
                }
            }
        };

        for (RLock lock : locks) {
            if (lock.isHeldByCurrentThread()) {
                continue;
            }

            tryLockRequestsAmount.incrementAndGet();
            Future<Boolean> future;
            if (waitTime > 0 || leaseTime > 0) {
                future = ((RedissonLock)lock).tryLockAsync(waitTime, leaseTime, unit, currentThreadId);
            } else {
                future = ((RedissonLock)lock).tryLockAsync(currentThreadId);
            }
            tryLockFutures.put(future, lock);
        }

        for (Future<Boolean> future : tryLockFutures.keySet()) {
            future.addListener(listener);
        }
    }

    @Override
    public boolean tryLock() {
        List<Future<Boolean>> tryLockFutures = new ArrayList<Future<Boolean>>(locks.size());
        for (RLock lock : locks) {
            tryLockFutures.add(lock.tryLockAsync());
        }

        for (Future<Boolean> future : tryLockFutures) {
            try {
                if (!future.syncUninterruptibly().getNow()) {
                    unlockInner();
                    return false;
                }
            } catch (RuntimeException e) {
                unlockInner();
                throw e;
            }
        }

        return true;
    }

    private void unlockInner() {
        List<Future<Void>> futures = new ArrayList<Future<Void>>(locks.size());
        for (RLock lock : locks) {
            futures.add(lock.unlockAsync());
        }

        for (Future<Void> unlockFuture : futures) {
            unlockFuture.awaitUninterruptibly();
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        List<Future<Boolean>> tryLockFutures = new ArrayList<Future<Boolean>>(locks.size());
        for (RLock lock : locks) {
            tryLockFutures.add(lock.tryLockAsync(waitTime, leaseTime, unit));
        }

        for (Future<Boolean> future : tryLockFutures) {
            try {
                if (!future.syncUninterruptibly().getNow()) {
                    unlockInner();
                    return false;
                }
            } catch (RuntimeException e) {
                unlockInner();
                throw e;
            }
        }

        return true;
    }


    @Override
    public void unlock() {
        List<Future<Void>> futures = new ArrayList<Future<Void>>(locks.size());

        for (RLock lock : locks) {
            futures.add(lock.unlockAsync());
        }

        for (Future<Void> future : futures) {
            future.syncUninterruptibly();
        }
    }


    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

}
