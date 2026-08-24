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

import org.redisson.api.listener.LockRenewalFailureListener;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class LockRenewalScheduler {

    private final AtomicReference<LockTask> reference = new AtomicReference<>();
    private final AtomicReference<FastMultilockTask> multilockReference = new AtomicReference<>();
    private final AtomicReference<ReadLockTask> readLockReference = new AtomicReference<>();
    private final Map<String, Map<Integer, LockRenewalFailureListener>> name2failureListeners = new ConcurrentHashMap<>();
    private final CommandAsyncExecutor executor;

    private final int batchSize;
    private final long internalLockLeaseTime;

    public LockRenewalScheduler(CommandAsyncExecutor executor) {
        this.executor = executor;
        this.internalLockLeaseTime = executor.getServiceManager().getCfg().getLockWatchdogTimeout();
        this.batchSize = executor.getServiceManager().getCfg().getLockWatchdogBatchSize();
    }

    public void renewReadLock(String name, Long threadId, String lockName, String threadName, String keyPrefix) {
        readLockReference.compareAndSet(null, new ReadLockTask(internalLockLeaseTime, executor, batchSize));
        ReadLockTask task = readLockReference.get();
        task.add(name, lockName, threadId, threadName, keyPrefix);
    }

    public void renewFastMultiLock(String name, Long threadId, String lockName, String threadName, Collection<String> fields) {
        multilockReference.compareAndSet(null, new FastMultilockTask(internalLockLeaseTime, executor));
        FastMultilockTask task = multilockReference.get();
        task.add(name, lockName, threadId, threadName, fields);
    }

    public void renewLock(String name, Long threadId, String lockName, String threadName) {
        reference.compareAndSet(null, new LockTask(internalLockLeaseTime, executor, batchSize));
        LockTask task = reference.get();
        task.add(name, lockName, threadId, threadName);
    }

    public void cancelReadLockRenewal(String name, Long threadId) {
        ReadLockTask rtask = readLockReference.get();
        if (rtask != null) {
            rtask.cancelExpirationRenewal(name, threadId);
        }
    }

    public void cancelFastMultilockRenewl(String name, Long threadId) {
        FastMultilockTask mtask = multilockReference.get();
        if (mtask != null) {
            mtask.cancelExpirationRenewal(name, threadId);
        }
    }

    public void cancelLockRenewal(String name, Long threadId) {
        LockTask task = reference.get();
        if (task != null) {
            task.cancelExpirationRenewal(name, threadId);
        }
    }

    /**
     * Registers a listener notified when watchdog renewal of the lock fails.
     * Renewal state is shared per lock name, so the listener is registered against
     * the name instead of the lock object which added it.
     *
     * @param name lock name
     * @param listener renewal failure listener
     * @return listener id
     */
    public int addFailureListener(String name, LockRenewalFailureListener listener) {
        int listenerId = System.identityHashCode(listener);
        name2failureListeners.computeIfAbsent(name, k -> new ConcurrentHashMap<>())
                             .put(listenerId, listener);
        return listenerId;
    }

    /**
     * Removes a previously registered renewal failure listener.
     *
     * @param name lock name
     * @param listenerId listener id
     * @return <code>true</code> if the listener was registered for this lock name
     */
    public boolean removeFailureListener(String name, int listenerId) {
        AtomicBoolean removed = new AtomicBoolean();
        name2failureListeners.computeIfPresent(name, (k, listeners) -> {
            removed.set(listeners.remove(listenerId) != null);
            if (listeners.isEmpty()) {
                return null;
            }
            return listeners;
        });
        return removed.get();
    }

    Collection<LockRenewalFailureListener> getFailureListeners(String name) {
        return name2failureListeners.getOrDefault(name, Collections.emptyMap()).values();
    }

}
