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
public class StreamTrimParams {

    private int maxLen;
    private StreamMessageId minId;
    private int limit;

    public int getMaxLen() {
        return maxLen;
    }
    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
    }

    public StreamMessageId getMinId() {
        return minId;
    }
    public void setMinId(StreamMessageId minId) {
        this.minId = minId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

}
