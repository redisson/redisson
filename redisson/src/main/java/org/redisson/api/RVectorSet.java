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

import java.util.List;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorInfo;
import org.redisson.api.vector.VectorSimilarArgs;
import org.redisson.client.protocol.ScoreAttributesEntry;
import org.redisson.client.protocol.ScoredEntry;

/**
 * Vector Set
 * <p>
 * Requires <b>Redis 8.0.0 or higher.</b>
 *
 * @author Nikita Koksharov
 *
 */
public interface RVectorSet extends RExpirable, RVectorSetAsync {

    /**
     * Adds an element
     *
     * @param args add arguments
     * @return <code>true</code> if element was added and <code>false</code> if updated
     */
    boolean add(VectorAddArgs args);

    /**
     * Returns the number of elements
     *
     * @return number of elements
     */
    int size();

    /**
     * Returns the number of dimensions of vectors
     *
     * @return dimensions count
     */
    int dimensions();

    /**
     * Retrieves approximate vector associated with a given element name
     *
     * @param name element name
     * @return list of vector coordinates
     */
    List<Double> getVector(String name);

    /**
     * Retrieves raw internal representation of
     * the approximate vector associated with a given element name
     *
     * @param name element name
     * @return list of raw vector values
     */
    List<Object> getRawVector(String name);

    /**
     * Retrieves attributes associated with a given element name
     *
     * @param name element name
     * @param clazz type for deserialization
     * @return attributes
     */
    <T> T getAttributes(String name, Class<T> clazz);

    /**
     * Returns metadata for this vector set
     *
     * @return vector set information
     */
    VectorInfo getInfo();

    /**
     * Retrieves the neighbors of a specified element by name
     *
     * @param element element name
     * @return list of neighbor element names
     */
    List<String> getNeighbors(String element);

    /**
     * Retrieves the neighbors with scores of a specified element by name
     *
     * @param element element name
     * @return list of neighbor elements with scores
     */
    List<ScoredEntry<String>> getNeighborEntries(String element);

    /**
     * Returns a random element name
     *
     * @return random element name
     */
    String random();

    /**
     * Returns random element names
     *
     * @param count number of elements to return
     * @return list of random element names
     */
    List<String> random(int count);

    /**
     * Removes an element by name
     *
     * @param element element name to remove
     * @return <code>true</code> if element was removed, <code>false</code> otherwise
     */
    boolean remove(String element);

    /**
     * Sets attributes for an element by name
     *
     * @param element element name
     * @param attributes attributes
     * @return <code>true</code> if attributes were set, <code>false</code> otherwise
     */
    boolean setAttributes(String element, Object attributes);

    /**
     * Retrieves element names similar to a specified vector or element
     *
     * @param args vector similarity arguments
     * @return list of similar element names
     */
    List<String> getSimilar(VectorSimilarArgs args);

    /**
     * Retrieves element names with scores similar to a given vector or element
     *
     * @param args similarity arguments
     * @return list of similar element names with scores
     */
    List<ScoredEntry<String>> getSimilarEntries(VectorSimilarArgs args);

    /**
     * Retrieves element names with scores and attributes similar to a given vector or element
     *
     * @param args similarity arguments
     * @return list of similar element names with scores and attributes
     */
    List<ScoreAttributesEntry<String>> getSimilarScoreAttributesEntries(VectorSimilarArgs args);
}
