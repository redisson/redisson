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

import org.redisson.api.options.KeysScanOptions;
import org.redisson.api.keys.MigrateArgs;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RKeysAsync {

    /**
     * Move object to another database
     *
     * @param name of object
     * @param database - Redis database number
     * @return <code>true</code> if key was moved else <code>false</code>
     */
    RFuture<Boolean> moveAsync(String name, int database);
    
    /**
     * Transfer object from source Redis instance to destination Redis instance
     * @deprecated use {@link #migrateAsync(MigrateArgs)}  instead
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void 
     */
    RFuture<Void> migrateAsync(String name, String host, int port, int database, long timeout);

    /**
     * Transfer object from source Redis instance to destination Redis instance
     *
     * @param migrateArgs migrateArgs
     */
    RFuture<Void> migrateAsync(MigrateArgs migrateArgs);

    /**
     * Copy object from source Redis instance to destination Redis instance
     * in async mode
     *
     * @deprecated use {@link #migrateAsync(MigrateArgs)}  instead
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    @Deprecated
    RFuture<Void> copyAsync(String name, String host, int port, int database, long timeout);
    
    /**
     * Set a timeout for object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param name of object
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAsync(String name, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Set an expire date for object. When expire date comes
     * the key will automatically be deleted.
     * 
     * @param name of object
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    RFuture<Boolean> expireAtAsync(String name, long timestamp);
    
    /**
     * Clear an expire timeout or expire date for object.
     *
     * @param name of object
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    RFuture<Boolean> clearExpireAsync(String name);
    
    /**
     * Rename object with <code>oldName</code> to <code>newName</code>
     * only if new key is not exists
     *
     * @param oldName - old name of object
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    RFuture<Boolean> renamenxAsync(String oldName, String newName);
    
    /**
     * Rename current object key to <code>newName</code>
     *
     * @param currentName - current name of object
     * @param newName - new name of object
     * @return void
     */
    RFuture<Void> renameAsync(String currentName, String newName);
    
    /**
     * Remaining time to live of Redisson object that has a timeout
     *
     * @param name of key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    RFuture<Long> remainTimeToLiveAsync(String name);
    
    /**
     * Update the last access time of an object. 
     * 
     * @param names of keys
     * @return count of objects were touched
     */
    RFuture<Long> touchAsync(String... names);
    
    /**
     * Checks if provided keys exist
     * 
     * @param names of keys
     * @return amount of existing keys
     */
    RFuture<Long> countExistsAsync(String... names);

    /**
     * Get all keys using iterable. Keys traversing with SCAN operation.
     * Each SCAN operation loads up to <code>10</code> keys per request.
     *
     * @return Asynchronous Iterable object
     */
    AsyncIterator<String> getKeysAsync();

    /**
     * Get all keys using iterable. Keys traversing with SCAN operation.
     *
     * @param options scan options
     * @return Asynchronous Iterable object
     */
    AsyncIterator<String> getKeysAsync(KeysScanOptions options);

    /**
     * Get Redis object type by key
     * 
     * @param key - name of key
     * @return type of key
     */
    RFuture<RType> getTypeAsync(String key);
    
    /**
     * Get hash slot identifier for key in async mode.
     * Available for cluster nodes only
     *
     * @param key - name of key
     * @return slot
     */
    RFuture<Integer> getSlotAsync(String key);

    /**
     * Get random key in async mode
     *
     * @return random key
     */
    RFuture<String> randomKeyAsync();

    /**
     * Delete multiple objects by a key pattern.
     * <p>
     * Method executes in <b>NON atomic way</b> in cluster mode due to lua script limitations.
     * <p>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return number of removed keys
     */
    RFuture<Long> deleteByPatternAsync(String pattern);

    /**
     * Unlink multiple objects by a key pattern.
     * <p>
     * Method executes in <b>NON atomic way</b> in cluster mode due to lua script limitations.
     * <p>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return number of removed keys
     */
    RFuture<Long> unlinkByPatternAsync(String pattern);

    /**
     * Delete multiple objects
     *
     * @param objects of Redisson
     * @return number of removed keys
     */
    RFuture<Long> deleteAsync(RObject... objects);
    
    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return number of removed keys
     */
    RFuture<Long> deleteAsync(String... keys);

    /**
     * Delete multiple objects by name.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @param keys - object names
     * @return number of removed keys
     */
    RFuture<Long> unlinkAsync(String... keys);
    
    /**
     * Returns the number of keys in the currently-selected database in async mode
     *
     * @return number of keys
     */
    RFuture<Long> countAsync();

    /**
     * Swap two databases.
     * <p>
     * Requires Redis 4.0+
     *
     * @return void
     */
    RFuture<Void> swapdbAsync(int db1, int db2);

    /**
     * Delete all keys of currently selected database
     *
     * @return void
     */
    RFuture<Void> flushdbAsync();

    /**
     * Delete all keys of all existing databases
     *
     * @return void
     */
    RFuture<Void> flushallAsync();

    /**
     * Delete all keys of currently selected database
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     * 
     * @return void
     */
    RFuture<Void> flushdbParallelAsync();

    /**
     * Delete all keys of all existing databases
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     * 
     * @return void
     */
    RFuture<Void> flushallParallelAsync();

    /**
     * Adds global object event listener
     * which is invoked for each Redisson object.
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.SetObjectListener
     * @see org.redisson.api.listener.NewObjectListener
     * @see org.redisson.api.listener.FlushListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

    /**
     * Removes global object event listener
     *
     * @param listenerId - listener id
     */
    RFuture<Void> removeListenerAsync(int listenerId);

}
