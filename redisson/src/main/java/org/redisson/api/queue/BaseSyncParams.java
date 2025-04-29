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
package org.redisson.api.queue;

import java.time.Duration;

class BaseSyncParams<T> implements QueueSyncArgs<T> {

    private SyncMode syncMode = SyncMode.AUTO;
    private SyncFailureMode syncFailureMode = SyncFailureMode.LOG_WARNING;
    private long syncTimeout = 1000;

    @Override
    public T syncMode(SyncMode syncMode) {
        this.syncMode = syncMode;
        return (T) this;
    }

    @Override
    public T syncFailureMode(SyncFailureMode syncFailureMode) {
        this.syncFailureMode = syncFailureMode;
        return (T) this;
    }

    @Override
    public T syncTimeout(Duration timeout) {
        this.syncTimeout = timeout.toMillis();
        return (T) this;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public SyncFailureMode getSyncFailureMode() {
        return syncFailureMode;
    }

    public long getSyncTimeout() {
        return syncTimeout;
    }
}
