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

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class PositionValue implements Position {

    private static final String MAX_MESSAGES_PER_MILLISECOND = "9999999";

    private boolean latest;

    private boolean exclusive;
    private String messageId;
    private long timestamp;

    public PositionValue(boolean latest) {
        this.latest = latest;
    }

    public PositionValue(String messageId, boolean exclusive) {
        this.messageId = messageId;
        this.exclusive = exclusive;
    }

    public PositionValue(long timestamp, boolean exclusive) {
        this.timestamp = timestamp;
        this.exclusive = exclusive;
    }

    @Override
    public String toString() {
        if (timestamp > 0) {
            if (exclusive) {
                return timestamp + "-" + MAX_MESSAGES_PER_MILLISECOND;
            }
            return (timestamp - 1) + "-" + MAX_MESSAGES_PER_MILLISECOND;
        }
        if (messageId != null) {
            if (exclusive) {
                return "(" + messageId;
            }

            return "[" + messageId;
        }
        if (latest) {
            return "$";
        }
        return "0-0";
    }

}
