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
public final class PullParams extends BaseSyncParams<PullArgs> implements PullArgs {

    private AcknowledgeMode acknowledgeMode = AcknowledgeMode.MANUAL;
    private Duration timeout;
    private Duration visibility = Duration.ofSeconds(0);
    private int count = 1;

    private Codec headersCodec;

    @Override
    public PullArgs acknowledgeMode(AcknowledgeMode mode) {
        this.acknowledgeMode = mode;
        return this;
    }

    @Override
    public PullArgs headersCodec(Codec codec) {
        this.headersCodec = codec;
        return this;
    }

    @Override
    public PullArgs timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public PullArgs visibility(Duration visibility) {
        this.visibility = visibility;
        return this;
    }

    @Override
    public PullArgs count(int value) {
        this.count = value;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getVisibility() {
        return visibility;
    }

    public int getCount() {
        return count;
    }

    public Codec getHeadersCodec() {
        return headersCodec;
    }

    public AcknowledgeMode getAcknowledgeMode() {
        return acknowledgeMode;
    }
}
