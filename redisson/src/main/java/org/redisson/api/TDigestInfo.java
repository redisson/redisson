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
package org.redisson.api;

/**
 * t-digest information returned by the {@code TDIGEST.INFO} command.
 *
 * @author Nikita Koksharov
 *
 */
public class TDigestInfo {

    private final long compression;
    private final long capacity;
    private final long mergedNodes;
    private final long unmergedNodes;
    private final double mergedWeight;
    private final double unmergedWeight;
    private final long observations;
    private final long totalCompressions;
    private final long memoryUsage;

    public TDigestInfo(long compression, long capacity,
                       long mergedNodes, long unmergedNodes,
                       double mergedWeight, double unmergedWeight,
                       long observations, long totalCompressions,
                       long memoryUsage) {
        this.compression = compression;
        this.capacity = capacity;
        this.mergedNodes = mergedNodes;
        this.unmergedNodes = unmergedNodes;
        this.mergedWeight = mergedWeight;
        this.unmergedWeight = unmergedWeight;
        this.observations = observations;
        this.totalCompressions = totalCompressions;
        this.memoryUsage = memoryUsage;
    }

    /**
     * Returns the compression factor of the sketch.
     *
     * @return compression
     */
    public long getCompression() {
        return compression;
    }

    /**
     * Returns the number of centroids the sketch can hold.
     *
     * @return capacity
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Returns the number of merged centroids.
     *
     * @return merged nodes
     */
    public long getMergedNodes() {
        return mergedNodes;
    }

    /**
     * Returns the number of buffered, not yet merged centroids.
     *
     * @return unmerged nodes
     */
    public long getUnmergedNodes() {
        return unmergedNodes;
    }

    /**
     * Returns the total weight of merged centroids.
     *
     * @return merged weight
     */
    public double getMergedWeight() {
        return mergedWeight;
    }

    /**
     * Returns the total weight of buffered, not yet merged centroids.
     *
     * @return unmerged weight
     */
    public double getUnmergedWeight() {
        return unmergedWeight;
    }

    /**
     * Returns the number of observations added to the sketch.
     *
     * @return observations
     */
    public long getObservations() {
        return observations;
    }

    /**
     * Returns the number of compaction operations performed on the sketch.
     *
     * @return total compressions
     */
    public long getTotalCompressions() {
        return totalCompressions;
    }

    /**
     * Returns the number of bytes allocated for the sketch.
     *
     * @return memory usage in bytes
     */
    public long getMemoryUsage() {
        return memoryUsage;
    }
}