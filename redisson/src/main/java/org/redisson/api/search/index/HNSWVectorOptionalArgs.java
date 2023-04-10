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
package org.redisson.api.search.index;

/**
 * Optional options object for vector field index which uses HNSW indexing method.
 *
 * @author Nikita Koksharov
 *
 */
public interface HNSWVectorOptionalArgs extends FieldIndex {

    /**
     * Defines initial vector capacity.
     *
     * @param value initial vector capacity
     * @return vector options
     */
    HNSWVectorOptionalArgs initialCapacity(int value);

    /**
     * Defines number of maximum allowed outgoing edges for each node.
     *
     * @param value number of maximum allowed outgoing edges
     * @return vector options
     */
    HNSWVectorOptionalArgs m(int value);

    /**
     * Defines number of maximum allowed potential outgoing edges candidates for each node.
     *
     * @param value number of maximum allowed potential outgoing edges
     * @return vector options
     */
    HNSWVectorOptionalArgs efConstruction(int value);

    /**
     * Defines number of maximum top candidates to hold during the KNN search.
     *
     * @param value number of maximum top candidates
     * @return vector options
     */
    HNSWVectorOptionalArgs efRuntime(int value);

    /**
     * Defines relative factor that sets the boundaries in which a range query may search for candidates
     *
     * @param value relative factor
     * @return
     */
    HNSWVectorOptionalArgs epsilon(double value);

}
