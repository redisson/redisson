/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.core;

import java.util.List;
import java.util.Map;

import io.netty.util.concurrent.Future;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public interface RGeoAsync<V> extends RExpirableAsync {

    /**
     * Adds geospatial member.
     * 
     * @param entries
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    Future<Long> addAsync(double longitude, double latitude, V member);

    /**
     * Adds geospatial members.
     * 
     * @param entries
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    Future<Long> addAsync(GeoEntry... entries);

    /**
     * Returns distance between members in <code>GeoUnit</code> units.
     * 
     * @see {@link GeoUnit}
     * 
     * @param firstMember
     * @param secondMember
     * @param geoUnit
     * @return
     */
    Future<Double> distAsync(V firstMember, V secondMember, GeoUnit geoUnit);
    
    /**
     * Returns 11 characters Geohash string mapped by defined member.
     * 
     * @param members
     * @return
     */
    Future<Map<V, String>> hashAsync(V... members);

    /**
     * Returns geo-position mapped by defined member.
     * 
     * @param members
     * @return
     */
    Future<Map<V, GeoPosition>> posAsync(V... members);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);
 
    /**
     * Returns the distance mapped by member, distance between member and the location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the center location 
     * and the maximum distance from the center (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    /**
     * Returns the members of a sorted set, which are within the 
     * borders of the area specified with the defined member location
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit);
    
    /**
     * Returns the distance mapped by member, distance between member and the defined member location. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit);

    /**
     * Returns the geo-position mapped by member. 
     * Members of a sorted set, which are within the 
     * borders of the area specified with the defined member location 
     * and the maximum distance from the defined member location (the radius) 
     * in <code>GeoUnit</code> units.
     * 
     * @param longitude
     * @param latitude
     * @param radius
     * @param geoUnit
     * @return
     */
    Future<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit);
    
}
