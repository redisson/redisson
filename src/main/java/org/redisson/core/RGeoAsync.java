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

import io.netty.util.concurrent.Future;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public interface RGeoAsync<V> extends RExpirableAsync {

    Future<Long> addAsync(double longitude, double latitude, V member);

    Future<Long> addAsync(GeoEntry... entries);

    Future<Double> distAsync(V firstMember, V secondMember, GeoUnit geoUnit);
    
    Future<Map<V, String>> hashAsync(V... members);

    Future<Map<V, GeoPosition>> posAsync(V... members);
    
    Future<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);
 
    Future<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);

    Future<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit);
    
    Future<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit);
    
    Future<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit);

    Future<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit);
    
}
