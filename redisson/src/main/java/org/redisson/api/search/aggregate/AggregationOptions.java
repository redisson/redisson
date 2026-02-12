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
package org.redisson.api.search.aggregate;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class AggregationOptions extends AggregationBaseOptions<AggregationOptions> {

    private AggregationOptions() {
    }

    public static AggregationOptions defaults() {
        return new AggregationOptions();
    }

    public AggregationOptions withCursor() {
        withCursor = true;
        return this;
    }

    public AggregationOptions withCursor(int count) {
        withCursor = true;
        cursorCount = count;
        return this;
    }

    public AggregationOptions withCursor(int count, int maxIdle) {
        withCursor = true;
        cursorCount = count;
        cursorMaxIdle = Duration.ofMillis(maxIdle);
        return this;
    }

}
