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

import java.util.List;

/**
 * Import session for Map objects sharing the same field names.
 * <p>
 * Buffered Map objects are written to Redis when the buffer reaches the configured
 * batch size and when {@link #flush()} is called. Objects added but not flushed are
 * never written, so {@link #flush()} has to be called before the import object is discarded.
 * <p>
 * Each imported Map object replaces the whole object stored under the same name.
 * <p>
 * Map objects can be added from several threads.
 *
 * @author Nikita Koksharov
 *
 * @param <K> field type
 * @param <V> value type
 */
public interface RMapsImport<K, V> extends RMapsImportAsync<K, V> {

    /**
     * Adds Map object stored under the specified <code>name</code>.
     * <p>
     * Values are matched to the field names defined for this import object by position,
     * so the amount of values has to be equal to the amount of field names.
     * Values are encoded immediately.
     *
     * @param name name of object
     * @param values values ordered as the defined field names
     */
    void add(String name, V... values);

    /**
     * Adds Map object stored under the specified <code>name</code>.
     * <p>
     * Values are matched to the field names defined for this import object by position,
     * so the amount of values has to be equal to the amount of field names.
     * Values are encoded immediately.
     *
     * @param name name of object
     * @param values values ordered as the defined field names
     */
    void add(String name, List<V> values);

    /**
     * Writes all buffered Map objects.
     */
    void flush();

    /**
     * Returns the amount of Map objects written by this import object.
     *
     * @return amount of Map objects
     */
    long getImportedCount();

}
