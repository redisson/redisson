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

import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.client.protocol.RankedEntry;
import org.redisson.client.protocol.ScoredEntry;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RScoredSortedSetAsync<V> extends RExpirableAsync, RSortableAsync<Set<V>> {

    /**
     * Removes and returns first available tail element of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param queueNames name of queues
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail element, or {@code null} if all sorted sets are empty 
     */
    RFuture<V> pollLastFromAnyAsync(long timeout, TimeUnit unit, String... queueNames);

    /**
     * Removes and returns first available tail elements of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for elements to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the tail elements
     */
    RFuture<List<V>> pollLastFromAnyAsync(Duration duration, int count, String... queueNames);

    /**
     * Removes and returns first available tail elements
     * of <b>any</b> sorted set <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param count elements amount
     * @param queueNames name of queues
     * @return the tail elements
     */
    RFuture<List<V>> pollLastFromAnyAsync(int count, String... queueNames);

    /**
     * Removes and returns first available tail entries
     * of <b>any</b> sorted set <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param count entries amount
     * @param queueNames name of queues
     * @return the head entries
     */
    RFuture<Map<String, Map<V, Double>>> pollLastEntriesFromAnyAsync(int count, String... queueNames);

    /**
     * Removes and returns first available tail entries of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for elements to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @param queueNames name of queues
     * @return the tail entries
     */
    RFuture<Map<String, Map<V, Double>>> pollLastEntriesFromAnyAsync(Duration duration, int count, String... queueNames);

    /**
     * Removes and returns first available head element of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param queueNames name of queues
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head element, or {@code null} if all sorted sets are empty
     *  
     */
    RFuture<V> pollFirstFromAnyAsync(long timeout, TimeUnit unit, String... queueNames);

    /**
     * Removes and returns first available head elements of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for elements to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    RFuture<List<V>> pollFirstFromAnyAsync(Duration duration, int count, String... queueNames);

    /**
     * Removes and returns first available head elements
     * of <b>any</b> sorted set <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    RFuture<List<V>> pollFirstFromAnyAsync(int count, String... queueNames);

    /**
     * Removes and returns first available head elements
     * of <b>any</b> sorted set <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    RFuture<Map<String, Map<V, Double>>> pollFirstEntriesFromAnyAsync(int count, String... queueNames);

    /**
     * Removes and returns first available head elements of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for elements to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    RFuture<Map<String, Map<V, Double>>> pollFirstEntriesFromAnyAsync(Duration duration, int count, String... queueNames);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    RFuture<V> pollFirstAsync(long timeout, TimeUnit unit);

    /**
     * Removes and returns the head elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the head elements
     */
    RFuture<List<V>> pollFirstAsync(Duration duration, int count);

    /**
     * Removes and returns the head element waiting if necessary for an element to become available.
     *
     * @return the head element
     */
    RFuture<V> takeFirstAsync();

    /**
     * Removes and returns the tail element waiting if necessary for an element to become available.
     *
     * @return the tail element
     */
    RFuture<V> takeLastAsync();
    
    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<V> pollLastAsync(long timeout, TimeUnit unit);

    /**
     * Removes and returns the tail elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the tail elements
     */
    RFuture<List<V>> pollLastAsync(Duration duration, int count);

    /**
     * Removes and returns the head elements of this sorted set.
     *
     * @param count - elements amount
     * @return the head elements of this sorted set
     */
    RFuture<Collection<V>> pollFirstAsync(int count);

    /**
     * Removes and returns the tail elements of this sorted set.
     *
     * @param count - elements amount
     * @return the tail elements of this sorted set
     */
    RFuture<Collection<V>> pollLastAsync(int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    RFuture<V> pollFirstAsync();

    /**
     * Removes and returns the head entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the head entry,
     *         or {@code null} if this sorted set is empty
     */
    RFuture<ScoredEntry<V>> pollFirstEntryAsync();

    /**
     * Removes and returns the head entries (value and its score) of this sorted set.
     *
     * @param count entries amount
     * @return the head entries of this sorted set
     */
    RFuture<List<ScoredEntry<V>>> pollFirstEntriesAsync(int count);

    /**
     * Removes and returns the head entries (value and its score).
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @return the head entries
     */
    RFuture<List<ScoredEntry<V>>> pollFirstEntriesAsync(Duration duration, int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<V> pollLastAsync();

    /**
     * Removes and returns the tail entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the tail entry or {@code null} if this sorted set is empty
     */
    RFuture<ScoredEntry<V>> pollLastEntryAsync();

    /**
     * Removes and returns the tail entries (value and its score) of this sorted set.
     *
     * @param count entries amount
     * @return the tail entries of this sorted set
     */
    RFuture<List<ScoredEntry<V>>> pollLastEntriesAsync(int count);

    /**
     * Removes and returns the head entries (value and its score).
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @return the tail entries
     */
    RFuture<List<ScoredEntry<V>>> pollLastEntriesAsync(Duration duration, int count);

    /**
     * Returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element or {@code null} if this sorted set is empty
     */
    RFuture<V> firstAsync();

    /**
     * Returns the head entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the head entry or {@code null} if this sorted set is empty
     */
    RFuture<ScoredEntry<V>> firstEntryAsync();

    /**
     * Returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<V> lastAsync();

    /**
     * Returns the tail entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the tail entry or {@code null} if this sorted set is empty
     */
    RFuture<ScoredEntry<V>> lastEntryAsync();

    /**
     * Returns score of the head element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<Double> firstScoreAsync();

    /**
     * Returns score of the tail element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<Double> lastScoreAsync();

    /**
     * Returns random element from this sorted set
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @return value
     */
    RFuture<V> randomAsync();

    /**
     * Returns random elements from this sorted set limited by <code>count</code>
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - values amount to return
     * @return value
     */
    RFuture<Collection<V>> randomAsync(int count);

    /**
     * Returns random entries from this sorted set limited by <code>count</code>.
     * Each map entry uses element as key and score as value.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - entries amount to return
     * @return random entries
     */
    RFuture<Map<V, Double>> randomEntriesAsync(int count);

    /**
     * Adds all elements contained in the specified map to this sorted set.
     * Map contains of score mapped by object. 
     * 
     * @param objects - map of elements to add
     * @return amount of added elements, not including already existing in this sorted set
     */
    RFuture<Integer> addAllAsync(Map<V, Double> objects);

    /**
     * Adds elements to this set only if they haven't been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfAbsentAsync(Map<V, Double> objects);

    /**
     * Adds elements to this set only if they already exist.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfExistAsync(Map<V, Double> objects);

    /**
     * Adds elements to this set only if new scores greater than current score of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfGreaterAsync(Map<V, Double> objects);

    /**
     * Adds elements to this set only if new scores less than current score of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfLessAsync(Map<V, Double> objects);

    /**
     * Removes values by score range.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return number of elements removed
     */
    RFuture<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Removes values by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return number of elements removed
     */
    RFuture<Integer> removeRangeByRankAsync(int startIndex, int endIndex);

    /**
     * Returns rank of value, with the scores ordered from low to high.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    RFuture<Integer> rankAsync(V o);

    /**
     * Returns rank and score of specified <code>value</code>,
     * with the ranks ordered from low to high.
     *
     * @param value object
     * @return ranked entry or <code>null</code> if value does not exist
     */
    RFuture<RankedEntry<V>> rankEntryAsync(V value);

    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    RFuture<Integer> revRankAsync(V o);

    /**
     * Returns rank and score of specified <code>value</code>,
     * with the ranks ordered from high to low.
     *
     * @param value object
     * @return ranked entry or <code>null</code> if value does not exist
     */
    RFuture<RankedEntry<V>> revRankEntryAsync(V value);

    /**
     * Returns ranks of elements, with the scores ordered from high to low.
     *
     * @param elements - elements
     * @return ranks or <code>null</code> if value does not exist
     */
    RFuture<List<Integer>> revRankAsync(Collection<V> elements);

    /**
     * Returns score of element or <code>null</code> if it doesn't exist.
     * 
     * @param o - element
     * @return score
     */
    RFuture<Double> getScoreAsync(V o);

    /**
     * Returns scores of elements.
     *
     * @param elements - elements
     * @return element scores
     */
    RFuture<List<Double>> getScoreAsync(Collection<V> elements);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    RFuture<Boolean> addAsync(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the rank of the item
     * @param score - object score
     * @param object - object itself
     * @return rank
     */
    RFuture<Integer> addAndGetRankAsync(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the reverse rank of the item
     * @param score - object score
     * @param object - object itself
     * @return reverse rank
     */
    RFuture<Integer> addAndGetRevRankAsync(double score, V object);

    /**
     * Adds elements to this set, overrides previous score if it has been already added.
     * Finally returns reverse rank list of the items
     * @param map - map of object and scores, make sure to use an ordered map
     * @return collection of reverse ranks
     */
    RFuture<List<Integer>> addAndGetRevRankAsync(Map<? extends V, Double> map);

    /**
     * Use {@link #addIfAbsentAsync(double, Object)} instead
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    @Deprecated
    RFuture<Boolean> tryAddAsync(double score, V object);

    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfAbsentAsync(double score, V object);

    /**
     * Adds element to this set only if it's already exists.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfExistsAsync(double score, V object);

    /**
     * Adds element to this set only if new score less than current score of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfLessAsync(double score, V object);

    /**
     * Adds element to this set only if new score greater than current score of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfGreaterAsync(double score, V object);

    /**
     * Removes a single instance of the specified element from this
     * sorted set, if it is present.
     *
     * @param o element to be removed from this sorted set, if present
     * @return <code>true</code> if an element was removed as a result of this call
     */
    RFuture<Boolean> removeAsync(V o);

    /**
     * Replaces a previous <code>oldObject</code> with a <code>newObject</code>.
     * Returns <code>false</code> if previous object doesn't exist.
     *
     * @param oldObject old object
     * @param newObject new object
     * @return <code>true</code> if object has been replaced otherwise <code>false</code>.
     */
    RFuture<Boolean> replaceAsync(V oldObject, V newObject);

    /**
     * Returns size of this set.
     * 
     * @return size
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns <code>true</code> if this sorted set contains encoded state of the specified element.
     *
     * @param o element whose presence in this collection is to be tested
     * @return <code>true</code> if this sorted set contains the specified
     *         element and <code>false</code> otherwise
     */
    RFuture<Boolean> containsAsync(Object o);

    /**
     * Returns <code>true</code> if this sorted set contains all of the elements
     * in encoded state in the specified collection.
     *
     * @param  c collection to be checked for containment in this sorted set
     * @return <code>true</code> if this sorted set contains all of the elements
     *         in the specified collection
     */
    RFuture<Boolean> containsAllAsync(Collection<?> c);

    /**
     * Removes all of this sorted set's elements that are also contained in the
     * specified collection.
     *
     * @param c sorted set containing elements to be removed from this collection
     * @return <code>true</code> if this sorted set changed as a result of the
     *         call
     */
    RFuture<Boolean> removeAllAsync(Collection<?> c);

    /**
     * Retains only the elements in this sorted set that are contained in the
     * specified collection.
     *
     * @param c collection containing elements to be retained in this collection
     * @return <code>true</code> if this sorted set changed as a result of the call
     */
    RFuture<Boolean> retainAllAsync(Collection<?> c);

    /**
     * Increases score of specified element by value.
     * 
     * @param element - element whose score needs to be increased
     * @param value - value
     * @return updated score of element
     */
    RFuture<Double> addScoreAsync(V element, Number value);

    /**
     * Adds score to element and returns its reverse rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return reverse rank
     */
    RFuture<Integer> addScoreAndGetRevRankAsync(V object, Number value);
    
    /**
     * Adds score to element and returns its rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return rank
     */
    RFuture<Integer> addScoreAndGetRankAsync(V object, Number value);

    /**
     * Stores to defined ScoredSortedSet values by rank range. Indexes are zero based.
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     *
     * @param startIndex - start index
     * @param endIndex - end index
     * @return elements
     */
    RFuture<Integer> rangeToAsync(String destName, int startIndex, int endIndex);

    /**
     * Stores to defined ScoredSortedSet values between <code>startScore</code> and <code>endScore</code>.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startScore - start score.
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     *
     * @param endScoreInclusive - end score inclusive
     * @return values
     */
    RFuture<Integer> rangeToAsync(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Stores to defined ScoredSortedSet values between <code>startScore</code> and <code>endScore</code>.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startScore - start score.
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     *
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return values
     */
    RFuture<Integer> rangeToAsync(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Stores to defined ScoredSortedSet values in reversed order by rank range. Indexes are zero based.
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     *
     * @param startIndex - start index
     * @param endIndex - end index
     * @return elements
     */
    RFuture<Integer> revRangeToAsync(String destName, int startIndex, int endIndex);

    /**
     * Stores to defined ScoredSortedSet values in reversed order between <code>startScore</code> and <code>endScore</code>.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startScore - start score.
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     *
     * @param endScoreInclusive - end score inclusive
     * @return values
     */
    RFuture<Integer> revRangeToAsync(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Stores to defined ScoredSortedSet values in reversed order between <code>startScore</code> and <code>endScore</code>.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startScore - start score.
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code>
     *                     to define infinity numbers
     *
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return values
     */
    RFuture<Integer> revRangeToAsync(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns values by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return elements
     */
    RFuture<Collection<V>> valueRangeAsync(int startIndex, int endIndex);

    /**
     * Returns values by rank range in reverse order. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return elements
     */
    RFuture<Collection<V>> valueRangeReversedAsync(int startIndex, int endIndex);

    /**
     * Returns entries (value and its score) by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex);
    
    /**
     * Returns entries (value and its score) by rank range in reverse order. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(int startIndex, int endIndex);

    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return values
     */
    RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code> in reversed order.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return values
     */
    RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Returns all entries (value and its score) between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return values
     */
    RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code> in reversed order.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return values
     */
    RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns all entries (value and its score) between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns all entries (value and its score) between <code>startScore</code> and <code>endScore</code> in reversed order.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Returns all entries (value and its score) between <code>startScore</code> and <code>endScore</code> in reversed order.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @param offset - offset of sorted data
     * @param count - amount of sorted data
     * @return entries
     */
    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     * @param endScoreInclusive - end score inclusive
     * @return count
     */
    RFuture<Integer> countAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return values
     */
    RFuture<Collection<V>> readAllAsync();

    /**
     * Intersect provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(String... names);

    /**
     * Intersect provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Aggregate aggregate, String... names);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets
     * with current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - names of ScoredSortedSet
     * @return result of intersection
     */
    @Deprecated
    RFuture<Collection<V>> readIntersectionAsync(String... names);

    /**
     * Intersect provided ScoredSortedSets with current ScoredSortedSet using defined aggregation method
     * without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return result of intersection
     */
    @Deprecated
    RFuture<Collection<V>> readIntersectionAsync(Aggregate aggregate, String... names);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier
     * with current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of intersection
     */
    @Deprecated
    RFuture<Collection<V>> readIntersectionAsync(Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier
     * with current ScoredSortedSet using defined aggregation method
     * without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of intersection
     */
    @Deprecated
    RFuture<Collection<V>> readIntersectionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets
     * with current ScoredSortedSet
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args object
     * @return result of intersection
     */
    RFuture<Collection<V>> readIntersectionAsync(SetIntersectionArgs args);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    RFuture<Integer> countIntersectionAsync(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    RFuture<Integer> countIntersectionAsync(int limit, String... names);

    /**
     * Union provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    RFuture<Integer> unionAsync(String... names);

    /**
     * Union provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    RFuture<Integer> unionAsync(Aggregate aggregate, String... names);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    RFuture<Integer> unionAsync(Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    RFuture<Integer> unionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Union ScoredSortedSets specified by name with current ScoredSortedSet
     * without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - names of ScoredSortedSet
     * @return result of union
     */
    RFuture<Collection<V>> readUnionAsync(String... names);

    /**
     * Union ScoredSortedSets specified by name with defined aggregation method
     * and current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return result of union
     */
    RFuture<Collection<V>> readUnionAsync(Aggregate aggregate, String... names);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier
     * and current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of union
     */
    RFuture<Collection<V>> readUnionAsync(Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier
     * with defined aggregation method
     * and current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of union
     */
    RFuture<Collection<V>> readUnionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Diff ScoredSortedSets specified by name
     * with current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return result of diff
     */
    RFuture<Collection<V>> readDiffAsync(String... names);

    /**
     * Diff ScoredSortedSets specified by name
     * with current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return result of diff entries (value and its score)
     */
    RFuture<Collection<ScoredEntry<V>>> readDiffEntriesAsync(String... names);

    /**
     * Diff provided ScoredSortedSets
     * and store result to current ScoredSortedSet
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return length of diff
     */
    RFuture<Integer> diffAsync(String... names);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.ScoredSortedSetAddListener
     * @see org.redisson.api.listener.ScoredSortedSetRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    @Override
    RFuture<Integer> addListenerAsync(ObjectListener listener);

}
