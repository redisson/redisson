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

import java.util.Collection;
import java.util.Set;
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.api.bloomfilter.BloomFilterInfoOption;
import org.redisson.api.bloomfilter.BloomFilterInitArgs;
import org.redisson.api.bloomfilter.BloomFilterInsertArgs;

/**
 * Bloom filter based on BF.* commands
 *
 * @author Su Ko
 *
 * @param <T> - type of object
 */
public interface RBloomFilterNative<T> extends RExpirable, RBloomFilterNativeAsync<T> {

    /**
     * Adds element
     *
     * @param element - element to add
     *
     * @return <code>true</code> if element has been added successfully
     *         <code>false</code> if element is already present
     */
    Boolean add(T element);

    /**
     * Adds elements
     *
     * @param elements elements to add
     *
     * @return set of elements representing whether each element has been added successfully
     */
    Set<T> add(Collection<T> elements);

    /**
     * create filter (if filter is not existing and not NOCREATE mode)
     * and
     * Adds elements
     *
     * @param args insert args
     *
     * @return set of elements representing whether each element has been added successfully
     */
    Set<T> insert(BloomFilterInsertArgs<T> args);

    /**
     * Initializes Bloom filter
     *
     * @param errorRate acceptable false positive rate
     * @param capacity expected number of elements to be added
     */
    void init(double errorRate, long capacity);

    /**
     * Initializes Bloom filter
     *
     * @param args init args
     */
    void init(BloomFilterInitArgs args);

    /**
     * Checks for element presence
     *
     * @param element element
     *
     * @return <code>true</code> if element is present
     *         <code>false</code> if element is not present
     */
    Boolean exists(T element);

    /**
     * Checks for elements presence
     *
     * @param elements elements to check presence
     *
     * @return set of elements representing whether each element is present
     */
    Set<T> exists(Collection<T> elements);

    /**
     * Returns count of present elements
     *
     * @return count of present elements
     */
    Long count();

    /**
     * Returns Bloom filter information
     *
     * @return Bloom filter information
     */
    BloomFilterInfo getInfo();

    /**
     * Returns specific Bloom filter information
     *
     * @param option information option
     * @return specific Bloom filter information value
     */
    long getInfo(BloomFilterInfoOption option);

}
