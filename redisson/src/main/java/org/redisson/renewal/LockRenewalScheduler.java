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

import org.redisson.command.CommandAsyncExecutor;

import java.util.Collection;
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
    private final CommandAsyncExecutor executor;

    private final int batchSize;
    private final long internalLockLeaseTime;

    public LockRenewalScheduler(CommandAsyncExecutor executor) {
        this.executor = executor;
        this.internalLockLeaseTime = executor.getServiceManager().getCfg().getLockWatchdogTimeout();
        this.batchSize = executor.getServiceManager().getCfg().getLockWatchdogBatchSize();
    }

    public void renewReadLock(String name, Long threadId, String lockName, String keyPrefix) {
        readLockReference.compareAndSet(null, new ReadLockTask(internalLockLeaseTime, executor, batchSize));
        ReadLockTask task = readLockReference.get();
        task.add(name, lockName, threadId, keyPrefix);
    }

    public void renewFastMultiLock(String name, Long threadId, String lockName, Collection<String> fields) {
        multilockReference.compareAndSet(null, new FastMultilockTask(internalLockLeaseTime, executor));
        FastMultilockTask task = multilockReference.get();
        task.add(name, lockName, threadId, fields);
    }

    public void renewLock(String name, Long threadId, String lockName) {
        reference.compareAndSet(null, new LockTask(internalLockLeaseTime, executor, batchSize));
        LockTask task = reference.get();
        task.add(name, lockName, threadId);
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

}
