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
package org.redisson.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import io.netty.util.internal.ThreadLocalRandom;

import org.redisson.RedissonLock;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

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
        long waitTime = -1;
        if (leaseTime == -1) {
            waitTime = 5;
            unit = TimeUnit.SECONDS;
        } else {
            waitTime = unit.toMillis(leaseTime);
            if (waitTime <= 2000) {
                waitTime = 2000;
            } else if (waitTime <= 5000) {
                waitTime = ThreadLocalRandom.current().nextLong(waitTime/2, waitTime);
            } else {
                waitTime = ThreadLocalRandom.current().nextLong(5000, waitTime);
            }
            waitTime = unit.convert(waitTime, TimeUnit.MILLISECONDS);
        }
        while (true) {
            if (tryLock(waitTime, leaseTime, unit)) {
                return;
            }
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
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
    
    protected int failedLocksLimit() {
        return 0;
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long newLeaseTime = -1;
        if (leaseTime != -1) {
            newLeaseTime = waitTime*2;
        }
        
        long time = System.currentTimeMillis();
        long remainTime = -1;
        if (waitTime != -1) {
            remainTime = unit.toMillis(waitTime);
        }
        int failedLocksLimit = failedLocksLimit();
        List<RLock> lockedLocks = new ArrayList<RLock>(locks.size());
        for (ListIterator<RLock> iterator = locks.listIterator(); iterator.hasNext();) {
            RLock lock = iterator.next();
            boolean lockAcquired;
            try {
                if (waitTime == -1 && leaseTime == -1) {
                    lockAcquired = lock.tryLock();
                } else {
                    lockAcquired = lock.tryLock(unit.convert(remainTime, TimeUnit.MILLISECONDS), newLeaseTime, unit);
                }
            } catch (Exception e) {
               lockAcquired = false;
            }
            
            if (lockAcquired) {
                lockedLocks.add(lock);
            } else {
                if (locks.size() - lockedLocks.size() == failedLocksLimit()) {
                    break;
                }
                if (failedLocksLimit == 0) {
                    unlockInner(lockedLocks);
                    if (waitTime == -1 && leaseTime == -1) {
                        return false;
                    }
                    failedLocksLimit = failedLocksLimit();
                    lockedLocks.clear();
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
                    unlockInner(lockedLocks);
                    return false;
                }
            }
        }

        if (leaseTime != -1) {
            List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>(lockedLocks.size());
            for (RLock rLock : lockedLocks) {
                Future<Boolean> future = rLock.expireAsync(unit.toMillis(leaseTime), TimeUnit.MILLISECONDS);
                futures.add(future);
            }
            
            for (Future<Boolean> rFuture : futures) {
                rFuture.syncUninterruptibly();
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

    protected boolean isLockFailed(Future<Boolean> future) {
        return !future.isSuccess();
    }

    protected boolean isAllLocksAcquired(AtomicReference<RLock> lockedLockHolder, AtomicReference<Throwable> failed, Queue<RLock> lockedLocks) {
        return lockedLockHolder.get() == null && failed.get() == null;
    }

}
