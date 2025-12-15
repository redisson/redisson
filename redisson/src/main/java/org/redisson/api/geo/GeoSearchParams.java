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
package org.redisson.api.geo;

/**
 * @author Nikita Koksharov
 */
public final class GeoSearchParams implements ShapeGeoSearch, OptionalGeoSearch {

    private Object member;
    private Double longitude;
    private Double latitude;
    private Double width;
    private Double height;
    private Double radius;
    private GeoUnit unit;
    private Integer count;
    private boolean countAny;
    private GeoOrder order;

    GeoSearchParams(Object member) {
        this.member = member;
    }

    GeoSearchParams(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    @Override
    public OptionalGeoSearch box(double width, double height, GeoUnit geoUnit) {
        this.width = width;
        this.height = height;
        this.unit = geoUnit;
        return this;
    }

    @Override
    public OptionalGeoSearch radius(double radius, GeoUnit geoUnit) {
        this.radius = radius;
        this.unit = geoUnit;
        return this;
    }

    @Override
    public OptionalGeoSearch count(int value) {
        this.count = value;
        this.countAny = false;
        return this;
    }

    @Override
    public OptionalGeoSearch countAny(int value) {
        this.count = value;
        this.countAny = true;
        return this;
    }

    @Override
    public OptionalGeoSearch order(GeoOrder value) {
        this.order = value;
        return this;
    }

    public Object getMember() {
        return member;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getWidth() {
        return width;
    }

    public Double getHeight() {
        return height;
    }

    public Double getRadius() {
        return radius;
    }

    public GeoUnit getUnit() {
        return unit;
    }

    public Integer getCount() {
        return count;
    }

    public boolean isCountAny() {
        return countAny;
    }

    public GeoOrder getOrder() {
        return order;
    }
}
