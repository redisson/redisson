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
public class StreamAddParams<K, V> {

    private Map<K, V> entries;
    private boolean noMakeStream;
    private boolean trimStrict;
    private TrimStrategy trimStrategy;
    private int trimThreshold;
    private int limit;

    public StreamAddParams(Map<K, V> entries) {
        this.entries = entries;
    }

    public Map<K, V> getEntries() {
        return entries;
    }

    public boolean isNoMakeStream() {
        return noMakeStream;
    }

    public void setNoMakeStream(boolean noMakeStream) {
        this.noMakeStream = noMakeStream;
    }

    public boolean isTrimStrict() {
        return trimStrict;
    }

    public void setTrimStrict(boolean trimStrict) {
        this.trimStrict = trimStrict;
    }

    public TrimStrategy getTrimStrategy() {
        return trimStrategy;
    }

    public void setTrimStrategy(TrimStrategy trimStrategy) {
        this.trimStrategy = trimStrategy;
    }

    public int getTrimThreshold() {
        return trimThreshold;
    }

    public void setTrimThreshold(int trimThreshold) {
        this.trimThreshold = trimThreshold;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
