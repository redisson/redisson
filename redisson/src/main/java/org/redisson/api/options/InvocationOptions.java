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
package org.redisson.api.options;

import org.redisson.config.DelayStrategy;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface InvocationOptions<T extends InvocationOptions<T>> {

    /**
     * Defines Redis server response timeout. Starts to countdown
     * when a Redis command was successfully sent.
     * <p>
     * Default is the value specified for the same parameter in Redisson configuration
     *
     * @param timeout Redis server response timeout
     * @return options instance
     */
    T timeout(Duration timeout);

    /**
     * Defines command retry attempts. Error is thrown if
     * the Redis command can't be sent to Redis server after <code>retryAttempts</code>.
     * But if it sent successfully then <code>responseTimeout</code> is started.
     * <p>
     * Default is the value specified for the same parameter in Redisson configuration
     *
     * @param retryAttempts command retry attempts
     * @return options instance
     */
    T retryAttempts(int retryAttempts);

    /**
     * Use {@link #retryDelay(DelayStrategy)} instead.
     *
     * @param interval retry time interval
     * @return options instance
     */
    @Deprecated
    T retryInterval(Duration interval);

    /**
     * Defines the delay strategy for a new attempt to send a command.
     *
     * @param delayStrategy delay strategy implementation
     * @return options instance
     */
    T retryDelay(DelayStrategy delayStrategy);

}
