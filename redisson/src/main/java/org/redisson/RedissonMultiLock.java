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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.RedisResponseTimeoutException;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.ThreadLocalRandom;

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
     * @param locks - array of locks
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
    
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit) {
        return lockAsync(leaseTime, unit, Thread.currentThread().getId());
    }
    
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long threadId) {
        long baseWaitTime = locks.size() * 1500;
        long waitTime = -1;
        if (leaseTime == -1) {
            waitTime = baseWaitTime;
        } else {
            leaseTime = unit.toMillis(leaseTime);
            waitTime = leaseTime;
            if (waitTime <= 2000) {
                waitTime = 2000;
            } else if (waitTime <= baseWaitTime) {
                waitTime = ThreadLocalRandom.current().nextLong(waitTime/2, waitTime);
            } else {
                waitTime = ThreadLocalRandom.current().nextLong(baseWaitTime, waitTime);
            }
        }
        
        RPromise<Void> result = new RedissonPromise<Void>();
        tryLockAsync(threadId, leaseTime, TimeUnit.MILLISECONDS, waitTime, result);
        return result;
    }
    
    protected void tryLockAsync(final long threadId, final long leaseTime, final TimeUnit unit, final long waitTime, final RPromise<Void> result) {
        tryLockAsync(waitTime, leaseTime, unit, threadId).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow()) {
                    result.trySuccess(null);
                } else {
                    tryLockAsync(threadId, leaseTime, unit, waitTime, result);
                }
            }
        });
    }


    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long baseWaitTime = locks.size() * 1500;
        long waitTime = -1;
        if (leaseTime == -1) {
            waitTime = baseWaitTime;
        } else {
            leaseTime = unit.toMillis(leaseTime);
            waitTime = leaseTime;
            if (waitTime <= 2000) {
                waitTime = 2000;
            } else if (waitTime <= baseWaitTime) {
                waitTime = ThreadLocalRandom.current().nextLong(waitTime/2, waitTime);
            } else {
                waitTime = ThreadLocalRandom.current().nextLong(baseWaitTime, waitTime);
            }
        }
        
        while (true) {
            if (tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(-1, -1, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
    
    protected RFuture<Void> unlockInnerAsync(Collection<RLock> locks, long threadId) {
        if (locks.isEmpty()) {
            return RedissonPromise.newSucceededFuture(null);
        }
        
        final RPromise<Void> result = new RedissonPromise<Void>();
        final AtomicInteger counter = new AtomicInteger(locks.size());
        for (RLock lock : locks) {
            lock.unlockAsync(threadId).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        result.tryFailure(future.cause());
                        return;
                    }
                    
                    if (counter.decrementAndGet() == 0) {
                        result.trySuccess(null);
                    }
                }
            });
        }
        return result;
    }


    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }
    
    protected int failedLocksLimit() {
        return 0;
    }
    
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
//        try {
//            return tryLockAsync(waitTime, leaseTime, unit).get();
//        } catch (ExecutionException e) {
//            throw new IllegalStateException(e);
//        }
        long newLeaseTime = -1;
        if (leaseTime != -1) {
            newLeaseTime = unit.toMillis(waitTime)*2;
        }
        
        long time = System.currentTimeMillis();
        long remainTime = -1;
        if (waitTime != -1) {
            remainTime = unit.toMillis(waitTime);
        }
        long lockWaitTime = calcLockWaitTime(remainTime);
        
        int failedLocksLimit = failedLocksLimit();
        List<RLock> acquiredLocks = new ArrayList<RLock>(locks.size());
        for (ListIterator<RLock> iterator = locks.listIterator(); iterator.hasNext();) {
            RLock lock = iterator.next();
            boolean lockAcquired;
            try {
                if (waitTime == -1 && leaseTime == -1) {
                    lockAcquired = lock.tryLock();
                } else {
                    long awaitTime = Math.min(lockWaitTime, remainTime);
                    lockAcquired = lock.tryLock(awaitTime, newLeaseTime, TimeUnit.MILLISECONDS);
                }
            } catch (RedisResponseTimeoutException e) {
                unlockInner(Arrays.asList(lock));
                lockAcquired = false;
            } catch (Exception e) {
                lockAcquired = false;
            }
            
            if (lockAcquired) {
                acquiredLocks.add(lock);
            } else {
                if (locks.size() - acquiredLocks.size() == failedLocksLimit()) {
                    break;
                }

                if (failedLocksLimit == 0) {
                    unlockInner(acquiredLocks);
                    if (waitTime == -1 && leaseTime == -1) {
                        return false;
                    }
                    failedLocksLimit = failedLocksLimit();
                    acquiredLocks.clear();
                    // reset iterator
                    while (iterator.hasPrevious()) {
                        iterator.previous();
                    }
                } else {
                    failedLocksLimit--;
                }
            }
            
            if (remainTime != -1) {
                remainTime -= (System.currentTimeMillis() - time);
                time = System.currentTimeMillis();
                if (remainTime <= 0) {
                    unlockInner(acquiredLocks);
                    return false;
                }
            }
        }

        if (leaseTime != -1) {
            List<RFuture<Boolean>> futures = new ArrayList<RFuture<Boolean>>(acquiredLocks.size());
            for (RLock rLock : acquiredLocks) {
                RFuture<Boolean> future = rLock.expireAsync(unit.toMillis(leaseTime), TimeUnit.MILLISECONDS);
                futures.add(future);
            }
            
            for (RFuture<Boolean> rFuture : futures) {
                rFuture.syncUninterruptibly();
            }
        }
        
        return true;
    }

    private void tryAcquireLockAsync(final ListIterator<RLock> iterator, final List<RLock> acquiredLocks, final RPromise<Boolean> result, 
            final long lockWaitTime, final long waitTime, final long leaseTime, final long newLeaseTime, 
            final AtomicLong remainTime, final AtomicLong time, final AtomicInteger failedLocksLimit, final TimeUnit unit, final long threadId) {
        if (!iterator.hasNext()) {
            checkLeaseTimeAsync(acquiredLocks, result, leaseTime, unit);
            return;
        }

        final RLock lock = iterator.next();
        RPromise<Boolean> lockAcquired = new RedissonPromise<Boolean>();
        if (waitTime == -1 && leaseTime == -1) {
            lock.tryLockAsync(threadId)
                .addListener(new TransferListener<Boolean>(lockAcquired));
        } else {
            long awaitTime = Math.min(lockWaitTime, remainTime.get());
            lock.tryLockAsync(awaitTime, newLeaseTime, TimeUnit.MILLISECONDS, threadId)
                .addListener(new TransferListener<Boolean>(lockAcquired));;
        }
        
        lockAcquired.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                boolean lockAcquired = false;
                if (future.getNow() != null) {
                    lockAcquired = future.getNow();
                }

                if (future.cause() instanceof RedisResponseTimeoutException) {
                    unlockInnerAsync(Arrays.asList(lock), threadId);
                }
                
                if (lockAcquired) {
                    acquiredLocks.add(lock);
                } else {
                    if (locks.size() - acquiredLocks.size() == failedLocksLimit()) {
                        checkLeaseTimeAsync(acquiredLocks, result, leaseTime, unit);
                        return;
                    }

                    if (failedLocksLimit.get() == 0) {
                        unlockInnerAsync(acquiredLocks, threadId).addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    result.tryFailure(future.cause());
                                    return;
                                }
                                
                                if (waitTime == -1 && leaseTime == -1) {
                                    result.trySuccess(false);
                                    return;
                                }
                                
                                failedLocksLimit.set(failedLocksLimit());
                                acquiredLocks.clear();
                                // reset iterator
                                while (iterator.hasPrevious()) {
                                    iterator.previous();
                                }
                                
                                checkRemainTimeAsync(iterator, acquiredLocks, result, 
                                        lockWaitTime, waitTime, leaseTime, newLeaseTime, 
                                        remainTime, time, failedLocksLimit, unit, threadId);
                            }
                        });
                        return;
                    } else {
                        failedLocksLimit.decrementAndGet();
                    }
                }
                
                checkRemainTimeAsync(iterator, acquiredLocks, result, 
                        lockWaitTime, waitTime, leaseTime, newLeaseTime, 
                        remainTime, time, failedLocksLimit, unit, threadId);
            }
        });
    }

    private void checkLeaseTimeAsync(List<RLock> acquiredLocks, final RPromise<Boolean> result, long leaseTime, TimeUnit unit) {
        if (leaseTime != -1) {
            final AtomicInteger counter = new AtomicInteger(locks.size());
            for (RLock rLock : acquiredLocks) {
                RFuture<Boolean> future = rLock.expireAsync(unit.toMillis(leaseTime), TimeUnit.MILLISECONDS);
                future.addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        if (counter.decrementAndGet() == 0) {
                            result.trySuccess(true);
                        }
                    }
                });
            }
            return;
        }
        
        result.trySuccess(true);
    }
    
    protected void checkRemainTimeAsync(ListIterator<RLock> iterator, List<RLock> acquiredLocks, final RPromise<Boolean> result, 
            long lockWaitTime, long waitTime, long leaseTime, long newLeaseTime, 
            AtomicLong remainTime, AtomicLong time, AtomicInteger failedLocksLimit, TimeUnit unit, long threadId) {
        if (remainTime.get() != -1) {
            remainTime.addAndGet(-(System.currentTimeMillis() - time.get()));
            time.set(System.currentTimeMillis());;
            if (remainTime.get() <= 0) {
                unlockInnerAsync(acquiredLocks, threadId).addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        result.trySuccess(false);
                    }
                });
                return;
            }
        }
        
        tryAcquireLockAsync(iterator, acquiredLocks, result, lockWaitTime, waitTime, 
                leaseTime, newLeaseTime, remainTime, time, failedLocksLimit, unit, threadId);
    }
    
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        long newLeaseTime = -1;
        if (leaseTime != -1) {
            newLeaseTime = unit.toMillis(waitTime)*2;
        }
        
        AtomicLong time = new AtomicLong(System.currentTimeMillis());
        AtomicLong remainTime = new AtomicLong(-1);
        if (waitTime != -1) {
            remainTime.set(unit.toMillis(waitTime));
        }
        long lockWaitTime = calcLockWaitTime(remainTime.get());
        
        AtomicInteger failedLocksLimit = new AtomicInteger(failedLocksLimit());
        List<RLock> acquiredLocks = new ArrayList<RLock>(locks.size());
        tryAcquireLockAsync(locks.listIterator(), acquiredLocks, result, 
                lockWaitTime, waitTime, leaseTime, newLeaseTime, 
                remainTime, time, failedLocksLimit, unit, threadId);
        
        return result;
    }
    
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        return tryLockAsync(waitTime, leaseTime, unit, Thread.currentThread().getId());
    }

    
    protected long calcLockWaitTime(long remainTime) {
        return remainTime;
    }

    public RFuture<Void> unlockAsync(long threadId) {
        return unlockInnerAsync(locks, threadId);
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

}
