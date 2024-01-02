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

/**
 * Arguments object for Stream trim method.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamTrimArgs {

    /**
     * Defines MAXLEN strategy used for Stream trimming.
     * Evicts entries which position exceeds the specified stream's length threshold.
     *
     * @param threshold - trim threshold
     * @return arguments object
     */
    static StreamTrimLimitArgs<StreamTrimArgs> maxLen(int threshold) {
        return new StreamTrimParams(threshold);
    }

    /**
     * Defines MINID strategy used for Stream trimming.
     * Evicts entries with IDs lower than threshold, where threshold is a stream ID.
     *
     * @param messageId - stream Id
     * @return arguments object
     */
    static StreamTrimLimitArgs<StreamTrimArgs> minId(StreamMessageId messageId) {
        return new StreamTrimParams(messageId);
    }

}
