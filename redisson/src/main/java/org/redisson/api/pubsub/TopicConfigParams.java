/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.time.Duration;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TopicConfigParams implements TopicConfig {

    private RetentionMode retentionMode = RetentionMode.SUBSCRIPTION_OPTIONAL_RETAIN_ALL;
    private Duration visibilityTimeout = Duration.ofSeconds(30);
    private Duration ttl = Duration.ZERO;
    private int maxMessageSize;
    private int maxSize;
    private Duration delay = Duration.ZERO;
    private int deliveryLimit = 10;

    @Override
    public TopicConfig visibility(Duration value) {
        Objects.requireNonNull(value);
        if (value.toMillis() < 1) {
            throw new IllegalArgumentException("Delivery limit can't lower than 1 ms");
        }
        this.visibilityTimeout = value;
        return this;
    }

    @Override
    public TopicConfig timeToLive(Duration value) {
        Objects.requireNonNull(value);
        this.ttl = value;
        return this;
    }

    @Override
    public TopicConfig maxMessageSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Max message size limit can't lower than 0");
        }
        this.maxMessageSize = value;
        return this;
    }

    @Override
    public TopicConfig delay(Duration value) {
        Objects.requireNonNull(value);
        this.delay = value;
        return this;
    }

    @Override
    public TopicConfig maxSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Max size can't lower than 0");
        }
        this.maxSize = value;
        return this;
    }

    @Override
    public TopicConfig deliveryLimit(int value) {
        this.deliveryLimit = value;
        return this;
    }

    @Override
    public TopicConfig retentionMode(RetentionMode mode) {
        this.retentionMode = mode;
        return this;
    }

    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public Duration getTtl() {
        return ttl;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public Duration getDelay() {
        return delay;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getDeliveryLimit() {
        return deliveryLimit;
    }

    public RetentionMode getRetentionMode() {
        return retentionMode;
    }
}
