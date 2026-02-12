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

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class SubscriptionConfigParams implements SubscriptionConfig {

    private boolean retainAfterAck;
    private String deadLetterTopicName;
    private int deliveryLimit;
    private Duration visibility = Duration.ofSeconds(0);
    private Position position = Position.latest();

    private final String name;

    public SubscriptionConfigParams(String name) {
        this.name = name;
    }

    @Override
    public SubscriptionConfig deadLetterTopicName(String value) {
        this.deadLetterTopicName = value;
        return this;
    }

    @Override
    public SubscriptionConfig deliveryLimit(int value) {
        this.deliveryLimit = value;
        return this;
    }

    @Override
    public SubscriptionConfig visibility(Duration value) {
        this.visibility = value;
        return this;
    }

    @Override
    public SubscriptionConfig position(Position value) {
        this.position = value;
        return this;
    }

    @Override
    public SubscriptionConfig retainAfterAck() {
        this.retainAfterAck = true;
        return this;
    }

    public String getDeadLetterTopicName() {
        return deadLetterTopicName;
    }

    public int getDeliveryLimit() {
        return deliveryLimit;
    }

    public Duration getVisibility() {
        return visibility;
    }

    public Position getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public boolean isRetainAfterAck() {
        return retainAfterAck;
    }
}
