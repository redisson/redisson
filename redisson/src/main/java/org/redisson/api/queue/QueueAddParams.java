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

import org.redisson.api.BaseSyncParams;
import org.redisson.api.MessageArgs;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class QueueAddParams<V> extends BaseSyncParams<QueueAddArgs<V>> implements QueueAddArgs<V> {

    private final MessageArgs<V>[] msgs;

    private Duration timeout;
    private Codec headersCodec;

    @SafeVarargs
    public QueueAddParams(MessageArgs<V>... msgs) {
        this.msgs = msgs;
    }

    public MessageArgs<V>[] getMsgs() {
        return msgs;
    }

    @Override
    public QueueAddArgs<V> timeout(Duration value) {
        this.timeout = value;
        return this;
    }

    @Override
    public QueueAddArgs<V> headersCodec(Codec codec) {
        this.headersCodec = codec;
        return this;
    }

    public Codec getHeadersCodec() {
        return headersCodec;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
