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

import java.util.Collection;
import java.util.List;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 * 
 * @param <V> object type
 */
public interface RSortableReactive<V> {

    /**
     * Read data in sorted view
     * 
     * @param order for sorted data
     * @return sorted collection
     */
    Mono<V> readSorted(SortOrder order);

    /**
     * Read data in sorted view
     * 
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return sorted collection
     */
    Mono<V> readSorted(SortOrder order, int offset, int count);

    /**
     * Read data in sorted view
     * 
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param order for sorted data
     * @return sorted collection
     */
    Mono<V> readSorted(String byPattern, SortOrder order);

    /**
     * Read data in sorted view
     * 
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return sorted collection
     */
    Mono<V> readSorted(String byPattern, SortOrder order, int offset, int count);

    /**
     * Read data in sorted view
     * 
     * @param <T> object type 
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param getPatterns that is used to load values by keys in sorted view
     * @param order for sorted data
     * @return sorted collection
     */
    <T> Mono<Collection<T>> readSorted(String byPattern, List<String> getPatterns, SortOrder order);

    /**
     * Read data in sorted view
     * 
     * @param <T> object type
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param getPatterns that is used to load values by keys in sorted view
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return sorted collection
     */
    <T> Mono<Collection<T>> readSorted(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination 
     * @param order for sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, SortOrder order);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, SortOrder order, int offset, int count);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param order for sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, String byPattern, SortOrder order);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, String byPattern, SortOrder order, int offset, int count);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param getPatterns that is used to load values by keys in sorted view
     * @param order for sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order);

    /**
     * Sort data and store to <code>destName</code> list
     * 
     * @param destName list object destination
     * @param byPattern that is used to generate the keys that are used for sorting
     * @param getPatterns that is used to load values by keys in sorted view
     * @param order for sorted data
     * @param offset of sorted data
     * @param count of sorted data
     * @return length of sorted data
     */
    Mono<Integer> sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count);
    
}
