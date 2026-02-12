/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.client;

import org.redisson.client.protocol.pubsub.PubSubType;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SubscribeListener extends BaseRedisPubSubListener {

    private final CompletableFuture<Void> promise = new CompletableFuture<>();
    private final ChannelName name;
    private final PubSubType type;

    public SubscribeListener(ChannelName name, PubSubType type) {
        super();
        this.name = name;
        this.type = type;
    }

    @Override
    public void onStatus(PubSubType type, CharSequence channel) {
        if (name.equals(channel) && this.type.equals(type)) {
            promise.complete(null);
        }
    }

    public CompletableFuture<Void> getSuccessFuture() {
        return promise;
    }
    
}
