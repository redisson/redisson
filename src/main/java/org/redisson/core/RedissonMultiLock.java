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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

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
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Object> result = new AtomicReference<Object>();
        Promise<Void> promise = new DefaultPromise<Void>() {
            public Promise<Void> setSuccess(Void result) {
                latch.countDown();
                return this;
            };

            public Promise<Void> setFailure(Throwable cause) {
                result.set(cause);
                latch.countDown();
                return this;
            };
        };

        lock(promise, 0, leaseTime, unit);

        latch.await();
        if (result.get() instanceof Throwable) {
            PlatformDependent.throwException((Throwable)result.get());
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Object> result = new AtomicReference<Object>();
        Promise<Void> promise = new DefaultPromise<Void>() {
            public Promise<Void> setSuccess(Void result) {
                latch.countDown();
                return this;
            };

            public Promise<Void> setFailure(Throwable cause) {
                result.set(cause);
                latch.countDown();
                return this;
            };
        };

        lock(promise, 0, -1, null);

        latch.await();
        if (result.get() instanceof Throwable) {
            PlatformDependent.throwException((Throwable)result.get());
        }
    }

    private void lock(final Promise<Void> promise, final long waitTime, final long leaseTime, final TimeUnit unit) throws InterruptedException {
        final AtomicInteger tryLockRequestsAmount = new AtomicInteger();
        final Map<Future<Boolean>, RLock> tryLockFutures = new HashMap<Future<Boolean>, RLock>(locks.size());

        FutureListener<Boolean> listener = new FutureListener<Boolean>() {

            AtomicBoolean unlock = new AtomicBoolean();

            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    // unlock once
                    if (unlock.compareAndSet(false, true)) {
                        for (RLock lock : locks) {
                            lock.unlockAsync();
                        }

                        promise.setFailure(future.cause());
                    }
                    return;
                }

                Boolean res = future.getNow();
                // unlock once
                if (!res && unlock.compareAndSet(false, true)) {
                    for (RLock lock : locks) {
                        lock.unlockAsync();
                    }

                    RLock lock = tryLockFutures.get(future);
                    lock.lockAsync().addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.setFailure(future.cause());
                                return;
                            }

                            lock(promise, waitTime, leaseTime, unit);
                        }
                    });
                }
                if (!unlock.get() && tryLockRequestsAmount.decrementAndGet() == 0) {
                    promise.setSuccess(null);
                }
            }
        };

        for (RLock lock : locks) {
            if (lock.isHeldByCurrentThread()) {
                continue;
            }

            tryLockRequestsAmount.incrementAndGet();
            if (waitTime > 0 || leaseTime > 0) {
                tryLockFutures.put(lock.tryLockAsync(waitTime, leaseTime, unit), lock);
            } else {
                tryLockFutures.put(lock.tryLockAsync(), lock);
            }
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
