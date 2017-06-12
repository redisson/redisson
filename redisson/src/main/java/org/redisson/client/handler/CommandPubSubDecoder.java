/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.client.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.Message;
import org.redisson.client.protocol.pubsub.PubSubMessage;
import org.redisson.client.protocol.pubsub.PubSubPatternMessage;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;

import io.netty.channel.Channel;
import io.netty.util.internal.PlatformDependent;

/**
 * Redis Publish Subscribe protocol decoder
 *
 * @author Nikita Koksharov
 *
 */
public class CommandPubSubDecoder extends CommandDecoder {

    // It is not needed to use concurrent map because responses are coming consecutive
    private final Map<String, MultiDecoder<Object>> pubSubMessageDecoders = new HashMap<String, MultiDecoder<Object>>();
    private final Map<PubSubKey, CommandData<Object, Object>> pubSubChannels = PlatformDependent.newConcurrentHashMap();

    private final ExecutorService executor;
    
    public CommandPubSubDecoder(ExecutorService executor) {
        this.executor = executor;
    }

    public void addPubSubCommand(String channel, CommandData<Object, Object> data) {
        String operation = data.getCommand().getName().toLowerCase();
        pubSubChannels.put(new PubSubKey(channel, operation), data);
    }

    @Override
    protected void decodeResult(CommandData<Object, Object> data, List<Object> parts, Channel channel,
            final Object result) throws IOException {
        super.decodeResult(data, parts, channel, result);
        
        if (result instanceof Message) {
            checkpoint();

            if (result instanceof PubSubStatusMessage) {
                String channelName = ((PubSubStatusMessage) result).getChannel();
                String operation = ((PubSubStatusMessage) result).getType().name().toLowerCase();
                PubSubKey key = new PubSubKey(channelName, operation);
                CommandData<Object, Object> d = pubSubChannels.get(key);
                if (Arrays.asList(RedisCommands.PSUBSCRIBE.getName(), RedisCommands.SUBSCRIBE.getName()).contains(d.getCommand().getName())) {
                    pubSubChannels.remove(key);
                    pubSubMessageDecoders.put(channelName, d.getMessageDecoder());
                }
                if (Arrays.asList(RedisCommands.PUNSUBSCRIBE.getName(), RedisCommands.UNSUBSCRIBE.getName()).contains(d.getCommand().getName())) {
                    pubSubChannels.remove(key);
                    pubSubMessageDecoders.remove(channelName);
                }
            }
            
            final RedisPubSubConnection pubSubConnection = RedisPubSubConnection.getFrom(channel);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (result instanceof PubSubStatusMessage) {
                        pubSubConnection.onMessage((PubSubStatusMessage) result);
                    } else if (result instanceof PubSubMessage) {
                        pubSubConnection.onMessage((PubSubMessage) result);
                    } else {
                        pubSubConnection.onMessage((PubSubPatternMessage) result);
                    }
                }
            });
        }
    }
    
    @Override
    protected MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            if (parts.isEmpty()) {
                return null;
            }
            String command = parts.get(0).toString();
            if (Arrays.asList("subscribe", "psubscribe", "punsubscribe", "unsubscribe").contains(command)) {
                String channelName = parts.get(1).toString();
                PubSubKey key = new PubSubKey(channelName, command);
                CommandData<Object, Object> commandData = pubSubChannels.get(key);
                if (commandData == null) {
                    return null;
                }
                return commandData.getCommand().getReplayMultiDecoder();
            } else if (parts.get(0).equals("message")) {
                String channelName = (String) parts.get(1);
                return pubSubMessageDecoders.get(channelName);
            } else if (parts.get(0).equals("pmessage")) {
                String patternName = (String) parts.get(1);
                return pubSubMessageDecoders.get(patternName);
            }
        }

        return data.getCommand().getReplayMultiDecoder();
    }

    @Override
    protected Decoder<Object> selectDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null && parts != null) {
            if (parts.size() == 2 && "message".equals(parts.get(0))) {
                String channelName = (String) parts.get(1);
                return pubSubMessageDecoders.get(channelName);
            }
            if (parts.size() == 3 && "pmessage".equals(parts.get(0))) {
                String patternName = (String) parts.get(1);
                return pubSubMessageDecoders.get(patternName);
            }
        }
        return super.selectDecoder(data, parts);
    }

}
