/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
 * 
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public interface RGeo<V> extends RExpirable, RGeoAsync<V> {

    long add(double longitude, double latitude, V member);
    
    long add(GeoEntry... entries);
    
    Double dist(V firstMember, V secondMember, GeoUnit geoUnit);

    Map<V, String> hash(V... members);
    
    Map<V, GeoPosition> pos(V... members);
    
    List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    List<V> radius(V member, double radius, GeoUnit geoUnit);
    
    Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit);
    
    Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit);
    
}
