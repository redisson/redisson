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
package org.redisson.api.stream;

/**
 *
 * @author seakider
 *
 */
public final class StreamRangeParams implements StreamRangeArgs, StreamEndIdArgs {
    private StreamMessageId startId;
    private StreamMessageId endId;
    private boolean startIdExclusive;
    private boolean endIdExclusive;
    private int count;

    StreamRangeParams(StreamMessageId startId, boolean startIdExclusive) {
        this.startId = startId;
        this.startIdExclusive = startIdExclusive;
    }

    @Override
    public StreamRangeArgs endId(StreamMessageId endId) {
        this.endId = endId;
        return this;
    }

    @Override
    public StreamRangeArgs endIdExclusive(StreamMessageId endId) {
        this.endId = endId;
        endIdExclusive = true;
        return this;
    }

    @Override
    public StreamRangeArgs count(int count) {
        this.count = count;
        return this;
    }

    public boolean isStartIdExclusive() {
        return startIdExclusive;
    }

    public boolean isEndIdExclusive() {
        return endIdExclusive;
    }

    public StreamMessageId getStartId() {
        return startId;
    }

    public StreamMessageId getEndId() {
        return endId;
    }

    public int getCount() {
        return count;
    }
}
