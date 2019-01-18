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

/**
 * Distributed implementation of Bloom filter based on Highway 128-bit hash.
 *
 * @author Nikita Koksharov
 *
 * @param <T> - type of object
 */
public interface RBloomFilter<T> extends RExpirable {

    /**
     * Adds element
     * 
     * @param object - element to add
     * @return <code>true</code> if element has been added successfully
     *         <code>false</code> if element is already present
     */
    boolean add(T object);

    /**
     * Check for element present
     * 
     * @param object - element
     * @return <code>true</code> if element is present
     *         <code>false</code> if element is not present
     */
    boolean contains(T object);

    /**
     * Initializes Bloom filter params (size and hashIterations)
     * calculated from <code>expectedInsertions</code> and <code>falseProbability</code>
     * Stores config to Redis server.
     *
     * @param expectedInsertions - expected amount of insertions per element
     * @param falseProbability - expected false probability
     * @return <code>true</code> if Bloom filter initialized
     *         <code>false</code> if Bloom filter already has been initialized
     */
    boolean tryInit(long expectedInsertions, double falseProbability);

    /**
     * Returns expected amount of insertions per element.
     * Calculated during bloom filter initialization. 
     * 
     * @return expected amount of insertions per element
     */
    long getExpectedInsertions();

    /**
     * Returns false probability of element presence. 
     * Calculated during bloom filter initialization.
     * 
     * @return false probability of element presence
     */
    double getFalseProbability();

    /**
     * Returns number of bits in Redis memory required by this instance
     * 
     * @return number of bits
     */
    long getSize();

    /**
     * Returns hash iterations amount used per element. 
     * Calculated during bloom filter initialization. 
     * 
     * @return hash iterations amount
     */
    int getHashIterations();

    /**
     * Calculates probabilistic number of elements already added to Bloom filter.
     *
     * @return probabilistic number of elements
     */
    long count();

}
