/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.GeoUnit;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Nikita Koksharov
 */
class BaseGeoSearch implements ShapeGeoSearch {

    private final Map<GeoSearchNode.Params, Object> params = new EnumMap<>(GeoSearchNode.Params.class);

    BaseGeoSearch(Object member) {
        params.put(GeoSearchNode.Params.MEMBER, member);
    }

    BaseGeoSearch(double longitude, double latitude) {
        params.put(GeoSearchNode.Params.LONGITUDE, longitude);
        params.put(GeoSearchNode.Params.LATITUDE, latitude);
    }

    @Override
    public OptionalGeoSearch box(double width, double height, GeoUnit geoUnit) {
        params.put(GeoSearchNode.Params.WIDTH, width);
        params.put(GeoSearchNode.Params.HEIGHT, height);
        params.put(GeoSearchNode.Params.UNIT, geoUnit);
        return new BaseOptionalGeoSearch(params);
    }

    @Override
    public OptionalGeoSearch radius(double radius, GeoUnit geoUnit) {
        params.put(GeoSearchNode.Params.RADIUS, radius);
        params.put(GeoSearchNode.Params.UNIT, geoUnit);
        return new BaseOptionalGeoSearch(params);
    }

}
