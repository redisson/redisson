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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One row of a TS.MRANGE or TS.MREVRANGE reply: what a single series, or a single group,
 * contributed.
 *
 * @author Nikita Koksharov
 *
 */
public class TSSeriesSamples {

    private final Map<String, String> labels;
    private final List<TSSample> samples;

    public TSSeriesSamples(Map<String, String> labels, List<TSSample> samples) {
        this.labels = labels;
        this.samples = samples;
    }

    /**
     * Returns the labels reported for this series — every label with WITHLABELS, the requested
     * ones with SELECTED_LABELS, and none otherwise.
     *
     * @return labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Returns the samples of this series or group, ordered as the command was.
     *
     * @return samples
     */
    public List<TSSample> getSamples() {
        return samples;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TSSeriesSamples that = (TSSeriesSamples) o;
        return Objects.equals(labels, that.labels) && Objects.equals(samples, that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, samples);
    }

    @Override
    public String toString() {
        return "TSSeriesSamples [labels=" + labels + ", samples=" + samples + "]";
    }

}
