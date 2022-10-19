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

import java.time.Duration;
import java.util.Collections;

/**
 * Arguments object for RStream.readGroup() methods.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamReadGroupArgs {

    /**
     * Defines avoid of adding messages to Pending Entries List.
     *
     * @return arguments object
     */
    StreamReadGroupArgs noAck();

    /**
     * Defines stream data size limit.
     *
     * @param count - stream data size limit
     * @return arguments object
     */
    StreamReadGroupArgs count(int count);

    /**
     * Defines time interval to wait for stream data availability.
     *
     * @param timeout - timeout duration
     * @return arguments object
     */
    StreamReadGroupArgs timeout(Duration timeout);

    /**
     * Defines to return messages of current Stream
     * never delivered to any other consumer.
     *
     * @return arguments object
     */
    static StreamReadGroupArgs neverDelivered() {
        return greaterThan(null);
    }

    /**
     * Defines to return messages of current Stream
     * with ids greater than defined message id.
     *
     * @param id - message id
     * @return arguments object
     */
    static StreamReadGroupArgs greaterThan(StreamMessageId id) {
        return new BaseStreamReadGroupArgs(id, Collections.emptyMap());
    }

}
