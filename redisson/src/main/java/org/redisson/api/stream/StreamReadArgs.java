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
 * Arguments object for RStream.read() methods.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamReadArgs {

    /**
     * Defines stream data size limit.
     *
     * @param count stream data size limit
     * @return arguments object
     */
    StreamReadArgs count(int count);

    /**
     * Defines time interval to wait for stream data availability.
     * <code>0</code> is used to wait infinitely.
     *
     * @param timeout timeout duration
     * @return arguments object
     */
    StreamReadArgs timeout(Duration timeout);

    /**
     * Defines last stream id received from current Stream.
     * Read stream data with ids greater than defined id.
     *
     * @param id0 last stream id of current stream
     * @return arguments object
     */
    static StreamReadArgs greaterThan(StreamMessageId id0) {
        return new StreamReadParams(id0);
    }

}
