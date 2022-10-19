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

import java.util.Map;

/**
 * @author Nikita Koksharov
 */
public interface GeoSearchNode {

    enum Params {
        MEMBER, LONGITUDE, LATITUDE, WIDTH, HEIGHT, RADIUS, UNIT, COUNT, COUNT_ANY, ORDER
    }

    Map<Params, Object>  getParams();

}
