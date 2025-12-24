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
package org.redisson.api.keys;

/**
 * MigrateArgs
 *
 * @author lyrric
 */
public interface MigrateArgs {

    /**
     * Defines keys to transfer
     * Redis version >= 3.0.6
     *
     * @param keys keys to migrate，not empty
     * @return migrate conditions object
     */
    static HostMigrateArgs keys(String... keys){
        return new MigrateParams(keys);
    }
}
