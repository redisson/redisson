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

/**
 * @author Nikita Koksharov
 *
 * @param &lt;L&gt; label type
 */
public final class TimeSeriesReadParams<L> implements TimeSeriesReadArgs<L> {

    private final long timestamp;
    private int count;
    private boolean labelFiltered;
    private L label;

    TimeSeriesReadParams(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public TimeSeriesReadArgs<L> count(int count) {
        this.count = count;
        return this;
    }

    @Override
    public TimeSeriesReadArgs<L> label(L label) {
        this.labelFiltered = true;
        this.label = label;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getCount() {
        return count;
    }

    public boolean isLabelFiltered() {
        return labelFiltered;
    }

    public L getLabel() {
        return label;
    }

}
