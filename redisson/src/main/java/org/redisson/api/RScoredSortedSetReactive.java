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

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.client.protocol.RankedEntry;
import org.redisson.client.protocol.ScoredEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Reactive interface for SortedSet object
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RScoredSortedSetReactive<V> extends RExpirableReactive, RSortableReactive<Set<V>> {

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
    Mono<V> pollLastFromAny(long timeout, TimeUnit unit, String... queueNames);

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
    Mono<List<V>> pollLastFromAny(Duration duration, int count, String... queueNames);

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
    Mono<List<V>> pollLastFromAny(int count, String... queueNames);

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
    Mono<Map<String, Map<V, Double>>> pollLastEntriesFromAny(int count, String... queueNames);

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
    Mono<Map<String, Map<V, Double>>> pollLastEntriesFromAny(Duration duration, int count, String... queueNames);

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
    Mono<V> pollFirstFromAny(long timeout, TimeUnit unit, String... queueNames);

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
    Mono<List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames);

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
    Mono<List<V>> pollFirstFromAny(int count, String... queueNames);

    /**
     * Removes and returns first available head entries
     * of <b>any</b> sorted set <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param count entries amount
     * @param queueNames name of queues
     * @return the head elements
     */
    Mono<Map<String, Map<V, Double>>> pollFirstEntriesFromAny(int count, String... queueNames);

    /**
     * Removes and returns first available head entries of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for elements to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @param queueNames name of queues
     * @return the head entries
     */
    Mono<Map<String, Map<V, Double>>> pollFirstEntriesFromAny(Duration duration, int count, String... queueNames);

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
    @Deprecated
    Mono<V> pollFirst(long timeout, TimeUnit unit);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @return the head element,
     *         or {@code null} if this sorted set is empty
     */
    Mono<V> pollFirst(Duration duration);

    /**
     * Removes and returns the head elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @return the head elements
     */
    Mono<List<V>> pollFirst(Duration duration, int count);

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
    @Deprecated
    Mono<V> pollLast(long timeout, TimeUnit unit);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> pollLast(Duration duration);

    /**
     * Removes and returns the tail elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @return the tail elements
     */
    Mono<List<V>> pollLast(Duration duration, int count);

    /**
     * Removes and returns the head elements of this sorted set.
     *
     * @param count - elements amount
     * @return the head elements of this sorted set
     */
    Mono<Collection<V>> pollFirst(int count);

    /**
     * Removes and returns the tail elements of this sorted set.
     *
     * @param count - elements amount
     * @return the tail elements of this sorted set
     */
    Mono<Collection<V>> pollLast(int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    Mono<V> pollFirst();

    /**
     * Removes and returns the head entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the head entry,
     *         or {@code null} if this sorted set is empty
     */
    Maybe<ScoredEntry<V>> pollFirstEntry();

    /**
     * Removes and returns the head entries (value and its score) of this sorted set.
     *
     * @param count entries amount
     * @return the head entries of this sorted set
     */
    Single<List<ScoredEntry<V>>> pollFirstEntries(int count);

    /**
     * Removes and returns the head entries (value and its score).
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @return the head entries
     */
    Single<List<ScoredEntry<V>>> pollFirstEntries(Duration duration, int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> pollLast();

    /**
     * Removes and returns the tail entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the tail entry or {@code null} if this sorted set is empty
     */
    Maybe<ScoredEntry<V>> pollLastEntry();

    /**
     * Removes and returns the tail entries (value and its score) of this sorted set.
     *
     * @param count entries amount
     * @return the tail entries of this sorted set
     */
    Single<List<ScoredEntry<V>>> pollLastEntries(int count);

    /**
     * Removes and returns the head entries (value and its score).
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count entries amount
     * @return the tail entries
     */
    Single<List<ScoredEntry<V>>> pollLastEntries(Duration duration, int count);

    /**
     * Returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element or {@code null} if this sorted set is empty
     */
    Mono<V> first();

    /**
     * Returns the head entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the head entry or {@code null} if this sorted set is empty
     */
    Mono<ScoredEntry<V>> firstEntry();

    /**
     * Returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> last();

    /**
     * Returns the tail entry (value and its score) or {@code null} if this sorted set is empty.
     *
     * @return the tail entry or {@code null} if this sorted set is empty
     */
    Mono<ScoredEntry<V>> lastEntry();

    /**
     * Returns score of the head element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<Double> firstScore();

    /**
     * Returns score of the tail element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<Double> lastScore();

    /**
     * Returns random element from this sorted set
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @return random element
     */
    Mono<V> random();

    /**
     * Returns random elements from this sorted set limited by <code>count</code>
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - values amount to return
     * @return random elements
     */
    Mono<Collection<V>> random(int count);

    /**
     * Returns random entries from this sorted set limited by <code>count</code>.
     * Each map entry uses element as key and score as value.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - entries amount to return
     * @return random entries
     */
    Mono<Map<V, Double>> randomEntries(int count);

    /**
     * Returns an iterator over elements in this set.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flux<V> iterator(String pattern);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(int count);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(String pattern, int count);

    /**
     * Returns an iterator over elements in this set.
     *
     * @return iterator
     */
    Flux<V> iterator();

    /**
     * Returns an iterator over entries (value and its score) in this set.
     *
     * @return iterator
     */
    Flux<ScoredEntry<V>> entryIterator();

    /**
     * Returns an iterator over entries (value and its score) in this set.
     * If <code>pattern</code> is not null then only entries match this pattern are loaded.
     *
     * @param pattern - search pattern
     * @return iterator
     */
    Flux<ScoredEntry<V>> entryIterator(String pattern);

    /**
     * Returns an iterator over entries (value and its score) in this set.
     * Entries are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<ScoredEntry<V>> entryIterator(int count);

    /**
     * Returns an iterator over entries (value and its score) in this set.
     * Entries are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only entries match this pattern are loaded.
     *
     * @param pattern search pattern
     * @param count size of entries batch
     * @return iterator
     */
    Flux<ScoredEntry<V>> entryIterator(String pattern, int count);

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
    Mono<Integer> removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    /**
     * Removes values by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return number of elements removed
     */
    Mono<Integer> removeRangeByRank(int startIndex, int endIndex);

    /**
     * Returns rank of value, with the scores ordered from low to high.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    Mono<Integer> rank(V o);

    /**
     * Returns rank and score of specified <code>value</code>,
     * with the ranks ordered from low to high.
     *
     * @param value object
     * @return ranked entry or <code>null</code> if value does not exist
     */
    Mono<RankedEntry<V>> rankEntry(V value);

    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    Mono<Integer> revRank(V o);

    /**
     * Returns rank and score of specified <code>value</code>,
     * with the ranks ordered from high to low.
     *
     * @param value object
     * @return ranked entry or <code>null</code> if value does not exist
     */
    Mono<RankedEntry<V>> revRankEntry(V value);

    /**
     * Returns ranks of elements, with the scores ordered from high to low.
     *
     * @param elements - elements
     * @return ranks or <code>null</code> if value does not exist
     */
    Mono<List<Integer>> revRank(Collection<V> elements);

    /**
     * Returns score of element or <code>null</code> if it doesn't exist.
     * 
     * @param o - element
     * @return score
     */
    Mono<Double> getScore(V o);

    /**
     * Returns scores of elements.
     *
     * @param elements - elements
     * @return element scores
     */
    Mono<List<Double>> getScore(Collection<V> elements);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    Mono<Boolean> add(double score, V object);

    /**
     * Adds all elements contained in the specified map to this sorted set.
     * Map contains of score mapped by object. 
     * 
     * @param objects - map of elements to add
     * @return amount of added elements, not including already existing in this sorted set
     */
    Mono<Integer> addAll(Map<V, Double> objects);

    /**
     * Adds elements to this set only if they haven't been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Mono<Integer> addAllIfAbsent(Map<V, Double> objects);

    /**
     * Adds elements to this set only if they already exist.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Mono<Integer> addAllIfExist(Map<V, Double> objects);

    /**
     * Adds elements to this set only if new scores greater than current score of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Mono<Integer> addAllIfGreater(Map<V, Double> objects);

    /**
     * Adds elements to this set only if new scores less than current score of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Mono<Integer> addAllIfLess(Map<V, Double> objects);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the rank of the item
     * 
     * @param score - object score
     * @param object - object itself
     * @return rank
     */
    Mono<Integer> addAndGetRank(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the reverse rank of the item
     * 
     * @param score - object score
     * @param object - object itself
     * @return reverse rank
     */
    Mono<Integer> addAndGetRevRank(double score, V object);

    /**
     * Adds elements to this set, overrides previous score if it has been already added.
     * Finally returns reverse rank list of the items
     * @param map - map of object and scores, make sure to use an ordered map
     * @return collection of reverse ranks
     */
    Mono<List<Integer>> addAndGetRevRank(Map<? extends V, Double> map);

    /**
     * Use {@link #addIfAbsent(double, Object)} instead
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    @Deprecated
    Mono<Boolean> tryAdd(double score, V object);

    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Mono<Boolean> addIfAbsent(double score, V object);

    /**
     * Adds element to this set only if it's already exists.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Mono<Boolean> addIfExists(double score, V object);

    /**
     * Adds element to this set only if new score less than current score of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Mono<Boolean> addIfLess(double score, V object);

    /**
     * Adds element to this set only if new score greater than current score of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Mono<Boolean> addIfGreater(double score, V object);

    /**
     * Replaces a previous <code>oldObject</code> with a <code>newObject</code>.
     * Returns <code>false</code> if previous object doesn't exist.
     *
     * @param oldObject old object
     * @param newObject new object
     * @return <code>true</code> if object has been replaced otherwise <code>false</code>.
     */
    Mono<Boolean> replace(V oldObject, V newObject);

    /**
     * Removes a single instance of the specified element from this
     * sorted set, if it is present.
     *
     * @param object element to be removed from this sorted set, if present
     * @return <code>true</code> if an element was removed as a result of this call
     */
    Mono<Boolean> remove(V object);

    /**
     * Returns size of this set.
     * 
     * @return size
     */
    Mono<Integer> size();

    /**
     * Returns <code>true</code> if this sorted set contains encoded state of the specified element.
     *
     * @param o element whose presence in this collection is to be tested
     * @return <code>true</code> if this sorted set contains the specified
     *         element and <code>false</code> otherwise
     */
    Mono<Boolean> contains(V o);

    /**
     * Returns <code>true</code> if this sorted set contains all of the elements
     * in encoded state in the specified collection.
     *
     * @param  c collection to be checked for containment in this sorted set
     * @return <code>true</code> if this sorted set contains all of the elements
     *         in the specified collection
     */
    Mono<Boolean> containsAll(Collection<?> c);

    /**
     * Removes all of this sorted set's elements that are also contained in the
     * specified collection.
     *
     * @param c sorted set containing elements to be removed from this collection
     * @return <code>true</code> if this sorted set changed as a result of the
     *         call
     */
    Mono<Boolean> removeAll(Collection<?> c);

    /**
     * Retains only the elements in this sorted set that are contained in the
     * specified collection.
     *
     * @param c collection containing elements to be retained in this collection
     * @return <code>true</code> if this sorted set changed as a result of the call
     */
    Mono<Boolean> retainAll(Collection<?> c);

    /**
     * Increases score of specified element by value.
     * 
     * @param element - element whose score needs to be increased
     * @param value - value
     * @return updated score of element
     */
    Mono<Double> addScore(V element, Number value);

    /**
     * Adds score to element and returns its reverse rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return reverse rank
     */
    Mono<Integer> addScoreAndGetRevRank(V object, Number value);
    
    /**
     * Adds score to element and returns its rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return rank
     */
    Mono<Integer> addScoreAndGetRank(V object, Number value);
    
    /**
     * Returns values by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return elements
     */
    Mono<Collection<V>> valueRange(int startIndex, int endIndex);

    /**
     * Returns entries (value and its score) by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return entries
     */
    Mono<Collection<ScoredEntry<V>>> entryRange(int startIndex, int endIndex);

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
    Mono<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    Mono<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    Mono<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

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
    Mono<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns values by rank range in reverse order. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return elements
     */
    Mono<Collection<V>> valueRangeReversed(int startIndex, int endIndex);
    
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
    Mono<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    Mono<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    /**
     * Returns entries (value and its score) by rank range in reverse order. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return entries
     */
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(int startIndex, int endIndex);

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
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
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
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     * @param endScoreInclusive - end score inclusive
     * @return count
     */
    Mono<Integer> count(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return values
     */
    Mono<Collection<V>> readAll();

    /**
     * Stores to defined ScoredSortedSet values by rank range. Indexes are zero based.
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startIndex - start index
     * @param endIndex - end index
     * @return elements
     */
    Mono<Integer> rangeTo(String destName, int startIndex, int endIndex);

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
    Mono<Integer> rangeTo(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    Mono<Integer> rangeTo(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Stores to defined ScoredSortedSet values in reversed order by rank range. Indexes are zero based.
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param startIndex - start index
     * @param endIndex - end index
     * @return elements
     */
    Mono<Integer> revRangeTo(String destName, int startIndex, int endIndex);

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
    Mono<Integer> revRangeTo(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    Mono<Integer> revRangeTo(String destName, double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Use {@link #intersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    @Deprecated
    Mono<Integer> intersection(String... names);

    /**
     * Use {@link #intersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    @Deprecated
    Mono<Integer> intersection(Aggregate aggregate, String... names);

    /**
     * Use {@link #intersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    @Deprecated
    Mono<Integer> intersection(Map<String, Double> nameWithWeight);

    /**
     * Use {@link #intersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    @Deprecated
    Mono<Integer> intersection(Aggregate aggregate, Map<String, Double> nameWithWeight);
    
    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier
     * with defined aggregation method
     * and store result to current ScoredSortedSet
     *
     * @param args object
     * @return length of intersection
     */
    Mono<Integer> intersection(SetIntersectionArgs args);

    /**
     * Use {@link #readIntersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets
     * with current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - names of ScoredSortedSet
     * @return result of intersection
     */
    @Deprecated
    Mono<Collection<V>> readIntersection(String... names);

    /**
     * Use {@link #readIntersection(SetIntersectionArgs)} instead.
     * <p>
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
    Mono<Collection<V>> readIntersection(Aggregate aggregate, String... names);

    /**
     * Use {@link #readIntersection(SetIntersectionArgs)} instead.
     * <p>
     * Intersect provided ScoredSortedSets mapped to weight multiplier
     * with current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of intersection
     */
    @Deprecated
    Mono<Collection<V>> readIntersection(Map<String, Double> nameWithWeight);

    /**
     * Use {@link #readIntersection(SetIntersectionArgs)} instead.
     * <p>
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
    Mono<Collection<V>> readIntersection(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets
     * with current ScoredSortedSet
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args object
     * @return result of intersection
     */
    Mono<Collection<V>> readIntersection(SetIntersectionArgs args);

    /**
     * Intersect provided ScoredSortedSets
     * with current ScoredSortedSet
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args object
     * @return result of intersection entries (value and its score)
     */
    Mono<Collection<ScoredEntry<V>>> readIntersectionEntries(SetIntersectionArgs args);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    Mono<Integer> countIntersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    Mono<Integer> countIntersection(int limit, String... names);

    /**
     * Use {@link #union(SetUnionArgs)} instead.
     * <p>
     * Union provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    @Deprecated
    Mono<Integer> union(String... names);

    /**
     * Use {@link #union(SetUnionArgs)} instead.
     * <p>
     * Union provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    @Deprecated
    Mono<Integer> union(Aggregate aggregate, String... names);

    /**
     * Use {@link #union(SetUnionArgs)} instead.
     * <p>
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    @Deprecated
    Mono<Integer> union(Map<String, Double> nameWithWeight);

    /**
     * Use {@link #union(SetUnionArgs)} instead.
     * <p>
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    @Deprecated
    Mono<Integer> union(Aggregate aggregate, Map<String, Double> nameWithWeight);
    
    /**
     * Union provided ScoredSortedSets mapped to weight multiplier
     * with defined aggregation method
     * and store result to current ScoredSortedSet
     *
     * @param args object
     * @return length of union
     */
    Mono<Integer> union(SetUnionArgs args);
    /**
     * Use {@link #readUnion(SetUnionArgs)} instead.
     * <p>
     * Union ScoredSortedSets specified by name with current ScoredSortedSet
     * without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - names of ScoredSortedSet
     * @return result of union
     */
    @Deprecated
    Mono<Collection<V>> readUnion(String... names);

    /**
     * Use {@link #readUnion(SetUnionArgs)} instead.
     * <p>
     * Union ScoredSortedSets specified by name with defined aggregation method
     * and current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return result of union
     */
    @Deprecated
    Mono<Collection<V>> readUnion(Aggregate aggregate, String... names);

    /**
     * Use {@link #readUnion(SetUnionArgs)} instead.
     * <p>
     * Union provided ScoredSortedSets mapped to weight multiplier
     * and current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return result of union
     */
    @Deprecated
    Mono<Collection<V>> readUnion(Map<String, Double> nameWithWeight);

    /**
     * Use {@link #readUnion(SetUnionArgs)} instead.
     * <p>
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
    @Deprecated
    Mono<Collection<V>> readUnion(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier
     * with defined aggregation method
     * and current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args object
     * @return result of union
     */
    Mono<Collection<V>> readUnion(SetUnionArgs args);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier
     * with defined aggregation method
     * and current ScoredSortedSet without state change
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args object
     * @return result of union entries (value and its score)
     */
    Mono<Collection<ScoredEntry<V>>> readUnionEntries(SetUnionArgs args);

    /**
     * Diff ScoredSortedSets specified by name
     * with current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return result of diff
     */
    Mono<Collection<V>> readDiff(String... names);

    /**
     * Diff ScoredSortedSets specified by name
     * with current ScoredSortedSet without state change.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return result of diff entries (value and its score)
     */
    Mono<Collection<ScoredEntry<V>>> readDiffEntries(String... names);


    /**
     * Diff provided ScoredSortedSets
     * and store result to current ScoredSortedSet
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param names - name of sets
     * @return length of diff
     */
    Mono<Integer> diff(String... names);

    /**
     * Removes and returns the head element waiting if necessary for an element to become available.
     *
     * @return the head element
     */
    Mono<V> takeFirst();

    /**
     * Removes and returns the tail element waiting if necessary for an element to become available.
     *
     * @return the tail element
     */
    Mono<V> takeLast();

    /**
     * Retrieves and removes continues stream of elements from the head of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of head elements
     */
    Flux<V> takeFirstElements();
    
    /**
     * Retrieves and removes continues stream of elements from the tail of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of tail elements
     */
    Flux<V> takeLastElements();

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.ScoredSortedSetAddListener
     * @see org.redisson.api.listener.ScoredSortedSetRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    Mono<Integer> addListener(ObjectListener listener);

}
