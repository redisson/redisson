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
import java.util.List;
import java.util.Map;

/**
 * Configuration and statistics of a series, as reported by TS.INFO.
 *
 * @author Nikita Koksharov
 *
 */
public class TSInfo {

    private final long totalSamples;
    private final long memoryUsage;
    private final Long firstTimestamp;
    private final Long lastTimestamp;
    private final Duration retentionTime;
    private final long chunkCount;
    private final long chunkSize;
    private final TSEncoding chunkType;
    private final TSDuplicatePolicy duplicatePolicy;
    private final Map<String, String> labels;
    private final String sourceKey;
    private final List<TSRule> rules;
    private final Duration ignoreMaxTimeDiff;
    private final double ignoreMaxValueDiff;
    private final String keySelfName;
    private final List<TSChunkInfo> chunks;

    @SuppressWarnings("ParameterNumber")
    public TSInfo(long totalSamples, long memoryUsage, Long firstTimestamp, Long lastTimestamp,
                  Duration retentionTime, long chunkCount, long chunkSize, TSEncoding chunkType,
                  TSDuplicatePolicy duplicatePolicy, Map<String, String> labels, String sourceKey,
                  List<TSRule> rules, Duration ignoreMaxTimeDiff, double ignoreMaxValueDiff,
                  String keySelfName, List<TSChunkInfo> chunks) {
        this.totalSamples = totalSamples;
        this.memoryUsage = memoryUsage;
        this.firstTimestamp = firstTimestamp;
        this.lastTimestamp = lastTimestamp;
        this.retentionTime = retentionTime;
        this.chunkCount = chunkCount;
        this.chunkSize = chunkSize;
        this.chunkType = chunkType;
        this.duplicatePolicy = duplicatePolicy;
        this.labels = labels;
        this.sourceKey = sourceKey;
        this.rules = rules;
        this.ignoreMaxTimeDiff = ignoreMaxTimeDiff;
        this.ignoreMaxValueDiff = ignoreMaxValueDiff;
        this.keySelfName = keySelfName;
        this.chunks = chunks;
    }

    /**
     * Returns the number of samples held by this series.
     *
     * @return sample count
     */
    public long getTotalSamples() {
        return totalSamples;
    }

    /**
     * Returns the bytes this series occupies, configuration, rules, labels and chunks included.
     *
     * @return memory usage in bytes
     */
    public long getMemoryUsage() {
        return memoryUsage;
    }

    /**
     * Returns the lowest timestamp in this series.
     * <p>
     * An empty series reports 0 rather than nothing — the module gives no way to tell that
     * apart from a series whose first sample really sits at 0, so {@link #getTotalSamples()} is
     * what says whether there is a sample at all. The type stays boxed so that a server which
     * omits the field is read as <code>null</code> rather than crashing.
     *
     * @return first timestamp
     */
    public Long getFirstTimestamp() {
        return firstTimestamp;
    }

    /**
     * Returns the highest timestamp in this series, 0 on an empty one — see
     * {@link #getFirstTimestamp()}.
     *
     * @return last timestamp
     */
    public Long getLastTimestamp() {
        return lastTimestamp;
    }

    /**
     * Returns the retention window of this series, {@link Duration#ZERO} if samples never expire.
     *
     * @return retention window
     */
    public Duration getRetentionTime() {
        return retentionTime;
    }

    /**
     * Returns the number of chunks this series is held in.
     *
     * @return chunk count
     */
    public long getChunkCount() {
        return chunkCount;
    }

    /**
     * Returns the initial allocation size in bytes of each new chunk's data.
     *
     * @return chunk size in bytes
     */
    public long getChunkSize() {
        return chunkSize;
    }

    /**
     * Returns the sample encoding of this series.
     *
     * @return encoding
     */
    public TSEncoding getChunkType() {
        return chunkType;
    }

    /**
     * Returns the duplicate policy of this series, or <code>null</code> if it has none of its
     * own and follows the server's.
     *
     * @return duplicate policy
     */
    public TSDuplicatePolicy getDuplicatePolicy() {
        return duplicatePolicy;
    }

    /**
     * Returns the labels indexing this series.
     *
     * @return labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Returns the key this series is compacted from, or <code>null</code> if it is not a
     * compaction target.
     *
     * @return source key
     */
    public String getSourceKey() {
        return sourceKey;
    }

    /**
     * Returns the compaction rules fed by this series.
     *
     * @return rules
     */
    public List<TSRule> getRules() {
        return rules;
    }

    /**
     * Returns the largest timestamp difference an in-order duplicate may have and still be
     * ignored.
     *
     * @return ignore threshold on time
     */
    public Duration getIgnoreMaxTimeDiff() {
        return ignoreMaxTimeDiff;
    }

    /**
     * Returns the largest value difference an in-order duplicate may have and still be ignored.
     *
     * @return ignore threshold on value
     */
    public double getIgnoreMaxValueDiff() {
        return ignoreMaxValueDiff;
    }

    /**
     * Returns the key name the server reports for this series, or <code>null</code> when the
     * information was not requested with DEBUG.
     *
     * @return key name
     */
    public String getKeySelfName() {
        return keySelfName;
    }

    /**
     * Returns per-chunk detail, empty unless the information was requested with DEBUG.
     *
     * @return chunk detail
     */
    public List<TSChunkInfo> getChunks() {
        return chunks;
    }

    @Override
    public String toString() {
        return "TSInfo [totalSamples=" + totalSamples + ", memoryUsage=" + memoryUsage
                + ", firstTimestamp=" + firstTimestamp + ", lastTimestamp=" + lastTimestamp
                + ", retentionTime=" + retentionTime + ", chunkCount=" + chunkCount
                + ", chunkSize=" + chunkSize + ", chunkType=" + chunkType
                + ", duplicatePolicy=" + duplicatePolicy + ", labels=" + labels
                + ", sourceKey=" + sourceKey + ", rules=" + rules
                + ", ignoreMaxTimeDiff=" + ignoreMaxTimeDiff
                + ", ignoreMaxValueDiff=" + ignoreMaxValueDiff
                + ", keySelfName=" + keySelfName + ", chunks=" + chunks + "]";
    }

}
