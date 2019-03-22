/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import org.redisson.client.codec.Codec;

import reactor.core.publisher.Mono;

/**
 * Base interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObjectReactive {

    String getName();
    
    Codec getCodec();
    
    /**
     * Restores object using its state returned by {@link #dump()} method.
     * 
     * @param state - state of object
     * @return void
     */
    Mono<Void> restore(byte[] state);
    
    /**
     * Restores object using its state returned by {@link #dump()} method and set time to live for it.
     * 
     * @param state - state of object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     * @return void
     */
    Mono<Void> restore(byte[] state, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Restores and replaces object if it already exists.
     * 
     * @param state - state of the object
     * @return void
     */
    Mono<Void> restoreAndReplace(byte[] state);
    
    /**
     * Restores and replaces object if it already exists and set time to live for it.
     * 
     * @param state - state of the object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     * @return void
     */
    Mono<Void> restoreAndReplace(byte[] state, long timeToLive, TimeUnit timeUnit);

    /**
     * Returns dump of object
     * 
     * @return dump
     */
    Mono<byte[]> dump();
    
    /**
     * Update the last access time of an object. 
     * 
     * @return <code>true</code> if object was touched else <code>false</code>
     */
    Mono<Boolean> touch();    
    
    /**
     * Delete the objects.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @return <code>true</code> if it was exist and deleted else <code>false</code>
     */
    Mono<Boolean> unlink();
    
    /**
     * Copy object from source Redis instance to destination Redis instance
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    Mono<Void> copy(String host, int port, int database, long timeout);
    
    /**
     * Transfer a object from a source Redis instance to a destination Redis instance
     * in  mode
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     * @return void
     */
    Mono<Void> migrate(String host, int port, int database, long timeout);

    /**
     * Move object to another database in  mode
     *
     * @param database - number of Redis database
     * @return <code>true</code> if key was moved <code>false</code> if not
     */
    Mono<Boolean> move(int database);

    /**
     * Delete object in  mode
     *
     * @return <code>true</code> if object was deleted <code>false</code> if not
     */
    Mono<Boolean> delete();

    /**
     * Rename current object key to <code>newName</code>
     * in  mode
     *
     * @param newName - new name of object
     * @return void
     */
    Mono<Void> rename(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * in  mode only if new key is not exists
     *
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    Mono<Boolean> renamenx(String newName);

    /**
     * Check object existence
     *
     * @return <code>true</code> if object exists and <code>false</code> otherwise
     */
    Mono<Boolean> isExists();

    /**
     * Adds object event listener
     * 
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * 
     * @param listener - object event listener
     * @return listener id
     */
    Mono<Integer> addListener(ObjectListener listener);

    /**
     * Removes object event listener
     * 
     * @param listenerId - listener id
     * @return void
     */
    Mono<Void> removeListener(int listenerId);
    
}
