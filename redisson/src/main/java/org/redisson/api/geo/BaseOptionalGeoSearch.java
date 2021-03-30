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

import org.redisson.api.GeoOrder;

import java.util.Map;

/**
 * @author Nikita Koksharov
 */
class BaseOptionalGeoSearch implements OptionalGeoSearch, GeoSearchNode {

    private final Map<Params, Object> params;

    BaseOptionalGeoSearch(Map<Params, Object> params) {
        this.params = params;
    }

    @Override
    public OptionalGeoSearch count(int value) {
        params.put(Params.COUNT, value);
        params.remove(Params.COUNT_ANY);
        return this;
    }

    @Override
    public OptionalGeoSearch countAny(int value) {
        params.put(Params.COUNT, value);
        params.put(Params.COUNT_ANY, true);
        return this;
    }

    @Override
    public OptionalGeoSearch order(GeoOrder geoOrder) {
        params.put(Params.ORDER, geoOrder);
        return this;
    }

    @Override
    public Map<Params, Object> getParams() {
        return params;
    }

}
