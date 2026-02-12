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

import org.redisson.api.ObjectListener;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RLockAsync;
import org.redisson.client.RedisResponseTimeoutException;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

/**
 * Groups multiple independent locks and manages them as one lock.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonMultiLock implements RLock {

    class LockState {
        
        private final long newLeaseTime;
        private final long lockWaitTime;
        private final List<RLock> acquiredLocks;
        private final long waitTime;
        private final long threadId;
        private final long leaseTime;
        private final TimeUnit unit;

        private long remainTime;
        private long time = System.currentTimeMillis();
        private int failedLocksLimit;
        
        LockState(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
            this.waitTime = waitTime;
            this.leaseTime = leaseTime;
            this.unit = unit;
            this.threadId = threadId;

            if (leaseTime > 0) {
                if (waitTime > 0) {
                    newLeaseTime = unit.toMillis(waitTime)*2;
                } else {
                    newLeaseTime = unit.toMillis(leaseTime);
                }
            } else {
                newLeaseTime = -1;
            }

            remainTime = -1;
            if (waitTime > 0) {
                remainTime = unit.toMillis(waitTime);
            }
            lockWaitTime = calcLockWaitTime(remainTime);
            
            failedLocksLimit = failedLocksLimit();
            acquiredLocks = new ArrayList<>(locks.size());
        }
        
        CompletionStage<Boolean> tryAcquireLockAsync(ListIterator<RLock> iterator) {
            if (!iterator.hasNext()) {
                return checkLeaseTimeAsync();
            }

            RLock lock = iterator.next();
            RFuture<Boolean> lockAcquiredFuture;
            if (waitTime <= 0 && leaseTime <= 0) {
                lockAcquiredFuture = lock.tryLockAsync(threadId);
            } else {
                long awaitTime = Math.min(lockWaitTime, remainTime);
                lockAcquiredFuture = lock.tryLockAsync(awaitTime, newLeaseTime, TimeUnit.MILLISECONDS, threadId);
            }

            return lockAcquiredFuture
                    .exceptionally(e -> null)
                    .thenCompose(res -> {
                boolean lockAcquired = false;
                if (res != null) {
                    lockAcquired = res;
                } else {
                    unlockInnerAsync(Arrays.asList(lock), threadId);
                }

                if (lockAcquired) {
                    acquiredLocks.add(lock);
                } else {
                    if (locks.size() - acquiredLocks.size() == failedLocksLimit()) {
                        return checkLeaseTimeAsync();
                    }

                    if (failedLocksLimit == 0) {
                        return unlockInnerAsync(acquiredLocks, threadId).thenCompose(r -> {
                            if (waitTime <= 0) {
                                return CompletableFuture.completedFuture(false);
                            }
                            
                            failedLocksLimit = failedLocksLimit();
                            acquiredLocks.clear();
                            // reset iterator
                            while (iterator.hasPrevious()) {
                                iterator.previous();
                            }
                            
                            return checkRemainTimeAsync(iterator);
                        });
                    } else {
                        failedLocksLimit--;
                    }
                }
                
                return checkRemainTimeAsync(iterator);
            });
        }
        
        private CompletableFuture<Boolean> checkLeaseTimeAsync() {
            if (leaseTime > 0) {
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (RLock rLock : acquiredLocks) {
                    RFuture<Boolean> future = ((RedissonBaseLock) rLock).expireAsync(unit.toMillis(leaseTime), TimeUnit.MILLISECONDS);
                    futures.add(future.toCompletableFuture());
                }
                CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                return f.thenApply(r -> true);
            }

            return CompletableFuture.completedFuture(true);
        }
        
        private CompletionStage<Boolean> checkRemainTimeAsync(ListIterator<RLock> iterator) {
            if (remainTime > 0) {
                remainTime += -(System.currentTimeMillis() - time);
                time = System.currentTimeMillis();
                if (remainTime <= 0) {
                    return unlockInnerAsync(acquiredLocks, threadId).thenApply(res -> false);
                }
            }
            
            return tryAcquireLockAsync(iterator);
        }
        
    }
    
    final List<RLock> locks = new ArrayList<>();
    
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

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit) {
        return lockAsync(leaseTime, unit, Thread.currentThread().getId());
    }
    
    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long threadId) {
        long baseWaitTime = locks.size() * 1500;
        long waitTime;
        if (leaseTime <= 0) {
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

        CompletionStage<Void> f = tryLockAsyncCycle(threadId, leaseTime, TimeUnit.MILLISECONDS, waitTime);
        return new CompletableFutureWrapper<>(f);
    }
    
    protected CompletionStage<Void> tryLockAsyncCycle(long threadId, long leaseTime, TimeUnit unit, long waitTime) {
        return tryLockAsync(waitTime, leaseTime, unit, threadId).thenCompose(res -> {
            if (res) {
                return CompletableFuture.completedFuture(null);
            } else {
                return tryLockAsyncCycle(threadId, leaseTime, unit, waitTime);
            }
        });
    }


    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long baseWaitTime = locks.size() * 1500;
        while (true) {
            long waitTime;
            if (leaseTime <= 0) {
                waitTime = baseWaitTime;
            } else {
                waitTime = unit.toMillis(leaseTime);
                if (waitTime <= baseWaitTime) {
                    waitTime = ThreadLocalRandom.current().nextLong(waitTime/2, waitTime);
                } else {
                    waitTime = ThreadLocalRandom.current().nextLong(baseWaitTime, waitTime);
                }
            }

            if (leaseTime > 0) {
                leaseTime = unit.toMillis(leaseTime);
            }

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
        locks.stream()
                .map(RLockAsync::unlockAsync)
                .forEach(f -> {
                    try {
                        f.toCompletableFuture().join();
                    } catch (Exception e) {
                        // skip
                    }
                });
    }
    
    protected RFuture<Void> unlockInnerAsync(Collection<RLock> locks, long threadId) {
        CompletableFuture[] s = locks.stream().map(l -> l.unlockAsync(threadId).toCompletableFuture())
                                                .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> future = CompletableFuture.allOf(s);
        return new CompletableFutureWrapper<>(future);
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }
    
    protected int failedLocksLimit() {
        return 0;
    }
    
    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
//        try {
//            return tryLockAsync(waitTime, leaseTime, unit).get();
//        } catch (ExecutionException e) {
//            throw new IllegalStateException(e);
//        }
        long newLeaseTime = -1;
        if (leaseTime > 0) {
            if (waitTime > 0) {
                newLeaseTime = unit.toMillis(waitTime)*2;
            } else {
                newLeaseTime = unit.toMillis(leaseTime);
            }
        }
        
        long time = System.currentTimeMillis();
        long remainTime = -1;
        if (waitTime > 0) {
            remainTime = unit.toMillis(waitTime);
        }
        long lockWaitTime = calcLockWaitTime(remainTime);
        
        int failedLocksLimit = failedLocksLimit();
        List<RLock> acquiredLocks = new ArrayList<>(locks.size());
        for (ListIterator<RLock> iterator = locks.listIterator(); iterator.hasNext();) {
            RLock lock = iterator.next();
            boolean lockAcquired;
            try {
                if (waitTime <= 0 && leaseTime <= 0) {
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
                    if (waitTime <= 0) {
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
            
            if (remainTime > 0) {
                remainTime -= System.currentTimeMillis() - time;
                time = System.currentTimeMillis();
                if (remainTime <= 0) {
                    unlockInner(acquiredLocks);
                    return false;
                }
            }
        }

        if (leaseTime > 0) {
            acquiredLocks.stream()
                    .map(l -> (RedissonBaseLock) l)
                    .map(l -> l.expireAsync(unit.toMillis(leaseTime), TimeUnit.MILLISECONDS))
                    .forEach(f -> f.toCompletableFuture().join());
        }
        
        return true;
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
        LockState state = new LockState(waitTime, leaseTime, unit, threadId);
        CompletionStage<Boolean> f = state.tryAcquireLockAsync(locks.listIterator());
        return new CompletableFutureWrapper<>(f);
    }
    
    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        return tryLockAsync(waitTime, leaseTime, unit, Thread.currentThread().getId());
    }

    
    protected long calcLockWaitTime(long remainTime) {
        return remainTime;
    }

    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        return unlockInnerAsync(locks, threadId);
    }
    
    @Override
    public void unlock() {
        locks.forEach(Lock::unlock);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Void> unlockAsync() {
        return unlockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Boolean> tryLockAsync() {
        return tryLockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Void> lockAsync() {
        return lockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Void> lockAsync(long threadId) {
        return lockAsync(-1, null, threadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long threadId) {
        return tryLockAsync(-1, -1, null, threadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit) {
        return tryLockAsync(waitTime, -1, unit);
    }

    @Override
    public RFuture<Integer> getHoldCountAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean forceUnlock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public RFuture<Boolean> isLockedAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHeldByThread(long threadId) {
        return locks.stream().map(l -> l.isHeldByThread(threadId)).reduce(true, (r, u) -> r && u);
    }

    @Override
    public RFuture<Boolean> isHeldByThreadAsync(long threadId) {
        CompletableFuture<Boolean>[] s = locks.stream().map(l -> l.isHeldByThreadAsync(threadId).toCompletableFuture())
                                                        .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> future = CompletableFuture.allOf(s);
        CompletableFuture<Boolean> f = future.thenApply(v -> Stream.of(s).map(r2 -> r2.getNow(false))
                                                            .reduce(true, (r, u) -> r && u));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return locks.stream().map(l -> l.isHeldByCurrentThread())
                                .reduce(true, (r, u) -> r && u);
    }

    @Override
    public int getHoldCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long remainTimeToLive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int addListener(ObjectListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeListener(int listenerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        throw new UnsupportedOperationException();
    }
}
