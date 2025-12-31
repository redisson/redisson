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

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class SubscriptionStatisticsImpl implements SubscriptionStatistics {

    private final String name;
    private final long consumers;
    private final long redeliveryAttempts;
    private final long unacknowledgedMessages;
    private final long acknowledgedMessages;
    private final long negativelyAcknowledgedMessages;
    private final long deadLetteredMessages;

    public SubscriptionStatisticsImpl(String name, long consumers, long redeliveryAttempts, long unacknowledgedMessages,
                                      long acknowledgedMessages, long negativelyAcknowledgedMessages, long deadLetteredMessages) {
        this.name = name;
        this.consumers = consumers;
        this.redeliveryAttempts = redeliveryAttempts;
        this.unacknowledgedMessages = unacknowledgedMessages;
        this.acknowledgedMessages = acknowledgedMessages;
        this.negativelyAcknowledgedMessages = negativelyAcknowledgedMessages;
        this.deadLetteredMessages = deadLetteredMessages;
    }

    @Override
    public String getSubscriptionName() {
        return name;
    }

    @Override
    public long getConsumersCount() {
        return consumers;
    }

    @Override
    public long getRedeliveryAttemptsCount() {
        return redeliveryAttempts;
    }

    @Override
    public long getUnacknowledgedMessagesCount() {
        return unacknowledgedMessages;
    }

    @Override
    public long getAcknowledgedMessagesCount() {
        return acknowledgedMessages;
    }

    @Override
    public long getNegativelyAcknowledgedMessagesCount() {
        return negativelyAcknowledgedMessages;
    }

    @Override
    public long getDeadLetteredMessagesCount() {
        return deadLetteredMessages;
    }
}
