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
package org.redisson.api;

/**
 * Base interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObjectAsync {

    /**
     * Update the last access time of an object in async mode. 
     * 
     * @return <code>true</code> if object was touched else <code>false</code>
     */
    RFuture<Boolean> touchAsync();
    
    /**
     * Transfer an object from source Redis instance to destination Redis instance
     * in async mode
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @return void
     */
    RFuture<Void> migrateAsync(String host, int port, int database);

    /**
     * Move object to another database in async mode
     *
     * @param database - number of Redis database
     * @return <code>true</code> if key was moved <code>false</code> if not
     */
    RFuture<Boolean> moveAsync(int database);

    /**
     * Delete object in async mode
     *
     * @return <code>true</code> if object was deleted <code>false</code> if not
     */
    RFuture<Boolean> deleteAsync();

    /**
     * Delete the objects.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @return <code>true</code> if it was exist and deleted else <code>false</code>
     */
    RFuture<Boolean> unlinkAsync();
    
    /**
     * Rename current object key to <code>newName</code>
     * in async mode
     *
     * @param newName - new name of object
     * @return void
     */
    RFuture<Void> renameAsync(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * in async mode only if new key is not exists
     *
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    RFuture<Boolean> renamenxAsync(String newName);

    /**
     * Check object existence in async mode.
     *
     * @return <code>true</code> if object exists and <code>false</code> otherwise
     */
    RFuture<Boolean> isExistsAsync();

}
