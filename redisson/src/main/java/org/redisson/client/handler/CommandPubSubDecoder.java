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
package org.redisson.client.handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListObjectDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.Message;
import org.redisson.client.protocol.pubsub.PubSubMessage;
import org.redisson.client.protocol.pubsub.PubSubPatternMessage;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.misc.LogHelper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.internal.PlatformDependent;

/**
 * Redis Publish Subscribe protocol decoder
 *
 * @author Nikita Koksharov
 *
 */
public class CommandPubSubDecoder extends CommandDecoder {

    private static final Set<String> MESSAGES = new HashSet<String>(Arrays.asList("subscribe", "psubscribe", "punsubscribe", "unsubscribe"));
    // It is not needed to use concurrent map because responses are coming consecutive
    private final Map<ChannelName, PubSubEntry> entries = new HashMap<ChannelName, PubSubEntry>();
    private final Map<PubSubKey, CommandData<Object, Object>> commands = PlatformDependent.newConcurrentHashMap();

    private final boolean keepOrder;
    
    public CommandPubSubDecoder(ExecutorService executor, boolean keepOrder, boolean decodeInExecutor) {
        super(executor, decodeInExecutor);
        this.keepOrder = keepOrder;
    }

    public void addPubSubCommand(ChannelName channel, CommandData<Object, Object> data) {
        String operation = data.getCommand().getName().toLowerCase();
        commands.put(new PubSubKey(channel, operation), data);
    }

    @Override
    protected void decodeCommand(Channel channel, ByteBuf in, QueueCommand data) throws Exception {
        if (data == null) {
            try {
                while (in.writerIndex() > in.readerIndex()) {
                    decode(in, null, null, channel, false, null);
                }
                sendNext(channel);
            } catch (Exception e) {
                log.error("Unable to decode data. channel: " + channel + ", reply: " + LogHelper.toString(in), e);
                sendNext(channel);
                throw e;
            }
        } else if (data instanceof CommandData) {
            CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
            try {
                while (in.writerIndex() > in.readerIndex()) {
                    decode(in, cmd, null, channel, false, null);
                }
                sendNext(channel, data);
            } catch (Exception e) {
                log.error("Unable to decode data. channel: " + channel + ", reply: " + LogHelper.toString(in), e);
                cmd.tryFailure(e);
                sendNext(channel);
                throw e;
            }
        }
    }
    
