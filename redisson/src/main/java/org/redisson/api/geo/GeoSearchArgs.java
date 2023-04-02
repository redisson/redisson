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
package org.redisson.api.geo;

/**
 * Arguments object for RGeo search method.
 * <p>
 * {@link org.redisson.api.RGeo#search(GeoSearchArgs)}
 * {@link org.redisson.api.RGeoAsync#searchAsync(GeoSearchArgs)}
 * {@link org.redisson.api.RGeoRx#search(GeoSearchArgs)}
 * {@link org.redisson.api.RGeoReactive#search(GeoSearchArgs)}
 *
 * @author Nikita Koksharov
 */
public interface GeoSearchArgs {

    /**
     * Defines search from defined member
     *
     * @param member - object
     * @return search conditions object
     */
    static <V> ShapeGeoSearch from(V member) {
        return new GeoSearchParams(member);
    }

    /**
     * Defines search from defined longitude and latitude coordinates
     *
     * @param longitude - longitude of object
     * @param latitude - latitude of object
     * @return search conditions object
     */
    static ShapeGeoSearch from(double longitude, double latitude) {
        return new GeoSearchParams(longitude, latitude);
    }

}
