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
public final class TSAddParams extends TSCreationParams implements TSAddArgs {

    private final Long timestamp;
    private final double value;
    private TSDuplicatePolicy onDuplicate;

    TSAddParams(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    TSAddParams(double value) {
        this.timestamp = null;
        this.value = value;
    }

    /**
     * Returns the sample timestamp, or <code>null</code> when the server is to supply one.
     *
     * @return timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    public double getValue() {
        return value;
    }

    public TSDuplicatePolicy getOnDuplicate() {
        return onDuplicate;
    }

    @Override
    public TSAddArgs onDuplicate(TSDuplicatePolicy duplicatePolicy) {
        this.onDuplicate = duplicatePolicy;
        return this;
    }

    @Override
    public TSAddArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy) {
        setDuplicatePolicy(duplicatePolicy);
        return this;
    }

    @Override
    public TSAddArgs retention(Duration retention) {
        setRetention(retention);
        return this;
    }

    @Override
    public TSAddArgs encoding(TSEncoding encoding) {
        setEncoding(encoding);
        return this;
    }

    @Override
    public TSAddArgs chunkSize(int chunkSize) {
        setChunkSize(chunkSize);
        return this;
    }

    @Override
    public TSAddArgs ignore(Duration maxTimeDiff, double maxValueDiff) {
        setIgnore(maxTimeDiff, maxValueDiff);
        return this;
    }

    @Override
    public TSAddArgs labels(Map<String, String> labels) {
        setLabels(labels);
        return this;
    }

    @Override
    public TSAddArgs label(String name, String value) {
        addLabel(name, value);
        return this;
    }

}
