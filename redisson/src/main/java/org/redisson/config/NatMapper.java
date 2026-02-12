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
package org.redisson.config;

import org.redisson.misc.RedisURI;

/**
 * Maps RedisURI object. Allows to change input RedisURI's port and host values.
 *
 * @author Nikita Koksharov
 *
 * @see HostNatMapper
 * @see HostPortNatMapper
 */
@FunctionalInterface
public interface NatMapper {

    /**
     * Applies map function to input <code>uri</code> object
     *
     * @param uri - RedisURI object
     * @return mapped RedisURI object
     */
    RedisURI map(RedisURI uri);

    /**
     * Returns input RedisURI object. Used by default
     *
     * @return NatMapper instance what returns input RedisURI object
     */
    static NatMapper direct() {
        return new DefaultNatMapper();
    }

}
