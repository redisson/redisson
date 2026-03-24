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
package org.redisson.api;

import java.time.Duration;

/**
 * Interface defining parameters for queue synchronization.
 * These parameters control how queue operations are synchronized with secondary Valkey or Redis nodes.
 *
 * @param <T> type
 * @author Nikita Koksharov
 */
public interface SyncArgs<T> {

    /**
     * Sets the synchronization mode to be used for current operation.
     * <p>
     * Default value is SyncMode.AUTO
     *
     * @param syncMode the synchronization mode
     * @return arguments object
     * @see SyncMode
     */
    T syncMode(SyncMode syncMode);

    /**
     * Sets the behavior when synchronization with secondary nodes fails.
     * <p>
     * Default value is SyncFailureMode.LOG_WARNING
     *
     * @param syncFailureMode the failure handling mode
     * @return The current instance for method chaining
     * @see SyncFailureMode
     */
    T syncFailureMode(SyncFailureMode syncFailureMode);

    /**
     * Sets the timeout duration for synchronization of the current operation.
     * Defines how long the system will wait for acknowledgment from secondary nodes.
     * <p>
     * Default value is 1 second.
     *
     * @param timeout The maximum time to wait for synchronization to complete
     * @return arguments object
     */
    T syncTimeout(Duration timeout);

}
