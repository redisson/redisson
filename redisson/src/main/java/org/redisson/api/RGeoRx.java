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
import org.redisson.api.geo.GeoEntry;
import org.redisson.api.geo.GeoPosition;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoUnit;

import java.util.List;
import java.util.Map;

/**
 * Geospatial items holder. Reactive interface.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RGeoRx<V> extends RScoredSortedSetRx<V> {

    /**
     * Adds geospatial member.
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param member - object itself
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    Single<Long> add(double longitude, double latitude, V member);

    /**
     * Adds geospatial members.
     * 
     * @param entries - objects
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    Single<Long> add(GeoEntry... entries);

    /**
     * Adds geospatial member only if it's already exists.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param member - object itself
     * @return number of elements added to the sorted set
     */
    Single<Long> addIfExists(double longitude, double latitude, V member);

    /**
     * Adds geospatial members only if it's already exists.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param entries - objects
     * @return number of elements added to the sorted set
     */
    Single<Long> addIfExists(GeoEntry... entries);

    /**
     * Adds geospatial member only if has not been added before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param member - object itself
     * @return number of elements added to the sorted set
     */
    Single<Boolean> tryAdd(double longitude, double latitude, V member);

    /**
     * Adds geospatial members only if has not been added before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param entries - objects
     * @return number of elements added to the sorted set
     */
    Single<Long> tryAdd(GeoEntry... entries);

    /**
     * Returns distance between members in <code>GeoUnit</code> units.
     * 
     * @param firstMember - first object
     * @param secondMember - second object
     * @param geoUnit - geo unit
     * @return distance
     */
    Single<Double> dist(V firstMember, V secondMember, GeoUnit geoUnit);
    
    /**
     * Returns 11 characters Geohash string mapped by defined member.
     * 
     * @param members - objects
     * @return hash mapped by object
     */
    Single<Map<V, String>> hash(V... members);

    /**
     * Returns geo-position mapped by defined member.
     * 
     * @param members - objects
     * @return geo position mapped by object
     */
    Single<Map<V, GeoPosition>> pos(V... members);

    /**
     * Returns the members of a sorted set, which are within the
     * borders of specified search conditions.
     * <p>
     * Usage examples:
     * <pre>
     * List objects = geo.search(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)
     *                                 .order(GeoOrder.ASC)
     *                                 .count(1)));
     * </pre>
     * <pre>
     * List objects = geo.search(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)));
     * </pre>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     *
     * @param args - search conditions object
     * @return list of memebers
     */
    Single<List<V>> search(GeoSearchArgs args);

    /**
     * Returns the distance mapped by member of a sorted set,
     * which are within the borders of specified search conditions.
     * <p>
     * Usage examples:
     * <pre>
     * Map objects = geo.searchWithDistance(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)
     *                                 .order(GeoOrder.ASC)
     *                                 .count(1)));
     * </pre>
     * <pre>
     * Map objects = geo.searchWithDistance(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)));
     * </pre>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     *
     * @param args - search conditions object
     * @return distance mapped by object
     */
    Single<Map<V, Double>> searchWithDistance(GeoSearchArgs args);

    /**
     * Returns the position mapped by member of a sorted set,
     * which are within the borders of specified search conditions.
     * <p>
     * Usage examples:
     * <pre>
     * Map objects = geo.searchWithPosition(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)
     *                                 .order(GeoOrder.ASC)
     *                                 .count(1)));
     * </pre>
     * <pre>
     * Map objects = geo.searchWithPosition(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)));
     * </pre>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     *
     * @param args - search conditions object
     * @return position mapped by object
     */
    Single<Map<V, GeoPosition>> searchWithPosition(GeoSearchArgs args);

    /**
     * Finds the members of a sorted set,
     * which are within the borders of specified search conditions.
     * <p>
     * Stores result to <code>destName</code>.
     * <p>
     * Usage examples:
     * <pre>
     * long count = geo.storeSearchTo(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)
     *                                 .order(GeoOrder.ASC)
     *                                 .count(1)));
     * </pre>
     * <pre>
     * long count = geo.storeSearchTo(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)));
     * </pre>
     *
     * @param args - search conditions object
     * @return length of result
     */
    Single<Long> storeSearchTo(String destName, GeoSearchArgs args);

    /**
     * Finds the members of a sorted set,
     * which are within the borders of specified search conditions.
     * <p>
     * Stores result to <code>destName</code> sorted by distance.
     * <p>
     * Usage examples:
     * <pre>
     * long count = geo.storeSortedSearchTo(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)
     *                                 .order(GeoOrder.ASC)
     *                                 .count(1)));
     * </pre>
     * <pre>
     * long count = geo.storeSortedSearchTo(GeoSearchArgs.from(15, 37)
     *                                 .radius(200, GeoUnit.KILOMETERS)));
     * </pre>
     *
     * @param args - search conditions object
     * @return length of result
     */
    RFuture<Long> storeSortedSearchTo(String destName, GeoSearchArgs args);

}
