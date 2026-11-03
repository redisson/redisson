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

import java.util.concurrent.TimeUnit;

/**
 * Base asynchronous interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObjectAsync {

    /**
     * Returns number of seconds spent since last write or read operation over this object.
     *
     * @return number of seconds
     */
    RFuture<Long> getIdleTimeAsync();

    /**
     * Returns count of references over this object.
     *
     * @return count of reference
     */
    RFuture<Integer> getReferenceCountAsync();

    /**
     * Returns the logarithmic access frequency counter over this object.
     *
     * @return frequency counter
     */
    RFuture<Integer> getAccessFrequencyAsync();

    /**
     * Returns the internal encoding for the Redis object
     *
     * @return internal encoding
     */
    RFuture<ObjectEncoding> getInternalEncodingAsync();

    /**
     * Returns bytes amount used by object in Redis memory. 
     * 
     * @return size in bytes
     */
    RFuture<Long> sizeInMemoryAsync();
    
    /**
     * Restores object using its state returned by {@link #dumpAsync()} method.
     * 
     * @param state - state of object
     * @return void
     */
    RFuture<Void> restoreAsync(byte[] state);
    
    /**
     * Restores object using its state returned by {@link #dumpAsync()} method and set time to live for it.
     * 
     * @param state - state of object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     * @return void
     */
    RFuture<Void> restoreAsync(byte[] state, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Restores and replaces object if it already exists.
     * 
     * @param state - state of the object
     * @return void
     */
    RFuture<Void> restoreAndReplaceAsync(byte[] state);
    
    /**
     * Restores and replaces object if it already exists and set time to live for it.
     * 
     * @param state - state of the object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     * @return void
     */
    RFuture<Void> restoreAndReplaceAsync(byte[] state, long timeToLive, TimeUnit timeUnit);

    /**
     * Returns dump of object
     * 
     * @return dump
     */
    RFuture<byte[]> dumpAsync();
    
    /**
     * Update the last access time of an object in async mode. 
     * 
     * @return <code>true</code> if object was touched else <code>false</code>
     */
    RFuture<Boolean> touchAsync();
    
    /**
     * Transfer object from source Redis instance to destination Redis instance
     * in async mode
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    RFuture<Void> migrateAsync(String host, int port, int database, long timeout);

    /**
     * Copy object from source Redis instance to destination Redis instance
     * in async mode
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    RFuture<Void> copyAsync(String host, int port, int database, long timeout);

    /**
     * Copy this object instance to the new instance with a defined name.
     *
     * @param destination name of the destination instance
     * @return <code>true</code> if this object instance was copied else <code>false</code>
     */
    RFuture<Boolean> copyAsync(String destination);

    /**
     * Copy this object instance to the new instance with a defined name and database.
     *
     * @param destination name of the destination instance
     * @param database database number
     * @return <code>true</code> if this object instance was copied else <code>false</code>
     */
    RFuture<Boolean> copyAsync(String destination, int database);

    /**
     * Copy this object instance to the new instance with a defined name, and replace it if it already exists.
     *
     * @param destination name of the destination instance
     * @return <code>true</code> if this object instance was copied else <code>false</code>
     */
    RFuture<Boolean> copyAndReplaceAsync(String destination);

    /**
     * Copy this object instance to the new instance with a defined name and database, and replace it if it already exists.
     *
     * @param destination name of the destination instance
     * @param database database number
     * @return <code>true</code> if this object instance was copied else <code>false</code>
     */
    RFuture<Boolean> copyAndReplaceAsync(String destination, int database);

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

    /**
     * Protects this object from eviction: under {@code maxmemory} none of its keys
     * is chosen for eviction, whatever the eviction policy.
     * <p>
     * Every key the object has now is protected, including the auxiliary keys some
     * objects keep beside their data, such as the configuration of a Bloom filter or
     * the expiration index of a map cache. A key the object creates later is not:
     * an auxiliary key that doesn't exist yet, or the key of a collection emptied and
     * filled again, since removing the last element deletes the key. Call this method
     * again after such a change.
     * <p>
     * The protection stays with the keys when values are overwritten, through
     * {@code rename}, {@code copy} (the copy is protected too), {@code move} and a restart
     * from RDB or AOF, and is lost when a key is deleted or expires. {@code dump} doesn't
     * carry it. It doesn't protect the object from expiration or deletion.
     * <p>
     * When protected keys hold the memory eviction needs, used memory may grow up to
     * 1.25 times {@code maxmemory} before writes are rejected with OOM. This method is
     * rejected then too, unlike {@link #unblessAsync()}, so memory can always be released.
     * <p>
     * Uses {@code BLESS SET key NO-EVICT} on each key.
     * <p>
     * Requires <b>Redis 8.12.0 or higher.</b>
     *
     * @return {@code true} if a key was protected by this call,
     *         {@code false} if every key already was or the object doesn't exist
     */
    RFuture<Boolean> blessAsync();

    /**
     * Removes the protection from eviction set by {@link #blessAsync()} from every key
     * of this object.
     * <p>
     * Allowed when memory is full, so the memory that protected keys hold
     * can always be released.
     * <p>
     * Uses {@code BLESS CLEAR key NO-EVICT} on each key.
     * <p>
     * Requires <b>Redis 8.12.0 or higher.</b>
     *
     * @return {@code true} if the protection was removed from a key,
     *         {@code false} if no key had it or the object doesn't exist
     */
    RFuture<Boolean> unblessAsync();

    /**
     * Checks if this object is protected from eviction by {@link #blessAsync()}.
     * <p>
     * Uses {@code BLESS GET} on each key.
     * <p>
     * Requires <b>Redis 8.12.0 or higher.</b>
     *
     * @return {@code true} if the object exists and every key it has now is protected
     */
    RFuture<Boolean> isBlessedAsync();

    /**
     * Adds object event listener
     * 
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * 
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

    /**
     * Removes object event listener
     * 
     * @param listenerId - listener id
     */
    RFuture<Void> removeListenerAsync(int listenerId);
    
}
