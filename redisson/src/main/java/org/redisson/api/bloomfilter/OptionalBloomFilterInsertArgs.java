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
package org.redisson.api.bloomfilter;

/**
 * OptionalBloomFilterInsertArgs for BF.INSERT command
 *
 * @author Su Ko
 */
public interface OptionalBloomFilterInsertArgs<V> extends BloomFilterInsertArgs<V> {

    /**
     * Defines BloomFilter to BF.INSERT command
     *
     * @param capacity is number of entries intended to be added to the filter. must be greater than 0
     * When the number of entries reaches capacity, a new subfilter is created.
     *
     * @return OptionalBloomFilterInsertArgs
     */
    OptionalBloomFilterInsertArgs<V> capacity(long capacity);

    /**
     * Defines BloomFilter to BF.INSERT command
     *
     * @param errorRate is desired probability for false positives. must be greater than 0 and less than 1
     *
     * @return OptionalBloomFilterInsertArgs
     */
    OptionalBloomFilterInsertArgs<V> errorRate(double errorRate);

    /**
     * Defines BloomFilter to BF.INSERT command
     *
     * @param expansionRate is the value that is multiplied by the size of the last subfilter when a new subfilter is created when capacity is reached.
     * expansionRate and nonScaling are mutually exclusive
     *
     * @return OptionalBloomFilterInsertArgs
     */
    OptionalBloomFilterInsertArgs<V> expansionRate(long expansionRate);

    /**
     * Defines BloomFilter to BF.INSERT command
     *
     * @param nonScaling is option that prevents subfliters from being created even when fcapacity is reached.
     * expansionRate and nonScaling are mutually exclusive
     *
     * @return OptionalBloomFilterInsertArgs
     */
    OptionalBloomFilterInsertArgs<V> nonScaling(boolean nonScaling);

    /**
     * Defines BloomFilter to BF.INSERT command
     *
     * @param noCreate is option that prevents subfliters from being created even when fcapacity is reached.
     * expansionRate and nonScaling are mutually exclusive
     *
     * @return OptionalBloomFilterInsertArgs
     */
    OptionalBloomFilterInsertArgs<V> noCreate(boolean noCreate);
}