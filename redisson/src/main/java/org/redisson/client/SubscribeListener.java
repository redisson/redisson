/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SubscribeListener extends BaseRedisPubSubListener {

    private final Promise<Void> promise = ImmediateEventExecutor.INSTANCE.newPromise();
    private final ChannelName name;
    private final PubSubType type;

    public SubscribeListener(ChannelName name, PubSubType type) {
        super();
        this.name = name;
        this.type = type;
    }

    @Override
    public boolean onStatus(PubSubType type, CharSequence channel) {
        if (name.equals(channel) && this.type.equals(type)) {
            promise.trySuccess(null);
        }
        return true;
    }

    public Future<Void> getSuccessFuture() {
        return promise;
    }
    
}
