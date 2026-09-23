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

import org.redisson.config.ReadMode;

/**
 * Options which allow per-object override of the {@link ReadMode}
 * defined globally on the Redisson configuration.
 *
 * @author Nikita Koksharov
 *
 * @param <T> returned options object type
 */
public interface ReadModeOptions<T extends InvocationOptions<T>> extends InvocationOptions<T> {

    /**
     * Defines the {@link ReadMode} used for read operations on this object.
     * <p>
     * Overrides the {@code readMode} setting declared globally in the
     * Redisson configuration for this object instance only.
     * <p>
     * When set to {@code null} (default) the globally configured
     * {@code readMode} is used.
     * <p>
     * <b>Note:</b> the effect of this override depends on the slave pool
     * being initialized, which is governed by the globally configured
     * {@code readMode} and {@code subscriptionMode}. The slave pool is
     * not initialized when both global settings are {@code MASTER}, so an
     * override of any other read mode has no effect in
     * that configuration. Furthermore, when the slave pool is initialized,
     * the master node is included in it only when the global {@code readMode}
     * is {@code MASTER_SLAVE}, {@code AZ_AFFINITY_SLAVES_AND_MASTER} or
     * {@code AZ_AFFINITY_MASTER_SLAVE}. As a result:
     * <ul>
     * <li>Override {@code MASTER}: always honored unconditionally.</li>
     * <li>Override {@code SLAVE}: requires the global setting to be
     *     other than {@code MASTER}. When global is {@code MASTER_SLAVE},
     *     {@code AZ_AFFINITY_SLAVES_AND_MASTER} or {@code AZ_AFFINITY_MASTER_SLAVE},
     *     reads may still land on the master because master is part of the slave pool.</li>
     * <li>Override {@code MASTER_SLAVE}: requires the global setting to be
     *     {@code MASTER_SLAVE}, {@code AZ_AFFINITY_SLAVES_AND_MASTER} or
     *     {@code AZ_AFFINITY_MASTER_SLAVE}; when global is {@code SLAVE} or
     *     {@code AZ_AFFINITY}, behaves like {@code SLAVE} because master is not in the slave pool.</li>
     * <li>Override {@code AZ_AFFINITY}: requires the global setting to be
     *     other than {@code MASTER}, and {@code clientAvailabilityZone} to be set.
     *     Reads land on the master only when no slave is available, whatever the global setting.</li>
     * <li>Override {@code AZ_AFFINITY_SLAVES_AND_MASTER} or {@code AZ_AFFINITY_MASTER_SLAVE}:
     *     same requirements as {@code AZ_AFFINITY}; when global is {@code SLAVE} or
     *     {@code AZ_AFFINITY}, behaves like {@code AZ_AFFINITY} because master is not in the slave pool.</li>
     * </ul>
     *
     * @param readMode read mode applied to this object instance
     * @return options instance
     */
    T readMode(ReadMode readMode);

}
