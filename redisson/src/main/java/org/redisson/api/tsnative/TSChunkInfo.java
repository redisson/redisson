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

/**
 * Per-chunk detail, as reported by TS.INFO DEBUG only.
 *
 * @author Nikita Koksharov
 *
 */
public class TSChunkInfo {

    private final long startTimestamp;
    private final long endTimestamp;
    private final long samples;
    private final long size;
    private final double bytesPerSample;

    public TSChunkInfo(long startTimestamp, long endTimestamp, long samples, long size, double bytesPerSample) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.samples = samples;
        this.size = size;
        this.bytesPerSample = bytesPerSample;
    }

    /**
     * Returns the first timestamp held by this chunk.
     *
     * @return first timestamp
     */
    public long getStartTimestamp() {
        return startTimestamp;
    }

    /**
     * Returns the last timestamp held by this chunk.
     *
     * @return last timestamp
     */
    public long getEndTimestamp() {
        return endTimestamp;
    }

    /**
     * Returns the number of samples in this chunk.
     *
     * @return sample count
     */
    public long getSamples() {
        return samples;
    }

    /**
     * Returns this chunk's data size in bytes, overheads excluded.
     *
     * @return size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the ratio of {@link #getSize()} to {@link #getSamples()}.
     *
     * @return bytes per sample
     */
    public double getBytesPerSample() {
        return bytesPerSample;
    }

    @Override
    public String toString() {
        return "TSChunkInfo [startTimestamp=" + startTimestamp + ", endTimestamp=" + endTimestamp
                + ", samples=" + samples + ", size=" + size + ", bytesPerSample=" + bytesPerSample + "]";
    }

}
