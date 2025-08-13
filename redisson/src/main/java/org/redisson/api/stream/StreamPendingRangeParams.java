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
package org.redisson.api.stream;

import org.redisson.api.StreamMessageId;

import java.time.Duration;

/**
 *
 * @author seakider
 *
 */
public class StreamPendingRangeParams implements StreamPendingRangeArgs,
        StreamStartIdArgs<StreamCountArgs>,
        StreamEndIdArgs<StreamCountArgs>, StreamCountArgs {
    private String groupName;
    private String consumerName;
    private StreamMessageId startId;
    private StreamMessageId endId;
    private boolean startIdExclusive;
    private boolean endIdExclusive;
    private int count;
    private Duration idleTime;

    StreamPendingRangeParams(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public StreamPendingRangeArgs consumerName(String consumerName) {
        this.consumerName = consumerName;
        return this;
    }

    @Override
    public StreamPendingRangeArgs idleTime(Duration idleTime) {
        this.idleTime = idleTime;
        return this;
    }

    @Override
    public StreamPendingRangeArgs count(int count) {
        this.count = count;
        return this;
    }

    @Override
    public StreamCountArgs endId(StreamMessageId endId) {
        this.endId = endId;
        return this;
    }

    @Override
    public StreamCountArgs endIdExclusive(StreamMessageId endId) {
        this.endId = endId;
        this.endIdExclusive = true;
        return this;
    }

    @Override
    public StreamEndIdArgs<StreamCountArgs> startId(StreamMessageId startId) {
        this.startId = startId;
        return this;
    }

    @Override
    public StreamEndIdArgs<StreamCountArgs> startIdExclusive(StreamMessageId startId) {
        this.startId = startId;
        this.startIdExclusive = true;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public StreamMessageId getStartId() {
        return startId;
    }

    public StreamMessageId getEndId() {
        return endId;
    }

    public boolean isStartIdExclusive() {
        return startIdExclusive;
    }

    public boolean isEndIdExclusive() {
        return endIdExclusive;
    }

    public int getCount() {
        return count;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public Duration getIdleTime() {
        return idleTime;
    }
}
