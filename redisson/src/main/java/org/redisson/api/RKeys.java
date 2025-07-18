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
import java.util.stream.Stream;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RKeys extends RKeysAsync {

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param limit - limit of keys amount
     * @return Iterable object
     */
    @Deprecated
    Iterable<String> getKeysWithLimit(int limit);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param limit - limit of keys amount
     * @param pattern - match pattern
     * @return Iterable object
     */
    @Deprecated
    Iterable<String> getKeysWithLimit(String pattern, int limit);

    /**
     * Move object to another database
     *
     * @param name of object
     * @param database - Redis database number
     * @return <code>true</code> if key was moved else <code>false</code>
     */
    boolean move(String name, int database);
    
    /**
     * Transfer object from source Redis instance to destination Redis instance
     * @deprecated use {@link #migrate(MigrateArgs)}  instead
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     */
    @Deprecated
    void migrate(String name, String host, int port, int database, long timeout);

    /**
     * Transfer object from source Redis instance to destination Redis instance
     *
     * @param migrateArgs migrateArgs
     */
    void migrate(MigrateArgs migrateArgs);

    /**
     * Copy object from source Redis instance to destination Redis instance
     * @deprecated use {@link #migrate(MigrateArgs)}  instead
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     *
     */
    @Deprecated
    void copy(String name, String host, int port, int database, long timeout);
    
    /**
     * Set a timeout for object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param name of object
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expire(String name, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Set an expire date for object. When expire date comes
     * the key will automatically be deleted.
     * 
     * @param name of object
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    boolean expireAt(String name, long timestamp);
    
    /**
     * Clear an expire timeout or expire date for object.
     * 
     * @param name of object
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    boolean clearExpire(String name);
    
    /**
     * Rename object with <code>oldName</code> to <code>newName</code>
     * only if new key is not exists
     *
     * @param oldName - old name of object
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    boolean renamenx(String oldName, String newName);
    
    /**
     * Rename current object key to <code>newName</code>
     *
     * @param currentName - current name of object
     * @param newName - new name of object
     */
    void rename(String currentName, String newName);
    
    /**
     * Remaining time to live of Redisson object that has a timeout
     *
     * @param name of key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    long remainTimeToLive(String name);

    /**
     * Update the last access time of an object. 
     * 
     * @param names of keys
     * @return count of objects were touched
     */
    long touch(String... names);
    
    /**
     * Returns amount of existing keys
     * 
     * @param names of keys
     * @return amount of existing keys
     */
    long countExists(String... names);
    
    /**
     * Get Redis object type by key
     * 
     * @param key - name of key
     * @return type of key
     */
    RType getType(String key);
    
    /**
     * Get hash slot identifier for key.
     * Available for cluster nodes only
     *
     * @param key - name of key
     * @return slot number
     */
    int getSlot(String key);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     * 
     * @param pattern - match pattern
     * @return Iterable object
     */
    @Deprecated
    Iterable<String> getKeysByPattern(String pattern);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param pattern - match pattern
     * @param count - keys loaded per request to Redis
     * @return Iterable object
     */
    @Deprecated
    Iterable<String> getKeysByPattern(String pattern, int count);

    /**
     * Get all keys using iterable. Keys traversing with SCAN operation.
     * Each SCAN operation loads up to <code>10</code> keys per request. 
     *
     * @return Iterable object
     */
    Iterable<String> getKeys();

    /**
     * Get all keys using iterable. Keys traversing with SCAN operation.
     *
     * @param options scan options
     * @return Iterable object
     */
    Iterable<String> getKeys(KeysScanOptions options);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param count - keys loaded per request to Redis
     * @return Iterable object
     */
    @Deprecated
    Iterable<String> getKeys(int count);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     * 
     * @param pattern - match pattern
     * @return Iterable object
     */
    @Deprecated
    Stream<String> getKeysStreamByPattern(String pattern);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param pattern - match pattern
     * @param count - keys loaded per request to Redis
     * @return Iterable object
     */
    @Deprecated
    Stream<String> getKeysStreamByPattern(String pattern, int count);
    
    /**
     * Get all keys using Stream. Keys traversing with SCAN operation.
     * Each SCAN operation loads up to <code>10</code> keys per request.
     *
     * @return Iterable object
     */
    Stream<String> getKeysStream();

    /**
     * Get all keys using Stream. Keys traversing with SCAN operation.
     * Each SCAN operation loads up to <code>10</code> keys per request.
     *
     * @return Iterable object
     */
    Stream<String> getKeysStream(KeysScanOptions options);

    /**
     * Use {@link #getKeys(KeysScanOptions)} instead.
     *
     * @param count - keys loaded per request to Redis
     * @return Iterable object
     */
    @Deprecated
    Stream<String> getKeysStream(int count);
    
    /**
     * Get random key
     *
     * @return random key
     */
    String randomKey();

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
    long deleteByPattern(String pattern);

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
    long unlinkByPattern(String pattern);

    /**
     * Delete multiple objects
     *
     * @param objects of Redisson
     * @return number of removed keys
     */
    long delete(RObject... objects);
    
    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return number of removed keys
     */
    long delete(String... keys);

    /**
     * Delete multiple objects by name.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @param keys of objects
     * @return number of removed keys
     */
    long unlink(String... keys);
    
    /**
     * Returns the number of keys in the currently-selected database
     *
     * @return count of keys
     */
    long count();

    /**
     * Swap two databases.
     */
    void swapdb(int db1, int db2);

    /**
     * Delete all keys of currently selected database
     */
    void flushdb();

    /**
     * Delete all keys of currently selected database 
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     * 
     */
    void flushdbParallel();

    /**
     * Delete all keys of all existing databases
     */
    void flushall();
    
    /**
     * Delete all keys of all existing databases
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     * 
     */
    void flushallParallel();

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
    int addListener(ObjectListener listener);

    /**
     * Removes global object event listener
     *
     * @param listenerId - listener id
     */
    void removeListener(int listenerId);

}
