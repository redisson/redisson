/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
 *
 * @author Nikita Koksharov
 */
public interface OptionalGeoSearch extends GeoSearchArgs {

    /**
     * Defines limit of search result
     *
     * @param value - result limit
     * @return search conditions object
     */
    OptionalGeoSearch count(int value);

    /**
     * Defines limit of search result.
     * Returns as soon as enough matches are found.
     * Result size might be not closest to defined limit,
     * but works faster.
     *
     * @param value - result limit
     * @return search conditions object
     */
    OptionalGeoSearch countAny(int value);

    /**
     * Defines order of search result
     *
     * @param geoOrder - result order
     * @return search conditions object
     */
    OptionalGeoSearch order(GeoOrder geoOrder);

}
