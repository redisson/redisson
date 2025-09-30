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
package org.redisson.api.search.index;

/**
 * GeoShape field index options.
 *
 * @author seakider
 *
 */
public interface GeoShapeIndex extends FieldIndex {
    enum CoordinateSystems {SPHERICAL, FLAT}

    /**
     * Defines the attribute associated to the field name
     *
     * @param as the associated attribute
     * @return options object
     */
    GeoShapeIndex as(String as);

    /**
     * Defines the coordinate systems to the field name
     *
     * @param coordinateSystems the coordinate systems
     * @return options object
     */
    GeoShapeIndex coordinateSystems(CoordinateSystems coordinateSystems);

    /**
     * Defines to not index this attribute
     *
     * @return options object
     */
    GeoShapeIndex noIndex();

    /**
     * Defines to index documents that don't contain this attribute
     *
     * @return options object
     */
    GeoShapeIndex indexMissing();
}