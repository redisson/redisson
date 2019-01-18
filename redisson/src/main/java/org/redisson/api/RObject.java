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

/**
 * Base interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObject extends RObjectAsync {

    /**
     * Returns size of object in Redis memory
     * 
     * @return size of object
     */
    long sizeInMemory();
    
    /**
     * Restores object using its state returned by {@link #dump()} method.
     * 
     * @param state - state of object
     */
    void restore(byte[] state);
    
    /**
     * Restores object using its state returned by {@link #dump()} method and set time to live for it.
     * 
     * @param state - state of object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     */
    void restore(byte[] state, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Restores and replaces object if it already exists.
     * 
     * @param state - state of the object
     */
    void restoreAndReplace(byte[] state);

    /**
     * Restores and replaces object if it already exists and set time to live for it.
     * 
     * @param state - state of the object
     * @param timeToLive - time to live of the object
     * @param timeUnit - time unit
     */
    void restoreAndReplace(byte[] state, long timeToLive, TimeUnit timeUnit);
    
    /**
     * Returns dump of object
     * 
     * @return dump
     */
    byte[] dump();
    
    /**
     * Update the last access time of an object. 
     * 
     * @return <code>true</code> if object was touched else <code>false</code>
     */
    boolean touch();
    
    /**
     * Copy object from source Redis instance to destination Redis instance
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     */
    void migrate(String host, int port, int database, long timeout);

    /**
     * Copy object from source Redis instance to destination Redis instance
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @param timeout - maximum idle time in any moment of the communication with the destination instance in milliseconds
     */
    void copy(String host, int port, int database, long timeout);
    
    /**
     * Move object to another database
     *
     * @param database - Redis database number
     * @return <code>true</code> if key was moved else <code>false</code>
     */
    boolean move(int database);

    /**
     * Returns name of object
     *
     * @return name - name of object
     */
    String getName();

    /**
     * Deletes the object
     * 
     * @return <code>true</code> if it was exist and deleted else <code>false</code>
     */
    boolean delete();

    /**
     * Delete the objects.
     * Actual removal will happen later asynchronously.
     * <p>
     * Requires Redis 4.0+
     * 
     * @return <code>true</code> if it was exist and deleted else <code>false</code>
     */
    boolean unlink();
    
    /**
     * Rename current object key to <code>newName</code>
     *
     * @param newName - new name of object
     */
    void rename(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * only if new key is not exists
     *
     * @param newName - new name of object
     * @return <code>true</code> if object has been renamed successfully and <code>false</code> otherwise
     */
    boolean renamenx(String newName);

    /**
     * Check object existence
     *
     * @return <code>true</code> if object exists and <code>false</code> otherwise
     */
    boolean isExists();

    /**
     * Returns the underlying Codec used by this RObject
     * 
     * @return Codec of object
     */
    Codec getCodec();
}
