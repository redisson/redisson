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
package org.redisson.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

import org.redisson.api.mapreduce.RCollectionMapReduce;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RSortedSet<V> extends SortedSet<V>, RObject {

    /**
     * Returns <code>RMapReduce</code> object associated with this object
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();

    Collection<V> readAll();
    
    RFuture<Collection<V>> readAllAsync();
    
    RFuture<Boolean> addAsync(V value);
    
    RFuture<Boolean> removeAsync(Object value);
    
    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator for values
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<? super V> comparator);

    /**
     * Returns element iterator that can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * See {@linkplain RList#distributedIterator(String, int)} for creating different iterators.
     * @param count batch size
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(int count);

    /**
     * Returns iterator over elements that match specified pattern. Iterator can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * Iterator name must be resolved to the same hash slot as list name.
     * @param count batch size
     * @param iteratorName redis object name to which cursor will be saved
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(String iteratorName, int count);

}
