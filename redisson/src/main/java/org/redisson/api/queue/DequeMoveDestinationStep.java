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
package org.redisson.api.queue;

/**
 *
 * @author Nikita Koksharov
 *
 */
class DequeMoveDestinationStep implements DequeMoveDestination, DequeMoveSource {

    private final DequeMoveParams params = new DequeMoveParams();

    DequeMoveDestinationStep(DequeMoveParams.Direction direction) {
        params.setSourceDirection(direction);
    }

    @Override
    public DequeMoveArgs addFirstTo(String name) {
        params.setDestDirection(DequeMoveParams.Direction.LEFT);
        params.setDestName(name);
        return this;
    }

    @Override
    public DequeMoveArgs addLastTo(String name) {
        params.setDestDirection(DequeMoveParams.Direction.RIGHT);
        params.setDestName(name);
        return this;
    }

    @Override
    public DequeMoveParams getParams() {
        return params;
    }
}
