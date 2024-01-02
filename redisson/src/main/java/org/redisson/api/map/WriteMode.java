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
package org.redisson.api.map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public enum WriteMode {

    /**
     * In write behind mode all data written in map object
     * also written using MapWriter in asynchronous mode.
     */
    WRITE_BEHIND,

    /**
     * In write through mode all write operations for map object
     * are synchronized with MapWriter write operations.
     * If MapWriter throws an error then it will be re-thrown to Map operation caller.
     */
    WRITE_THROUGH

}
