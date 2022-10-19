/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RKeysRx {

    /**
     * Move object to another database
     *
     * @param name of object
     * @param database - Redis database number
     * @return <code>true</code> if key was moved else <code>false</code>
     */
    Single<Boolean> move(String name, int database);
    
    /**
     * Transfer object from source Redis instance to destination Redis instance
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    Completable migrate(String name, String host, int port, int database, long timeout);
    
    /**
     * Copy object from source Redis instance to destination Redis instance
     *
     * @param name of object
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    Completable copy(String name, String host, int port, int database, long timeout);
    
    /**
     * Set a timeout for object. After the timeout has expired,
     * the key will automatically be deleted.
     *
     * @param name of object
     * @param timeToLive - timeout before object will be deleted
     * @param timeUnit - timeout time unit
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expire(String name, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Set an expire date for object. When expire date comes
     * the key will automatically be deleted.
     * 
     * @param name of object
     * @param timestamp - expire date in milliseconds (Unix timestamp)
     * @return <code>true</code> if the timeout was set and <code>false</code> if not
     */
    Single<Boolean> expireAt(String name, long timestamp);
    
    /**
     * Clear an expire timeout or expire date for object.
     * 
     * @param name of object
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    Single<Boolean> clearExpire(String name);
    
    /**
     * Rename object with <code>oldName</code> to <code>newName</code>
     * only if new key is not exists
     *
     * @param oldName - old name of object
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    Single<Boolean> renamenx(String oldName, String newName);
    
    /**
     * Rename current object key to <code>newName</code>
     *
     * @param currentName - current name of object
     * @param newName - new name of object
     * @return void
     */
    Completable rename(String currentName, String newName);
    
    /**
     * Remaining time to live of Redisson object that has a timeout
     *
     * @param name of key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Single<Long> remainTimeToLive(String name);

    /**
     * Update the last access time of an object. 
     * 
     * @param names of keys
     * @return count of objects were touched
     */
    Single<Long> touch(String... names);
    
    /**
     * Checks if provided keys exist
     * 
     * @param names of keys
     * @return amount of existing keys
     */
    Single<Long> countExists(String... names);
    
    /**
     * Get Redis object type by key
     * 
     * @param key - name of key
     * @return type of key
     */
    Single<RType> getType(String key);
    
    /**
     * Load keys in incrementally iterate mode. Keys traversed with SCAN operation.
     * Each SCAN operation loads up to 10 keys per request.
     *
     * @return keys
     */
    Flowable<String> getKeys();
    
    /**
     * Load keys in incrementally iterate mode. Keys traversed with SCAN operation.
     * Each SCAN operation loads up to <code>count</code> keys per request.
     *
     * @param count - keys loaded per request to Redis
     * @return keys
     */
    Flowable<String> getKeys(int count);

    /**
     * Find keys by pattern and load it in incrementally iterate mode.
     * Keys traversed with SCAN operation.
     * Each SCAN operation loads up to 10 keys per request.
     * <p>
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return keys
     */
    Flowable<String> getKeysByPattern(String pattern);

    /**
     * Get all keys by pattern using iterator. 
     * Keys traversed with SCAN operation. Each SCAN operation loads 
     * up to <code>count</code> keys per request. 
     * <p>
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @param count - keys loaded per request to Redis
     * @return keys
     */
    Flowable<String> getKeysByPattern(String pattern, int count);
    
    /**
     * Get hash slot identifier for key.
     * Available for cluster nodes only.
     *
     * Uses <code>KEYSLOT</code> Redis command.
     *
     * @param key - name of key
     * @return slot number
     */
    Single<Integer> getSlot(String key);

    /**
     * Get random key
     *
     * Uses <code>RANDOM_KEY</code> Redis command.
     *
     * @return random key
     */
    Maybe<String> randomKey();

    /**
     * Delete multiple objects by a key pattern.
     *
     * Uses Lua script.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return deleted objects amount
     */
    Single<Long> deleteByPattern(String pattern);

    /**
     * Delete multiple objects by name.
     *
     * Uses <code>DEL</code> Redis command.
     *
     * @param keys - object names
     * @return deleted objects amount
     */
    Single<Long> delete(String... keys);

    /**
     * Delete multiple objects by name.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @param keys of objects
     * @return number of removed keys
     */
    Single<Long> unlink(String... keys);
    
    /**
     * Returns the number of keys in the currently-selected database
     *
     * @return count of keys
     */
    Single<Long> count();
    
    /**
     * Delete all the keys of the currently selected database
     *
     * Uses <code>FLUSHDB</code> Redis command.
     * 
     * @return void
     */
    Completable flushdb();

    /**
     * Swap two databases.
     * <p>
     * Requires Redis 4.0+
     *
     * @return void
     */
    Completable swapdb(int db1, int db2);

    /**
     * Delete all the keys of all the existing databases
     *
     * Uses <code>FLUSHALL</code> Redis command.
     *
     * @return void
     */
    Completable flushall();

    /**
     * Delete all keys of currently selected database
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     *
     * @return void
     */
    Completable flushdbParallel();

    /**
     * Delete all keys of all existing databases
     * in background without blocking server.
     * <p>
     * Requires Redis 4.0+
     *
     * @return void
     */
    Completable flushallParallel();

}
