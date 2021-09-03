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

import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
class BaseStreamAddArgs<K, V> implements StreamAddArgs<K, V>, StreamAddArgsSource<K, V> {

    private final StreamAddParams<K, V> params;

    BaseStreamAddArgs(Map<K, V> entries) {
        params = new StreamAddParams<K, V>(entries);
    }

    @Override
    public StreamAddParams<K, V> getParams() {
        return params;
    }

    @Override
    public StreamAddArgs<K, V> noMakeStream() {
        params.setNoMakeStream(true);
        return this;
    }

    @Override
    public StreamAddArgs<K, V> trim(TrimStrategy strategy, int threshold) {
        return trim(strategy, threshold, 0);
    }

    @Override
    public StreamAddArgs<K, V> trimStrict(TrimStrategy strategy, int threshold) {
        params.setMaxLen(threshold);
        params.setTrimStrict(true);
        return this;
    }

    @Override
    public StreamAddArgs<K, V> trim(TrimStrategy strategy, int threshold, int limit) {
        params.setMaxLen(threshold);
        params.setTrimStrict(false);
        params.setLimit(limit);
        return this;
    }

    @Override
    public StreamTrimStrategyArgs<StreamAddArgs<K, V>> trim() {
        params.setTrimStrict(true);
        return new BaseStreamTrimArgs<>(params, this);
    }

    @Override
    public StreamTrimStrategyArgs<StreamAddArgs<K, V>> trimNonStrict() {
        return new BaseStreamTrimArgs<>(params, this);
    }
}
