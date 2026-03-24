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
 * @author Nikita Koksharov
 *
 */
public final class StreamTrimParams extends BaseReferencesParams<StreamTrimLimitArgs<StreamTrimArgs>>
                                        implements StreamTrimStrategyArgs<StreamTrimArgs>,
                                            StreamTrimArgs,
                                            StreamTrimReferencesArgs<StreamTrimArgs> {

    Integer maxLen;
    StreamMessageId minId;
    int limit;

    StreamTrimParams(int threshold) {
        this.maxLen = threshold;
    }

    StreamTrimParams(StreamMessageId minId) {
        this.minId = minId;
    }

    @Override
    public StreamTrimReferencesArgs<StreamTrimArgs> maxLen(int threshold) {
        this.maxLen = threshold;
        return this;
    }

    @Override
    public StreamTrimReferencesArgs<StreamTrimArgs> minId(StreamMessageId messageId) {
        this.minId = messageId;
        return this;
    }

    @Override
    public StreamTrimArgs noLimit() {
        this.limit = 0;
        return this;
    }

    @Override
    public StreamTrimArgs limit(int size) {
        this.limit = size;
        return this;
    }

    public Integer getMaxLen() {
        return maxLen;
    }

    public StreamMessageId getMinId() {
        return minId;
    }

    public int getLimit() {
        return limit;
    }
}
