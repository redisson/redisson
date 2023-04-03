/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class StreamAddParams<K, V> implements StreamAddArgs<K, V>,
                                              StreamTrimStrategyArgs<StreamAddArgs<K, V>>,
                                              StreamTrimLimitArgs<StreamAddArgs<K, V>>  {

    private final Map<K, V> entries;
    private boolean noMakeStream;
    private boolean trimStrict;

    private int maxLen;
    private StreamMessageId minId;
    private int limit;

    StreamAddParams(Map<K, V> entries) {
        this.entries = entries;
    }

    @Override
    public StreamAddArgs<K, V> noMakeStream() {
        this.noMakeStream = true;
        return this;
    }

    @Override
    public StreamAddArgs<K, V> trim(TrimStrategy strategy, int threshold) {
        return trim(strategy, threshold, 0);
    }

    @Override
    public StreamAddArgs<K, V> trimStrict(TrimStrategy strategy, int threshold) {
        this.maxLen = threshold;
        this.trimStrict = true;
        return this;
    }

    @Override
    public StreamAddArgs<K, V> trim(TrimStrategy strategy, int threshold, int limit) {
        this.maxLen = threshold;
        this.trimStrict = false;
        this.limit = limit;
        return this;
    }

    @Override
    public StreamTrimStrategyArgs<StreamAddArgs<K, V>> trim() {
        this.trimStrict = true;
        return this;
    }

    @Override
    public StreamTrimStrategyArgs<StreamAddArgs<K, V>> trimNonStrict() {
        this.trimStrict = false;
        return this;
    }

    @Override
    public StreamTrimLimitArgs<StreamAddArgs<K, V>> maxLen(int threshold) {
        this.maxLen = threshold;
        return this;
    }

    @Override
    public StreamTrimLimitArgs<StreamAddArgs<K, V>> minId(StreamMessageId messageId) {
        this.minId = messageId;
        return this;
    }

    @Override
    public StreamAddArgs<K, V> noLimit() {
        this.limit = 0;
        return this;
    }

    @Override
    public StreamAddArgs<K, V> limit(int size) {
        this.limit = size;
        return this;
    }

    public Map<K, V> getEntries() {
        return entries;
    }

    public boolean isNoMakeStream() {
        return noMakeStream;
    }

    public boolean isTrimStrict() {
        return trimStrict;
    }

    public int getMaxLen() {
        return maxLen;
    }

    public StreamMessageId getMinId() {
        return minId;
    }

    public int getLimit() {
        return limit;
    }
}
