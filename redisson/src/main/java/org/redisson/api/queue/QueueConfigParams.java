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
package org.redisson.api.queue;

import java.time.Duration;
import java.util.Objects;

public final class QueueConfigParams implements QueueConfig {

    private int deliveryLimit = 10;
    private Duration visibilityTimeout = Duration.ofSeconds(30);
    private Duration ttl = Duration.ZERO;
    private String deadLetterQueueName;
    private int maxMessageSize;
    private int maxSize;
    private Duration delay = Duration.ZERO;
    private ProcessingMode processingMode = ProcessingMode.PARALLEL;

    @Override
    public QueueConfig deliveryLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Delivery limit can't lower than 1");
        }
        this.deliveryLimit = value;
        return this;
    }

    @Override
    public QueueConfig visibility(Duration value) {
        Objects.requireNonNull(value);
        if (value.toMillis() < 1) {
            throw new IllegalArgumentException("Delivery limit can't lower than 1 ms");
        }
        this.visibilityTimeout = value;
        return this;
    }

    @Override
    public QueueConfig timeToLive(Duration value) {
        Objects.requireNonNull(value);
        this.ttl = value;
        return this;
    }

    @Override
    public QueueConfig deadLetterQueueName(String value) {
        this.deadLetterQueueName = value;
        return this;
    }

    @Override
    public QueueConfig maxMessageSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Max message size limit can't lower than 0");
        }
        this.maxMessageSize = value;
        return this;
    }

    @Override
    public QueueConfig delay(Duration value) {
        Objects.requireNonNull(value);
        this.delay = value;
        return this;
    }

    @Override
    public QueueConfig maxSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Max size can't lower than 0");
        }
        this.maxSize = value;
        return this;
    }

    @Override
    public QueueConfig processingMode(ProcessingMode mode) {
        this.processingMode = mode;
        return this;
    }

    public int getDeliveryLimit() {
        return deliveryLimit;
    }

    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public Duration getTtl() {
        return ttl;
    }

    public String getDeadLetterQueueName() {
        return deadLetterQueueName;
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

    public ProcessingMode getProcessingMode() {
        return processingMode;
    }
}
