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

/**
 * Base interface for all Redisson objects
 *
 * @author Nikita Koksharov
 *
 */
public interface RObject extends RObjectAsync {

    /**
     * Transfer an object from source Redis instance to destination Redis instance
     *
     * @param host - destination host
     * @param port - destination port
     * @param database - destination database
     * @return
     */
    void migrate(String host, int port, int database);

    /**
     * Move object to another database
     *
     * @param database
     * @return <code>true</code> if key was moved else <code>false</code>
     */
    boolean move(int database);

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

    /**
     * Rename current object key to <code>newName</code>
     *
     * @param newName
     */
    void rename(String newName);

    /**
     * Rename current object key to <code>newName</code>
     * only if new key is not exists
     *
     * @param newName
     * @return
     */
    boolean renamenx(String newName);

    /**
     * Check object existence
     *
     * @return <code>true</code> if object exists and <code>false</code> otherwise
     */
    boolean isExists();

}
