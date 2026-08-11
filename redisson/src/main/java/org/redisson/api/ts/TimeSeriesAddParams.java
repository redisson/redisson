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
package org.redisson.api.ts;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 * @param <L> label type
 */
public final class TimeSeriesAddParams<V, L> implements TimeSeriesAddArgs<V, L> {

    private final long timestamp;
    private final V object;
    private L label;
    private Duration timeToLive;
    private Duration retention;

    TimeSeriesAddParams(long timestamp, V object) {
        this.timestamp = timestamp;
        this.object = object;
    }

    @Override
    public TimeSeriesAddParams<V, L> label(L label) {
        this.label = label;
        return this;
    }

    @Override
    public TimeSeriesAddParams<V, L> retention(Duration retention) {
        this.retention = retention;
        return this;
    }

    @Override
    public TimeSeriesAddParams<V, L> timeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public V getObject() {
        return object;
    }

    public L getLabel() {
        return label;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public Duration getRetention() {
        return retention;
    }

}
