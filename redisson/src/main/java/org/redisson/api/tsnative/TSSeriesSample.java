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

import java.util.Map;
import java.util.Objects;

/**
 * One row of a TS.MGET reply: the labels the command was asked to report, and that series' last
 * sample.
 *
 * @author Nikita Koksharov
 *
 */
public class TSSeriesSample {

    private final Map<String, String> labels;
    private final TSSample sample;

    public TSSeriesSample(Map<String, String> labels, TSSample sample) {
        this.labels = labels;
        this.sample = sample;
    }

    /**
     * Returns the labels reported for this series — every label with WITHLABELS, the requested
     * ones with SELECTED_LABELS, and none otherwise. A label selected but not present on the
     * series maps to <code>null</code>.
     *
     * @return labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Returns the last sample of this series, or <code>null</code> if the series is empty.
     *
     * @return last sample
     */
    public TSSample getSample() {
        return sample;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TSSeriesSample that = (TSSeriesSample) o;
        return Objects.equals(labels, that.labels) && Objects.equals(sample, that.sample);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, sample);
    }

    @Override
    public String toString() {
        return "TSSeriesSample [labels=" + labels + ", sample=" + sample + "]";
    }

}
