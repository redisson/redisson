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

import org.redisson.api.RTimeSeries;

/**
 * Arguments of a tail read of a {@link RTimeSeries} collection.
 * <p>
 * <b>The read does not block.</b> It reports whatever is already there and returns, so the
 * caller owns the loop and its cadence. A sorted set has no server side wait for "entries
 * after a timestamp", and emulating one on the client would hide either a poll or a
 * dependency on keyspace notifications behind a call that looks like it blocks.
 * <pre>
 *     Long last = collection.lastTimestamp();
 *     long cursor = last == null ? Long.MIN_VALUE : last;
 *     while (running) {
 *         Collection&lt;TimeSeriesEntry&lt;V, L&gt;&gt; batch =
 *                 collection.readTail(TimeSeriesReadArgs.after(cursor).count(100));
 *         for (TimeSeriesEntry&lt;V, L&gt; entry : batch) {
 *             cursor = entry.getTimestamp();
 *         }
 *     }
 * </pre>
 *
 * @author Nikita Koksharov
 *
 * @param &lt;L&gt; label type
 */
public interface TimeSeriesReadArgs<L> {

    /**
     * Reads the entries whose timestamp is strictly greater than <code>timestamp</code>.
     * <p>
     * An entry added at a timestamp the caller has already passed is not reported, which is
     * what <code>TS.READ</code> does as well. Since this collection allows several entries at
     * one timestamp, a caller that advances its cursor to the last timestamp it saw will miss
     * an entry that lands at that same timestamp afterwards.
     *
     * @param timestamp timestamp to read after
     * @return arguments object
     * @param <L> label type
     */
    static <L> TimeSeriesReadArgs<L> after(long timestamp) {
        return new TimeSeriesReadParams<L>(timestamp);
    }

    /**
     * Defines how many entries to report. Defaults to <code>0</code>, which is all of them.
     * <p>
     * A batch never ends in the middle of a timestamp, so more than <code>count</code> entries
     * come back when the one that reaches the count has others sharing its timestamp. Ending
     * inside a timestamp would put the rest of it behind the cursor for good.
     *
     * @param count result size
     * @return arguments object
     */
    TimeSeriesReadArgs<L> count(int count);

    /**
     * Reports only the entries carrying <code>label</code>. A <code>null</code>
     * <code>label</code> selects the entries that carry no label at all.
     * <p>
     * <b>The label type is not checked here.</b> {@link #after(long)} has nothing to infer
     * <code>L</code> from, so in a chained call it is <code>Object</code> and any label is
     * accepted. A label of the wrong type simply matches nothing.
     *
     * @param label label to match, or <code>null</code> for entries without one
     * @return arguments object
     */
    TimeSeriesReadArgs<L> label(L label);

}
