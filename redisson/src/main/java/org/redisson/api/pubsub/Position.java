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
package org.redisson.api.pubsub;

import java.time.Instant;

/**
 * Represents a position within a pubsub subscription's message stream.
 * <p>
 * Positions are used with {@link Subscription#seek(Position)} to control
 * where message consumption begins, enabling message replay or skipping.
 *
 * @author Nikita Koksharov
 *
 */
public interface Position {

    /**
     * Creates a position pointing to the latest (newest) messages.
     * <p>
     * This is the default position.
     *
     * @return position object
     */
    static Position latest() {
        return new PositionValue(true);
    }

    /**
     * Creates a position pointing to the earliest (oldest) available messages.
     *
     * @return position object
     */
    static Position earliest() {
        return new PositionValue(false);
    }

    /**
     * Creates a position at the specified message ID (inclusive).
     *
     * @param id the message ID to seek to
     * @return position object
     */
    static Position messageId(String id) {
        return new PositionValue(id, false);
    }

    /**
     * Creates a position after the specified message ID (exclusive).
     *
     * @param id the message ID to seek after
     * @return position object
     */
    static Position messageIdExclusive(String id) {
        return new PositionValue(id, true);
    }

    /**
     * Creates a position at the specified timestamp (inclusive).
     *
     * @param value the timestamp to seek to
     * @return position object
     */
    static Position timestamp(Instant value) {
        return new PositionValue(value.toEpochMilli(), false);
    }

    /**
     * Creates a position after the specified timestamp (exclusive).
     * <p>
     * When seeking to this position, consumption will start from
     * the first message with a timestamp strictly greater than the specified value.
     *
     * @param value the timestamp to seek after
     * @return position object
     */
    static Position timestampExclusive(Instant value) {
        return new PositionValue(value.toEpochMilli(), true);
    }


}
