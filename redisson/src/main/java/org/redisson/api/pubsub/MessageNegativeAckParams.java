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

import org.redisson.api.BaseSyncParams;

import java.time.Duration;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MessageNegativeAckParams extends BaseSyncParams<MessageNegativeAckArgs> implements MessageNegativeAckArgs, FailedAckArgs {

    private final String[] ids;
    private Duration delay = Duration.ZERO;
    private boolean failed;

    public MessageNegativeAckParams(String[] ids, boolean failed) {
        this.ids = ids;
        this.failed = failed;
    }

    @Override
    public MessageNegativeAckArgs delay(Duration value) {
        Objects.requireNonNull(value);
        this.delay = value;
        return this;
    }

    public Duration getDelay() {
        return delay;
    }

    public String[] getIds() {
        return ids;
    }

    public boolean isFailed() {
        return failed;
    }
}
