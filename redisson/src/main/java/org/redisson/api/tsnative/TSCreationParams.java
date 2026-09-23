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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public abstract class TSCreationParams {

    private Duration retention;
    private TSEncoding encoding;
    private Integer chunkSize;
    private TSDuplicatePolicy duplicatePolicy;
    private Duration ignoreMaxTimeDiff;
    private Double ignoreMaxValueDiff;
    private Map<String, String> labels;

    public Duration getRetention() {
        return retention;
    }

    void setRetention(Duration retention) {
        this.retention = retention;
    }

    public TSEncoding getEncoding() {
        return encoding;
    }

    void setEncoding(TSEncoding encoding) {
        this.encoding = encoding;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public TSDuplicatePolicy getDuplicatePolicy() {
        return duplicatePolicy;
    }

    void setDuplicatePolicy(TSDuplicatePolicy duplicatePolicy) {
        this.duplicatePolicy = duplicatePolicy;
    }

    public Duration getIgnoreMaxTimeDiff() {
        return ignoreMaxTimeDiff;
    }

    public Double getIgnoreMaxValueDiff() {
        return ignoreMaxValueDiff;
    }

    void setIgnore(Duration maxTimeDiff, double maxValueDiff) {
        this.ignoreMaxTimeDiff = maxTimeDiff;
        this.ignoreMaxValueDiff = maxValueDiff;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    void setLabels(Map<String, String> labels) {
        this.labels = new LinkedHashMap<>(labels);
    }

    void addLabel(String name, String value) {
        if (labels == null) {
            labels = new LinkedHashMap<>();
        }
        labels.put(name, value);
    }

}
