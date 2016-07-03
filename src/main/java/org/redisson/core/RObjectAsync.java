/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.core;

import io.netty.util.concurrent.Future;

/**
 * Base interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObjectAsync {

    /**
     * Transfer an object from source Redis instance to destination Redis instance
     * in async mode
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @return
     */
    Future<Void> migrateAsync(String host, int port, int database);

    /**
     * Move object to another database in async mode
     *
     * @param database
     * @return <code>true</code> if key was moved <code>false</code> if not
     */
    Future<Boolean> moveAsync(int database);

    /**
     * Delete object in async mode
     *
     * @return <code>true</code> if object was deleted <code>false</code> if not
     */
    Future<Boolean> deleteAsync();

    /**
     * Rename current object key to <code>newName</code>
     * in async mode
     *
     * @param newName
     * @return
     */
    Future<Void> renameAsync(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * in async mode only if new key is not exists
     *
     * @param newName
     * @return
     */
    Future<Boolean> renamenxAsync(String newName);

    /**
     * Check object existence in async mode.
     *
     * @return <code>true</code> if object exists and <code>false</code> otherwise
     */
    Future<Boolean> isExistsAsync();

}
