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

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TSReadParams implements TSReadArgs {

    private final String timestamp;
    private Duration blockTimeout;
    private Integer minCount;
    private Integer maxCount;

    TSReadParams(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the cursor position as the wire wants it — a number, or one of the module's
     * <code>-</code>, <code>+</code> and <code>$</code> sentinels.
     *
     * @return cursor position
     */
    public String getTimestamp() {
        return timestamp;
    }

    public Duration getBlockTimeout() {
        return blockTimeout;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    @Override
    public TSReadArgs block(Duration timeout, int minCount) {
        if (timeout.toMillis() <= 0) {
            throw new IllegalArgumentException("block timeout must be at least 1ms, was " + timeout
                    + "; the module reads BLOCK 0 as waiting indefinitely");
        }
        this.blockTimeout = timeout;
        this.minCount = minCount;
        return this;
    }

    @Override
    public TSReadArgs maxCount(int maxCount) {
        this.maxCount = maxCount;
        return this;
    }

}
