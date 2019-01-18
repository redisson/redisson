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
package org.redisson.spring.data.connection;

import java.util.ArrayList;
import java.util.List;

import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.util.AbstractSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSubscription extends AbstractSubscription {

    private final ConnectionManager connectionManager;
    private final PublishSubscribeService subscribeService;
    
    public RedissonSubscription(ConnectionManager connectionManager, PublishSubscribeService subscribeService, MessageListener listener) {
        super(listener, null, null);
        this.connectionManager = connectionManager;
        this.subscribeService = subscribeService;
    }

    @Override
    protected void doSubscribe(byte[]... channels) {
        RedisPubSubListener<?> listener2 = new BaseRedisPubSubListener() {
            @Override
            public void onMessage(CharSequence channel, Object message) {
                DefaultMessage msg = new DefaultMessage(((ChannelName)channel).getName(), (byte[])message);
                getListener().onMessage(msg, null);
            }
        };
        
        List<RFuture<?>> list = new ArrayList<RFuture<?>>();
        for (byte[] channel : channels) {
            RFuture<PubSubConnectionEntry> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, new ChannelName(channel), listener2);
            list.add(f);
        }
        for (RFuture<?> future : list) {
            connectionManager.getCommandExecutor().syncSubscription(future);
        }
    }

    @Override
    protected void doUnsubscribe(boolean all, byte[]... channels) {
        for (byte[] channel : channels) {
            subscribeService.unsubscribe(new ChannelName(channel), PubSubType.UNSUBSCRIBE);
        }
    }

    @Override
    protected void doPsubscribe(byte[]... patterns) {
        RedisPubSubListener<?> listener2 = new BaseRedisPubSubListener() {
            @Override
            public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
                DefaultMessage msg = new DefaultMessage(((ChannelName)channel).getName(), (byte[])message);
                getListener().onMessage(msg, ((ChannelName)pattern).getName());
            }
        };
        
        List<RFuture<?>> list = new ArrayList<RFuture<?>>();
        for (byte[] channel : patterns) {
            RFuture<PubSubConnectionEntry> f = subscribeService.psubscribe(new ChannelName(channel), ByteArrayCodec.INSTANCE, listener2);
            list.add(f);
        }
        for (RFuture<?> future : list) {
            connectionManager.getCommandExecutor().syncSubscription(future);
        }
    }

    @Override
    protected void doPUnsubscribe(boolean all, byte[]... patterns) {
        for (byte[] pattern : patterns) {
            subscribeService.unsubscribe(new ChannelName(pattern), PubSubType.PUNSUBSCRIBE);
        }
    }

    @Override
    protected void doClose() {
        doUnsubscribe(false, (byte[][]) getChannels().toArray(new byte[getChannels().size()][]));
        doPUnsubscribe(false, (byte[][]) getPatterns().toArray(new byte[getPatterns().size()][]));
    }

}
