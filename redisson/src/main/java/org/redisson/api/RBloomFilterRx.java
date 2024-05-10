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

import io.reactivex.rxjava3.core.Single;

import java.util.Collection;

/**
 * Distributed implementation of Bloom filter based on Highway 128-bit hash.
 *
 * @author Nikita Koksharov
 *
 * @param <T> - type of object
 */
public interface RBloomFilterRx<T> extends RExpirableRx {

    /**
     * Adds element
     * 
     * @param object - element to add
     * @return <code>true</code> if element has been added successfully
     *         <code>false</code> if element is already present
     */
    Single<Boolean> add(T object);

    /**
     * Adds elements
     *
     * @param elements elements to add
     * @return number of added elements
     */
    Single<Long> add(Collection<T> elements);

    /**
     * Checks for element presence
     * 
     * @param object element
     * @return <code>true</code> if element is present
     *         <code>false</code> if element is not present
     */
    Single<Boolean> contains(T object);

    /**
     * Checks for elements presence
     *
     * @param elements elements to check presence
     * @return number of elements present
     */
    Single<Long> contains(Collection<T> elements);

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
    Single<Boolean> tryInit(long expectedInsertions, double falseProbability);

    /**
     * Returns expected amount of insertions per element.
     * Calculated during bloom filter initialization. 
     * 
     * @return expected amount of insertions per element
     */
    Single<Long> getExpectedInsertions();

    /**
     * Returns false probability of element presence. 
     * Calculated during bloom filter initialization.
     * 
     * @return false probability of element presence
     */
    Single<Double> getFalseProbability();

    /**
     * Returns number of bits in Redis memory required by this instance
     * 
     * @return number of bits
     */
    Single<Long> getSize();

    /**
     * Returns hash iterations amount used per element. 
     * Calculated during bloom filter initialization. 
     * 
     * @return hash iterations amount
     */
    Single<Integer> getHashIterations();

    /**
     * Calculates probabilistic number of elements already added to Bloom filter.
     *
     * @return probabilistic number of elements
     */
    Single<Long> count();

}
