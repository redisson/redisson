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
package org.redisson.renewal;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.LockRenewalFailureListener;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.AsyncIteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class RenewalTask implements TimerTask {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    final CommandAsyncExecutor executor;

    AtomicBoolean running = new AtomicBoolean();

    final Map<Integer, Set<String>> slot2names = new ConcurrentHashMap<>();
    final Map<String, LockEntry> name2entry = new ConcurrentHashMap<>();

    final long internalLockLeaseTime;
    final int chunkSize;

    boolean tryRun() {
        return running.compareAndSet(false, true);
    }

    void stop() {
        running.set(false);
    }

    public void schedule() {
        if (!running.get()) {
            return;
        }

        long internalLockLeaseTime = executor.getServiceManager().getCfg().getLockWatchdogTimeout();
        executor.getServiceManager().newTimeout(this, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);
    }

    RenewalTask(long internalLockLeaseTime,
                    CommandAsyncExecutor executor, int chunkSize) {
        this.executor = executor;
        this.internalLockLeaseTime = internalLockLeaseTime;
        this.chunkSize = chunkSize;
    }

    final CompletionStage<Void> execute() {
        if (name2entry.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (!executor.getServiceManager().isClusterSetup()) {
            return renew(name2entry.keySet().iterator(), chunkSize);
        }

        return renewSlots(slot2names.values().iterator(), chunkSize);
    }

    private CompletionStage<Void> renewSlots(Iterator<Set<String>> iter, int chunkSize) {
        return AsyncIteratorUtils.forEachAsync(iter,
                names -> renew(names.iterator(), chunkSize));
    }

    abstract CompletionStage<Void> renew(Iterator<String> iter, int chunkSize);

    void cancelExpirationRenewal(String name, Long threadId) {
        LockEntry newTask = name2entry.compute(name, (unused, task) -> {
            if (task == null) {
                return null;
            }

            if (threadId != null) {
                task.removeThreadId(threadId);
            }

            if (threadId == null || task.hasNoThreads()) {
                if (executor.getServiceManager().isClusterSetup()) {
                    int slot = executor.getConnectionManager().calcSlot(name);
                    slot2names.computeIfPresent(slot, (k, v) -> {
                        v.remove(name);
                        if (v.isEmpty()) {
                            return null;
                        }
                        return v;
                    });
                }
                return null;
            }
            return task;
        });

        if (newTask == null) {
            if (!name2entry.isEmpty()) {
                return;
            }

            stop();

            if (!name2entry.isEmpty() && tryRun()) {
                schedule();
            }
        }
    }

    final void add(String rawName, String lockName, long threadId, LockEntry entry) {
        name2entry.compute(rawName, (k, oldEntry) -> {
            addSlotName(rawName);

            LockEntry returnEntry = entry;
            if (oldEntry != null) {
                oldEntry.addThreadId(threadId, lockName);
                returnEntry = oldEntry;
            } else {
                if (tryRun()) {
                    schedule();
                }
            }
            return returnEntry;
        });

    }

    void addSlotName(String rawName) {
        if (!executor.getServiceManager().isClusterSetup()) {
            return;
        }

        int slot = executor.getConnectionManager().calcSlot(rawName);
        slot2names.compute(slot, (k, v) -> {
            if (v == null) {
                v = Collections.newSetFromMap(new ConcurrentHashMap<>());
            }
            v.add(rawName);
            return v;
        });
    }
    
    @Override
    public void run(Timeout timeout) {
        if (executor.getServiceManager().isShuttingDown()) {
            return;
        }

        CompletionStage<Void> future = execute();
        future.whenComplete((result, e) -> {
            if (e != null) {
                RenewalFailureException failure = getRenewalFailure(e);
                if (failure == null) {
                    log.error("Can't update locks {} expiration", name2entry.keySet(), e);
                    schedule();
                    return;
                }

                log.error("Can't update locks {} expiration", failure.getFailedLocks().keySet(), failure.getCause());
                schedule();
                notifyListener(failure.getFailedLocks(), failure.getCause());
                return;
            }

            schedule();
        });
    }

    final <T> CompletionStage<T> trackFailure(CompletionStage<T> future,
                                               Map<String, Set<Long>> failedLocks) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Map<String, Set<Long>> failedLocksSnapshot = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : failedLocks.entrySet()) {
            failedLocksSnapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        future.whenComplete((value, cause) -> {
            if (cause != null) {
                result.completeExceptionally(new RenewalFailureException(failedLocksSnapshot, unwrap(cause)));
                return;
            }
            result.complete(value);
        });
        return result;
    }

    private RenewalFailureException getRenewalFailure(Throwable cause) {
        Throwable unwrappedCause = unwrap(cause);
        if (unwrappedCause instanceof RenewalFailureException) {
            return (RenewalFailureException) unwrappedCause;
        }
        return null;
    }

    private Throwable unwrap(Throwable cause) {
        if (cause instanceof CompletionException && cause.getCause() != null) {
            return cause.getCause();
        }
        return cause;
    }

    private void notifyListener(Map<String, Set<Long>> failedLocks, Throwable cause) {
        LockRenewalFailureListener listener = executor.getServiceManager().getCfg().getLockRenewalFailureListener();
        if (listener == null || failedLocks.isEmpty()) {
            return;
        }

        try {
            executor.getServiceManager().getExecutor().execute(() -> invokeListener(listener, failedLocks, cause));
        } catch (RejectedExecutionException e) {
            log.error("Can't notify lock renewal failure listener", e);
        }
    }

    private void invokeListener(LockRenewalFailureListener listener,
                                Map<String, Set<Long>> failedLocks, Throwable cause) {
        for (Map.Entry<String, Set<Long>> entry : failedLocks.entrySet()) {
            String lockName;
            try {
                lockName = executor.getServiceManager().getNameMapper().unmap(entry.getKey());
            } catch (Exception e) {
                log.error("Can't map lock name for lock renewal failure listener", e);
                continue;
            }

            Set<Long> threadIds = entry.getValue();
            for (Long threadId : threadIds) {
                try {
                    listener.onFailure(lockName, threadId, cause);
                } catch (Exception e) {
                    log.error("Can't notify lock renewal failure listener", e);
                }
            }
        }
    }

    private static final class RenewalFailureException extends Exception {

        private final Map<String, Set<Long>> failedLocks;

        private RenewalFailureException(Map<String, Set<Long>> failedLocks, Throwable cause) {
            super(cause);
            this.failedLocks = failedLocks;
        }

        private Map<String, Set<Long>> getFailedLocks() {
            return failedLocks;
        }
    }

}
