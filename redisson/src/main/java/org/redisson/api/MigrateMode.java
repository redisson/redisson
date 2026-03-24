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


/**
 * migrate mode
 *
 * @author lyrric
 */
public enum MigrateMode {

    /**
     * Default migrate
     */
    MIGRATE,
    /**
     * Do not remove the key from the local instance.
     */
    COPY,

    /**
     * Replace existing key on the remote instance.
     */
    REPLACE,

    /**
     * Do not remove the key from the local instance and replace existing key on the remote instance.
     */
    COPY_AND_REPLACE;

}
