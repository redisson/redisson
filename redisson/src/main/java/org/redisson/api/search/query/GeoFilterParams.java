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
package org.redisson.api.search.query;

import org.redisson.api.geo.GeoUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class GeoFilterParams implements GeoFilter,
                                              GeoFilterRadius,
                                              QueryFilter {

    private String fieldName;
    private double longitude;
    private double latitude;
    private double radius;
    private GeoUnit unit;

    GeoFilterParams(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public GeoFilterRadius from(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        return this;
    }

    @Override
    public QueryFilter radius(double radius, GeoUnit geoUnit) {
        this.radius = radius;
        this.unit = geoUnit;
        return this;
    }

    public String getFieldName() {
        return fieldName;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getRadius() {
        return radius;
    }

    public GeoUnit getUnit() {
        return unit;
    }
}
