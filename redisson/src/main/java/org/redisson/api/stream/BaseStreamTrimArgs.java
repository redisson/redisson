/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

/**
 *
 * @author Nikita Koksharov
 *
 */
class BaseStreamTrimArgs<T> implements StreamTrimStrategyArgs<T>, StreamTrimArgs, StreamTrimArgsSource {

    private final StreamTrimParams params;
    private final T args;

    BaseStreamTrimArgs(StreamTrimParams params, T args) {
        this.params = params;
        this.args = args;
    }

    BaseStreamTrimArgs(StreamTrimParams params) {
        this.params = params;
        this.args = (T) this;
    }

    @Override
    public StreamTrimLimitArgs<T> maxLen(int threshold) {
        params.setMaxLen(threshold);
        return new BaseStreamTrimLimitArgs<>(params, args);
    }

    @Override
    public StreamTrimLimitArgs<T> minId(StreamMessageId messageId) {
        params.setMinId(messageId);
        return new BaseStreamTrimLimitArgs<>(params, args);
    }

    @Override
    public StreamTrimParams getParams() {
        return params;
    }
}
