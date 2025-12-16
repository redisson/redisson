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
package org.redisson.api.pubsub;

import org.redisson.api.BaseSyncParams;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class MessageListenerParams<V> extends BaseSyncParams<MessageListenerArgs<V>> implements MessageListenerArgs<V> {

    private final MessageListener<V> listener;

    private AcknowledgeMode acknowledgeMode = AcknowledgeMode.MANUAL;
    private Duration visibility = Duration.ofSeconds(30);

    private Codec headersCodec;

    MessageListenerParams(MessageListener<V> listener) {
        this.listener = listener;
    }

    @Override
    public MessageListenerArgs<V> acknowledgeMode(AcknowledgeMode mode) {
        this.acknowledgeMode = mode;
        return this;
    }

    @Override
    public MessageListenerArgs<V> headersCodec(Codec codec) {
        this.headersCodec = codec;
        return this;
    }

    @Override
    public MessageListenerArgs<V> visibility(Duration value) {
        this.visibility = value;
        return this;
    }

    public Duration getVisibility() {
        return visibility;
    }

    public Codec getHeadersCodec() {
        return headersCodec;
    }

    public AcknowledgeMode getAcknowledgeMode() {
        return acknowledgeMode;
    }

    public MessageListener<V> getListener() {
        return listener;
    }

}
