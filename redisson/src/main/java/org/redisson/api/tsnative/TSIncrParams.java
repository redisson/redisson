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
package org.redisson.api.tsnative;

import java.time.Duration;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TSIncrParams extends TSCreationParams implements TSIncrArgs {

    private final double value;
    private Long timestamp;

    TSIncrParams(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    /**
     * Returns the timestamp the result is written at, or <code>null</code> when the server is to
     * supply one.
     *
     * @return timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    @Override
    public TSIncrArgs timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    @Override
    public TSIncrArgs retention(Duration retention) {
        setRetention(retention);
        return this;
    }

    @Override
    public TSIncrArgs encoding(TSEncoding encoding) {
        setEncoding(encoding);
        return this;
    }

    @Override
    public TSIncrArgs chunkSize(int chunkSize) {
        setChunkSize(chunkSize);
        return this;
    }

    @Override
    public TSIncrArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy) {
        setDuplicatePolicy(duplicatePolicy);
        return this;
    }

    @Override
    public TSIncrArgs ignore(Duration maxTimeDiff, double maxValueDiff) {
        setIgnore(maxTimeDiff, maxValueDiff);
        return this;
    }

    @Override
    public TSIncrArgs labels(Map<String, String> labels) {
        setLabels(labels);
        return this;
    }

    @Override
    public TSIncrArgs label(String name, String value) {
        addLabel(name, value);
        return this;
    }

}
