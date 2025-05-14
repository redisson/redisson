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
package org.redisson.api.fanout;

import org.redisson.api.MessageArgs;
import org.redisson.api.BaseSyncParams;
import org.redisson.client.codec.Codec;

public final class FanoutPublishParams<V> extends BaseSyncParams<FanoutPublishArgs<V>> implements FanoutPublishArgs<V> {

    private final MessageArgs<V>[] msgs;
    private Codec headersCodec;

    public FanoutPublishParams(MessageArgs<V>[] msgs) {
        this.msgs = msgs;
    }

    @Override
    public FanoutPublishArgs<V> headersCodec(Codec codec) {
        this.headersCodec = codec;
        return this;
    }

    public MessageArgs<V>[] getMsgs() {
        return msgs;
    }

    public Codec getHeadersCodec() {
        return headersCodec;
    }

}

