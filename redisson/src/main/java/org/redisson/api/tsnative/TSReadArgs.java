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

import org.redisson.api.RTimeSeriesNative;

import java.time.Duration;

/**
 * Arguments of {@link RTimeSeriesNative#read(TSReadArgs)}.
 * <p>
 * TS.READ is a cursor over a series that is still being written to, which is why it has its own
 * arguments rather than sharing {@link TSRangeArgs}: it takes a lower bound and no upper one,
 * and it can wait for samples that have not arrived yet.
 * <pre>
 *     List&lt;TSSample&gt; batch = timeSeries.read(TSReadArgs.fromNext()
 *                                                      .block(Duration.ofSeconds(5), 1)
 *                                                      .maxCount(100));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface TSReadArgs {

    /**
     * Reads from the given timestamp onwards, that timestamp included.
     *
     * @param timestamp inclusive lower bound in milliseconds
     * @return arguments object
     */
    static TSReadArgs from(long timestamp) {
        return new TSReadParams(Long.toString(timestamp));
    }

    /**
     * Reads the whole series, earliest sample first.
     *
     * @return arguments object
     */
    static TSReadArgs fromEarliest() {
        return new TSReadParams("-");
    }

    /**
     * Reads from the latest sample onwards, that sample included.
     *
     * @return arguments object
     */
    static TSReadArgs fromLast() {
        return new TSReadParams("+");
    }

    /**
     * Reads only samples added after the call — the cursor starts one past the latest sample.
     * Combined with {@link #block}, this is how a caller tails a series.
     *
     * @return arguments object
     */
    static TSReadArgs fromNext() {
        return new TSReadParams("$");
    }

    /**
     * Waits up to the given time for at least <code>minCount</code> samples to qualify, rather
     * than returning whatever is there straight away.
     * <p>
     * A blocking read holds its connection for the duration, so it draws on the blocking
     * connection pool the way {@link org.redisson.api.RBlockingQueue} does.
     *
     * @param timeout how long to wait, at least one millisecond
     * @param minCount how many samples to wait for
     * @return arguments object
     * <p>
     * <code>minCount</code> is left to the server, which answers a non-positive one with
     * "BLOCK min_count must be a positive integer" straight away. Only the timeout is checked
     * here, because a zero there is not refused but silently means forever.
     *
     * @throws IllegalArgumentException if <code>timeout</code> is under a millisecond, which the
     *         module would read as waiting indefinitely
     */
    TSReadArgs block(Duration timeout, int minCount);

    /**
     * Limits how many samples are returned.
     *
     * @param maxCount maximum number of samples
     * @return arguments object
     */
    TSReadArgs maxCount(int maxCount);

}
