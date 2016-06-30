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

/**
 * Geospatial items holder 
 * 
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public interface RGeo<V> extends RExpirable, RGeoAsync<V> {

    /**
     * Adds geospatial member.
     * 
     * @param entries
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    long add(double longitude, double latitude, V member);
    
    /**
     * Adds geospatial members.
     * 
     * @param entries
     * @return number of elements added to the sorted set, 
     * not including elements already existing for which 
     * the score was updated
     */
    long add(GeoEntry... entries);
    
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
    Double dist(V firstMember, V secondMember, GeoUnit geoUnit);

    /**
     * Returns 11 characters Geohash string mapped by defined member.
     * 
     * @param members
     * @return
     */
    Map<V, String> hash(V... members);

    /**
     * Returns geo-position mapped by defined member.
     * 
     * @param members
     * @return
     */
    Map<V, GeoPosition> pos(V... members);
    
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
    List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit);

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
    Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit);

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
    Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit);

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
    List<V> radius(V member, double radius, GeoUnit geoUnit);

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
    Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit);

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
    Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit);
    
}
