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
public final class TSAlterParams extends TSCreationParams implements TSAlterArgs {

    TSAlterParams() {
    }

    @Override
    public TSAlterArgs retention(Duration retention) {
        setRetention(retention);
        return this;
    }

    @Override
    public TSAlterArgs chunkSize(int chunkSize) {
        setChunkSize(chunkSize);
        return this;
    }

    @Override
    public TSAlterArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy) {
        setDuplicatePolicy(duplicatePolicy);
        return this;
    }

    @Override
    public TSAlterArgs ignore(Duration maxTimeDiff, double maxValueDiff) {
        setIgnore(maxTimeDiff, maxValueDiff);
        return this;
    }

    @Override
    public TSAlterArgs labels(Map<String, String> labels) {
        setLabels(labels);
        return this;
    }

    @Override
    public TSAlterArgs label(String name, String value) {
        addLabel(name, value);
        return this;
    }

}
