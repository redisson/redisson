/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
public interface RObject {

    /**
     * Returns name of object
     *
     * @return name
     */
    String getName();

    /**
     * Deletes the object
     */
    boolean delete();
    
    Future<Boolean> deleteAsync();

    /**
     * Rename current object key to <code>newName</code>
     * 
     * @param newName
     * @return
     */
    boolean rename(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * in async mode
     * 
     * @param newName
     * @return
     */
    Future<Boolean> renameAsync(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * only if new key is not exists 
     * 
     * @param newName
     * @return
     */
    boolean renamenx(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * in async mode only if new key is not exists
     * 
     * @param newName
     * @return
     */
    Future<Boolean> renamenxAsync(String newName);
    
}