    @Override
    protected void decodeResult(CommandData<Object, Object> data, List<Object> parts, Channel channel,
            final Object result) throws IOException {
        if (executor.isShutdown()) {
            return;
        }

        if (result instanceof Message) {
            checkpoint();

            final RedisPubSubConnection pubSubConnection = RedisPubSubConnection.getFrom(channel);
            ChannelName channelName = ((Message) result).getChannel();
            if (result instanceof PubSubStatusMessage) {
                String operation = ((PubSubStatusMessage) result).getType().name().toLowerCase();
                PubSubKey key = new PubSubKey(channelName, operation);
                CommandData<Object, Object> d = commands.get(key);
                if (Arrays.asList(RedisCommands.PSUBSCRIBE.getName(), RedisCommands.SUBSCRIBE.getName()).contains(d.getCommand().getName())) {
                    commands.remove(key);
                    entries.put(channelName, new PubSubEntry(d.getMessageDecoder()));
                }
                
                if (Arrays.asList(RedisCommands.PUNSUBSCRIBE.getName(), RedisCommands.UNSUBSCRIBE.getName()).contains(d.getCommand().getName())) {
                    commands.remove(key);
                    if (result instanceof PubSubPatternMessage) {
                        channelName = ((PubSubPatternMessage)result).getPattern();
                    }
                    PubSubEntry entry = entries.remove(channelName);
                    if (keepOrder) {
                        enqueueMessage(result, pubSubConnection, entry);
                    }
                }
            }
            
            
            if (keepOrder) {
                if (result instanceof PubSubPatternMessage) {
                    channelName = ((PubSubPatternMessage)result).getPattern();
                }
                PubSubEntry entry = entries.get(channelName);
                if (entry != null) {
                    enqueueMessage(result, pubSubConnection, entry);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (result instanceof PubSubStatusMessage) {
                            pubSubConnection.onMessage((PubSubStatusMessage) result);
                        } else if (result instanceof PubSubMessage) {
                            pubSubConnection.onMessage((PubSubMessage) result);
                        } else if (result instanceof PubSubPatternMessage) {
                            pubSubConnection.onMessage((PubSubPatternMessage) result);
                        }
                    }
                });
            }
        } else {
            if (data != null && data.getCommand().getName().equals("PING")) {
                super.decodeResult(data, parts, channel, result);
            }
        }
    }

    private void enqueueMessage(Object result, final RedisPubSubConnection pubSubConnection, final PubSubEntry entry) {
        if (result != null) {
            entry.getQueue().add((Message)result);
        }
        
        if (entry.getSent().compareAndSet(false, true)) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            Message result = entry.getQueue().poll();
                            if (result != null) {
                                if (result instanceof PubSubStatusMessage) {
                                    pubSubConnection.onMessage((PubSubStatusMessage) result);
                                } else if (result instanceof PubSubMessage) {
                                    pubSubConnection.onMessage((PubSubMessage) result);
                                } else if (result instanceof PubSubPatternMessage) {
                                    pubSubConnection.onMessage((PubSubPatternMessage) result);
                                }
                            } else {
                                break;
                            }
                        }
                    } finally {
                        entry.getSent().set(false);
                        if (!entry.getQueue().isEmpty()) {
                            enqueueMessage(null, pubSubConnection, entry);
                        }
                    }
                }
            });
        }
    }
    
    @Override
    protected MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        String command = parts.get(0).toString();
        if (MESSAGES.contains(command)) {
            ChannelName channelName = new ChannelName((byte[]) parts.get(1));
            PubSubKey key = new PubSubKey(channelName, command);
            CommandData<Object, Object> commandData = commands.get(key);
            if (commandData == null) {
                return null;
            }
            return commandData.getCommand().getReplayMultiDecoder();
        } else if (command.equals("message")) {
            byte[] channelName = (byte[]) parts.get(1);
            return entries.get(new ChannelName(channelName)).getDecoder();
        } else if (command.equals("pmessage")) {
            byte[] patternName = (byte[]) parts.get(1);
            return entries.get(new ChannelName(patternName)).getDecoder();
        } else if (command.equals("pong")) {
            return new ListObjectDecoder<Object>(0);
        }

        return data.getCommand().getReplayMultiDecoder();
    }

    @Override
    protected Decoder<Object> selectDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (parts != null) {
            if (data != null && parts.size() == 1 && "pong".equals(parts.get(0))) {
                return data.getCodec().getValueDecoder();
            }
            if (parts.size() == 1) {
                return ByteArrayCodec.INSTANCE.getValueDecoder();
            }
            if (parts.size() == 2 && "pmessage".equals(parts.get(0))) {
                return ByteArrayCodec.INSTANCE.getValueDecoder();
            }
            
            if (parts.size() == 2 && "message".equals(parts.get(0))) {
                byte[] channelName = (byte[]) parts.get(1);
                return getDecoder(parts, channelName);
            }
            if (parts.size() == 3 && "pmessage".equals(parts.get(0))) {
                byte[] patternName = (byte[]) parts.get(1);
                return getDecoder(parts, patternName);
            }
        }
        
        if (data != null && data.getCommand().getName().equals(RedisCommands.PING.getName())) {
            return data.getCodec().getValueDecoder();
        }
        
        return super.selectDecoder(data, parts);
    }

    private Decoder<Object> getDecoder(List<Object> parts, byte[] name) {
        PubSubEntry entry = entries.get(new ChannelName(name));
        if (entry != null) {
            return entry.getDecoder().getDecoder(parts.size(), state());
        }
        return ByteArrayCodec.INSTANCE.getValueDecoder();
    }

}
