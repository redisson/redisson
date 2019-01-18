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

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

/**
 * Geospatial items holder. Reactive interface.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RGeoReactive<V> extends RScoredSortedSetReactive<V> {

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
    Mono<Long> add(double longitude, double latitude, V member);

    /**
     * Adds geospatial members.
     * 
     * @param entries - objects
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    Mono<Long> add(GeoEntry... entries);

    /**
     * Returns distance between members in <code>GeoUnit</code> units.
     * 
     * @param firstMember - first object
     * @param secondMember - second object
     * @param geoUnit - geo unit
     * @return distance
     */
    Mono<Double> dist(V firstMember, V secondMember, GeoUnit geoUnit);
    
    /**
     * Returns 11 characters Geohash string mapped by defined member.
     * 
     * @param members - objects
     * @return hash mapped by object
     */
    Mono<Map<V, String>> hash(V... members);

    /**
     * Returns geo-position mapped by defined member.
     * 
     * @param members - objects
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> pos(V... members);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return list of objects
     */
    Mono<List<V>> radius(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return list of objects
     */
    Mono<List<V>> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, int count);

    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - order of result
     * @return list of objects
     */
    Mono<List<V>> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - order of result
     * @param count - result limit
     * @return list of objects
     */
    Mono<List<V>> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);
 
    /**
     * Returns the distance mapped by member, distance between member and the location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit);

    /**
     * Returns the distance mapped by member, distance between member and the location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units and limited by count.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, int count);
    
    /**
     * Returns the distance mapped by member, distance between member and the location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - order of result
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder);
    
    /**
     * Returns the distance mapped by member, distance between member and the location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - order of result
     * @param count - result limit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);
    
    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, int count);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @param count - result limit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return list of objects
     */
    Mono<List<V>> radius(V member, double radius, GeoUnit geoUnit);

    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return list of objects
     */
    Mono<List<V>> radius(V member, double radius, GeoUnit geoUnit, int count);

    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @return list of objects
     */
    Mono<List<V>> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder);

    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @param count - result limit
     * @return list of objects
     */
    Mono<List<V>> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);
    
    /**
     * Returns the distance mapped by member, distance between member and the defined member location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(V member, double radius, GeoUnit geoUnit);

    /**
     * Returns the distance mapped by member, distance between member and the defined member location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(V member, double radius, GeoUnit geoUnit, int count);

    /**
     * Returns the distance mapped by member, distance between member and the defined member location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder);

    /**
     * Returns the distance mapped by member, distance between member and the defined member location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo
     * @param count - result limit
     * @return distance mapped by object
     */
    Mono<Map<V, Double>> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);
    
    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(V member, double radius, GeoUnit geoUnit);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(V member, double radius, GeoUnit geoUnit, int count);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code>
     * and limited by count
     * <p>
     * Requires <b>Redis 3.2.10 and higher.</b>
     * 
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @param count - result limit
     * @return geo position mapped by object
     */
    Mono<Map<V, GeoPosition>> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units. 
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units and limited by count 
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, int count);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code> 
     * and limited by count 
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - order of result
     * @param count - result limit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units. 
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units and limited by count
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param count - result limit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, int count);

    /**
     * Finds the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units with <code>GeoOrder</code> 
     * Store result to <code>destName</code>.
     * 
     * @param destName - Geo object destination
     * @param member - object
     * @param radius - radius in geo units
     * @param geoUnit - geo unit
     * @param geoOrder - geo order
     * @param count - result limit
     * @return length of result
     */
    Mono<Long> radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count);

}
