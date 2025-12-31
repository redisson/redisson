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
public class TopicStatisticsImpl implements TopicStatistics {

    private final String name;
    private final long delayedMessages;
    private final long subscriptions;
    private final long publishedMessages;

    public TopicStatisticsImpl(String name, long delayedMessages, long subscriptions, long publishedMessages) {
        this.name = name;
        this.delayedMessages = delayedMessages;
        this.subscriptions = subscriptions;
        this.publishedMessages = publishedMessages;
    }

    @Override
    public String getTopicName() {
        return name;
    }

    @Override
    public long getDelayedMessagesCount() {
        return delayedMessages;
    }

    @Override
    public long getSubscriptionsCount() {
        return subscriptions;
    }

    @Override
    public long getPublishedMessagesCount() {
        return publishedMessages;
    }
}
