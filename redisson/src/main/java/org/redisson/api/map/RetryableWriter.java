/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api.map;

public abstract class RetryableWriter {
    private static final int DEFAULT_RETRY_ATTEMPTS = 0;
    private static final long DEFAULT_RETRY_INTERVAL = 100;

    //max retry times
    private final int retryAttempts;
    private final long retryInterval;


    public RetryableWriter() {
        this(DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_INTERVAL);
    }

    public RetryableWriter(int retryAttempts) {
        this(retryAttempts, DEFAULT_RETRY_INTERVAL);
    }

    //todo add TimeUnit param
    public RetryableWriter(int retryAttempts, long retryInterval) {
        if (retryInterval < 0 || retryAttempts < 0) {
            throw new IllegalArgumentException("retryAttempts and retryInterval must be positive");
        }

        this.retryAttempts = retryAttempts;
        this.retryInterval = retryInterval;
    }

    public abstract Object getNoRetriesForWrite();

    public abstract Object getNoRetriesForDelete();

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

}
