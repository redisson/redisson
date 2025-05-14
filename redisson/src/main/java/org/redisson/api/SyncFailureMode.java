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
package org.redisson.api;

/**
 * Defines the behavior modes when synchronization with Valkey or Redis secondary nodes
 * fails.
 *
 * @author Nikita Koksharov
 *
 */
public enum SyncFailureMode {

    /**
     * When synchronization fails, throw an exception to the caller.
     * <p>
     * This mode is useful in scenarios where synchronization failures should
     * be immediately visible and handled by the application code.
     * </p>
     */
    THROW_EXCEPTION,

    /**
     * When synchronization fails, log a warning message but continue execution.
     * <p>
     * This mode is suitable for non-critical synchronization operations where
     * the application can continue functioning despite synchronization issues.
     * </p>
     */
    LOG_WARNING

}
