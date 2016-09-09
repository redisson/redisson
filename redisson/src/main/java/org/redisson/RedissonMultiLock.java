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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Groups multiple independent locks and manages them as one lock.
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
        RPromise<Void> promise = new RedissonPromise<Void>();

        long currentThreadId = Thread.currentThread().getId();            
        Queue<RLock> lockedLocks = new ConcurrentLinkedQueue<RLock>();
        lock(promise, 0, leaseTime, unit, locks, currentThreadId, lockedLocks);

        promise.sync();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    private void lock(final RPromise<Void> promise, final long waitTime, final long leaseTime, final TimeUnit unit, 
                        final List<RLock> locks, final long currentThreadId, final Queue<RLock> lockedLocks) throws InterruptedException {
        final AtomicInteger tryLockRequestsAmount = new AtomicInteger();
        final Map<Future<Boolean>, RLock> tryLockFutures = new HashMap<Future<Boolean>, RLock>(locks.size());

        FutureListener<Boolean> listener = new FutureListener<Boolean>() {

            AtomicReference<RLock> lockedLockHolder = new AtomicReference<RLock>();
            AtomicReference<Throwable> failed = new AtomicReference<Throwable>();

            @Override
            public void operationComplete(final Future<Boolean> future) throws Exception {
                if (isLockFailed(future)) {
                    failed.compareAndSet(null, future.cause());
                }

                Boolean res = future.getNow();
                if (res != null) {
                    RLock lock = tryLockFutures.get(future);
                    if (res) {
                        lockedLocks.add(lock);
                    } else {
                        lockedLockHolder.compareAndSet(null, lock);
                    }
                }

                if (tryLockRequestsAmount.decrementAndGet() == 0) {
                    if (isAllLocksAcquired(lockedLockHolder, failed, lockedLocks)) {
                        if (!promise.trySuccess(null)) {
                            unlockInner(lockedLocks);
                        }
                        return;
                    }

                    if (lockedLocks.isEmpty()) {
                        tryLockAgain(promise, waitTime, leaseTime, unit, currentThreadId, tryLockFutures);
                        return;
                    }
                    
                    final AtomicInteger locksToUnlockAmount = new AtomicInteger(lockedLocks.size());
                    for (RLock lock : lockedLocks) {
                        lock.unlockAsync().addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (locksToUnlockAmount.decrementAndGet() == 0) {
                                    tryLockAgain(promise, waitTime, leaseTime, unit, currentThreadId, tryLockFutures);
                                }
                            }
                        });
                    }
                }
            }

            protected void tryLockAgain(final RPromise<Void> promise, final long waitTime, final long leaseTime,
                    final TimeUnit unit, final long currentThreadId, final Map<Future<Boolean>, RLock> tryLockFutures) throws InterruptedException {
                lockedLocks.clear();
                if (failed.get() != null) {
                    promise.tryFailure(failed.get());
                } else if (lockedLockHolder.get() != null) {
                    final RedissonLock lockedLock = (RedissonLock) lockedLockHolder.get();
                    lockedLock.lockAsync(leaseTime, unit, currentThreadId).addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }
                            
                            lockedLocks.add(lockedLock);
                            List<RLock> newLocks = new ArrayList<RLock>(tryLockFutures.values());
                            newLocks.remove(lockedLock);
                            lock(promise, waitTime, leaseTime, unit, newLocks, currentThreadId, lockedLocks);
                        }
                    });
                } else {
                    lock(promise, waitTime, leaseTime, unit, locks, currentThreadId, lockedLocks);
                }
            }
        };

        for (RLock lock : locks) {
            tryLockRequestsAmount.incrementAndGet();
            Future<Boolean> future;
            if (waitTime > 0 || leaseTime > 0) {
                future = ((RedissonPromise)((RedissonLock)lock).tryLockAsync(waitTime, leaseTime, unit, currentThreadId)).getInnerPromise();
            } else {
                future = ((RedissonPromise)(((RedissonLock)lock).tryLockAsync(currentThreadId))).getInnerPromise();
            }
            
            if (future instanceof RedissonPromise) {
                future = ((RedissonPromise<Boolean>)future).getInnerPromise();
            }
            
            tryLockFutures.put(future, lock);
        }

        for (Future<Boolean> future : tryLockFutures.keySet()) {
            future.addListener(listener);
        }
    }

    @Override
    public boolean tryLock() {
        Map<RLock, RFuture<Boolean>> tryLockFutures = new HashMap<RLock, RFuture<Boolean>>(locks.size());
        for (RLock lock : locks) {
            tryLockFutures.put(lock, lock.tryLockAsync());
        }

        return sync(tryLockFutures);
    }

    protected boolean sync(Map<RLock, RFuture<Boolean>> tryLockFutures) {
        List<RLock> lockedLocks = new ArrayList<RLock>(tryLockFutures.size());
        RuntimeException latestException = null;
        for (Entry<RLock, RFuture<Boolean>> entry : tryLockFutures.entrySet()) {
            try {
                if (entry.getValue().syncUninterruptibly().getNow()) {
                    lockedLocks.add(entry.getKey());
                }
            } catch (RuntimeException e) {
                latestException = e;
            }
        }
        
        if (lockedLocks.size() < tryLockFutures.size()) {
            unlockInner(lockedLocks);
            if (latestException != null) {
                throw latestException;
            }
            return false;
        }
        
        return true;
    }

    protected void unlockInner(Collection<RLock> locks) {
        List<RFuture<Void>> futures = new ArrayList<RFuture<Void>>(locks.size());
        for (RLock lock : locks) {
            futures.add(lock.unlockAsync());
        }

        for (RFuture<Void> unlockFuture : futures) {
            unlockFuture.awaitUninterruptibly();
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        Map<RLock, RFuture<Boolean>> tryLockFutures = new HashMap<RLock, RFuture<Boolean>>(locks.size());
        for (RLock lock : locks) {
            tryLockFutures.put(lock, lock.tryLockAsync(waitTime, leaseTime, unit));
        }

        return sync(tryLockFutures);
    }


    @Override
    public void unlock() {
        List<RFuture<Void>> futures = new ArrayList<RFuture<Void>>(locks.size());

        for (RLock lock : locks) {
            futures.add(lock.unlockAsync());
        }

        for (RFuture<Void> future : futures) {
            future.syncUninterruptibly();
        }
    }


    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    protected boolean isLockFailed(Future<Boolean> future) {
        return !future.isSuccess();
    }

    protected boolean isAllLocksAcquired(AtomicReference<RLock> lockedLockHolder, AtomicReference<Throwable> failed, Queue<RLock> lockedLocks) {
        return lockedLockHolder.get() == null && failed.get() == null;
    }

}
