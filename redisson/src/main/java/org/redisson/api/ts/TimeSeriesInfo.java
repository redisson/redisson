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
 * A snapshot of a time-series collection, taken in one round trip.
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesInfo {

    private final int size;
    private final int totalEntries;
    private final Long firstTimestamp;
    private final Long lastTimestamp;
    private final long memoryUsage;
    private final long timeToLive;
    private final long entriesIssued;

    public TimeSeriesInfo(int size, int totalEntries, Long firstTimestamp, Long lastTimestamp,
                          long memoryUsage, long timeToLive, long entriesIssued) {
        this.size = size;
        this.totalEntries = totalEntries;
        this.firstTimestamp = firstTimestamp;
        this.lastTimestamp = lastTimestamp;
        this.memoryUsage = memoryUsage;
        this.timeToLive = timeToLive;
        this.entriesIssued = entriesIssued;
    }

    /**
     * Returns the number of entries that have not expired.
     *
     * @return live entries count
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the number of entries held, including those that have expired but that the
     * eviction task has not reclaimed yet. The difference from {@link #getSize()} is how far
     * behind eviction is.
     *
     * @return stored entries count
     */
    public int getTotalEntries() {
        return totalEntries;
    }

    /**
     * Returns the lowest timestamp of an entry that has not expired, or <code>null</code> if
     * the collection holds none.
     *
     * @return first timestamp or <code>null</code>
     */
    public Long getFirstTimestamp() {
        return firstTimestamp;
    }

    /**
     * Returns the highest timestamp of an entry that has not expired, or <code>null</code> if
     * the collection holds none.
     *
     * @return last timestamp or <code>null</code>
     */
    public Long getLastTimestamp() {
        return lastTimestamp;
    }

    /**
     * Returns the memory the collection occupies, in bytes, across all of its keys, as
     * reported by whichever node answered. A replica does not have to agree with its master
     * to the byte, so this can differ slightly from {@link org.redisson.api.RObject#sizeInMemory()},
     * which is always answered by the master.
     *
     * @return memory usage in bytes
     */
    public long getMemoryUsage() {
        return memoryUsage;
    }

    /**
     * Returns the time left before the collection itself expires, in milliseconds,
     * <code>-1</code> if it has no expiry and <code>-2</code> if it does not exist. This is
     * the expiry of the whole collection, not of its entries.
     *
     * @return remaining time to live in milliseconds
     */
    public long getTimeToLive() {
        return timeToLive;
    }

    /**
     * Returns how many entry ids have been issued to this collection. Ids are issued on every
     * addition and are not reused, so this only ever grows, and it resets when the collection
     * empties and the counter is reclaimed. Reported as {@link Long#MAX_VALUE} in the event it
     * has grown beyond one.
     *
     * @return entry ids issued
     */
    public long getEntriesIssued() {
        return entriesIssued;
    }

    @Override
    public String toString() {
        return "TimeSeriesInfo{size=" + size + ", totalEntries=" + totalEntries
                + ", firstTimestamp=" + firstTimestamp + ", lastTimestamp=" + lastTimestamp
                + ", memoryUsage=" + memoryUsage + ", timeToLive=" + timeToLive
                + ", entriesIssued=" + entriesIssued + "}";
    }

}
