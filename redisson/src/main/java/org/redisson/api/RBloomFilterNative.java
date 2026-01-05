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
import java.util.List;
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.api.bloomfilter.BloomFilterInfoOption;
import org.redisson.api.bloomfilter.BloomFilterInitOptions;
import org.redisson.api.bloomfilter.BloomFilterInsertOptions;

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
     * @return list of booleans representing whether each element has been added successfully
     */
    List<Boolean> add(Collection<T> elements);

    /**
     * create filter (if filter is not existing and not NOCREATE mode)
     * and
     * Adds elements
     *
     * @param options insert options
     * @param elements elements to add
     *
     * @return list of booleans representing whether each element has been added successfully
     */
    List<Boolean> insert(BloomFilterInsertOptions options, Collection<T> elements);

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
     * @param options init options
     */
    void init(BloomFilterInitOptions options);

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
     * @return list of booleans representing whether each element is present
     */
    List<Boolean> exists(Collection<T> elements);

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
