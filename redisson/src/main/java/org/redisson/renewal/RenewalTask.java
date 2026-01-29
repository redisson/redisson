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
package org.redisson.renewal;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.AsyncIteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
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
        LockEntry task = name2entry.get(name);
        if (task == null) {
            return;
        }

        if (threadId != null) {
            task.removeThreadId(threadId);
        }

        if (threadId == null || task.hasNoThreads()) {
            name2entry.remove(name);

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
        addSlotName(rawName);

        LockEntry oldEntry = name2entry.putIfAbsent(rawName, entry);
        if (oldEntry != null) {
            oldEntry.addThreadId(threadId, lockName);
        } else {
            if (tryRun()) {
                schedule();
            }
        }
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
                log.error("Can't update locks {} expiration", name2entry.keySet(), e);
                schedule();
                return;
            }

            schedule();
        });
    }

}
