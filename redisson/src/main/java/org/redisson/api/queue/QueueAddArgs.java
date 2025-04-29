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
package org.redisson.api.queue;

import org.redisson.api.MessageArgs;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 * Interface defining parameters for queue addition operations.
 *
 * @param <V> type
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueAddArgs<V> extends QueueSyncArgs<QueueAddArgs<V>> {

    /**
     * Sets the maximum time to wait when adding messages to a full queue with a limited size.
     *
     * @param value the maximum time to wait
     * @return arguments object
     */
    QueueAddArgs<V> timeout(Duration value);

    /**
     * Sets the codec to be used for encoding and decoding message headers.
     *
     * @param codec the codec
     * @return arguments object
     */
    QueueAddArgs<V> headersCodec(Codec codec);

    /**
     * Defines messages to be added.
     *
     * @param msgs The message arguments to be added to the queue
     * @return arguments object
     */
    @SafeVarargs
    static <V> QueueAddArgs<V> messages(MessageArgs<V>... msgs) {
        return new QueueAddParams<>(msgs);
    }

}
