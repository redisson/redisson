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

import org.redisson.api.IdGenerator;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 * {@link org.redisson.api.RExecutorService} instance options
 *
 * @author Nikita Koksharov
 *
 */
public interface ExecutorOptions extends CodecOptions<ExecutorOptions, Codec> {

    /**
     * Creates options with the name of object instance
     *
     * @param name of object instance
     * @return options instance
     */
    static ExecutorOptions name(String name) {
        return new ExecutorParams(name);
    }

    /**
     * Defines task retry interval at the end of which task
     * is executed again by ExecutorService worker.
     * <p>
     * Counted from the task start moment.
     * Applied only if the task was in progress but for some reason
     * wasn't marked as completed (successful or unsuccessful).
     * <p>
     * Set <code>0</code> to disable.
     * <p>
     * Default is <code>5 minutes</code>
     *
     * @param interval value
     * @return options instance
     */
    ExecutorOptions taskRetryInterval(Duration interval);

    /**
     * Defines identifier generator
     *
     * @param idGenerator identifier generator
     * @return options instance
     */
    ExecutorOptions idGenerator(IdGenerator idGenerator);

}
