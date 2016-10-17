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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.RedisTryAgainException;
import org.redisson.client.RedisAskException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.SlotsDecoder;
import org.redisson.client.protocol.pubsub.Message;
import org.redisson.client.protocol.pubsub.PubSubMessage;
import org.redisson.client.protocol.pubsub.PubSubPatternMessage;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

/**
 * Redis protocol command decoder
 *
 * Code parts from Sam Pullara
 *
 * @author Nikita Koksharov
 *
 */
public class CommandDecoder extends ReplayingDecoder<State> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final char CR = '\r';
    public static final char LF = '\n';
    private static final char ZERO = '0';

    // It is not needed to use concurrent map because responses are coming consecutive
    private final Map<String, MultiDecoder<Object>> pubSubMessageDecoders = new HashMap<String, MultiDecoder<Object>>();
    private final Map<PubSubKey, CommandData<Object, Object>> pubSubChannels = PlatformDependent.newConcurrentHashMap();

    public void addPubSubCommand(String channel, CommandData<Object, Object> data) {
        String operation = data.getCommand().getName().toLowerCase();
        pubSubChannels.put(new PubSubKey(channel, operation), data);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        QueueCommand data = ctx.channel().attr(CommandsQueue.CURRENT_COMMAND).get();

        if (log.isTraceEnabled()) {
            log.trace("channel: {} message: {}", ctx.channel(), in.toString(0, in.writerIndex(), CharsetUtil.UTF_8));
        }
        if (state() == null) {
            boolean makeCheckpoint = data != null;
            if (data != null) {
                if (data instanceof CommandsData) {
                    makeCheckpoint = false;
                } else {
                    CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
                    if (cmd.getCommand().getReplayMultiDecoder() != null 
                            && (NestedMultiDecoder.class.isAssignableFrom(cmd.getCommand().getReplayMultiDecoder().getClass())
                                    || SlotsDecoder.class.isAssignableFrom(cmd.getCommand().getReplayMultiDecoder().getClass())
                                    || ListMultiDecoder.class.isAssignableFrom(cmd.getCommand().getReplayMultiDecoder().getClass()))) {
                        makeCheckpoint = false;
                    }
                }
            }
            state(new State(makeCheckpoint));
        }

        state().setDecoderState(null);

        if (data == null) {
            decode(in, null, null, ctx.channel());
        } else if (data instanceof CommandData) {
            CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
            try {
                if (state().getLevels().size() > 0) {
                    decodeFromCheckpoint(ctx, in, data, cmd);
                } else {
                    decode(in, cmd, null, ctx.channel());
                }
            } catch (Exception e) {
                cmd.tryFailure(e);
            }
        } else if (data instanceof CommandsData) {
            CommandsData commands = (CommandsData)data;
            try {
                decodeCommandBatch(ctx, in, data, commands);
            } catch (Exception e) {
                commands.getPromise().tryFailure(e);
            }
            return;
        }
        
        ctx.pipeline().get(CommandsQueue.class).sendNextCommand(ctx.channel());

        state(null);
    }

    private void decodeFromCheckpoint(ChannelHandlerContext ctx, ByteBuf in, QueueCommand data,
            CommandData<Object, Object> cmd) throws IOException {
        if (state().getLevels().size() == 2) {
            StateLevel secondLevel = state().getLevels().get(1);
            
            if (secondLevel.getParts().isEmpty()) {
                state().getLevels().remove(1);
            }
        }
        
        if (state().getLevels().size() == 2) {
            StateLevel firstLevel = state().getLevels().get(0);
            StateLevel secondLevel = state().getLevels().get(1);
            
            decodeList(in, cmd, firstLevel.getParts(), ctx.channel(), secondLevel.getSize(), secondLevel.getParts());
            
            Channel channel = ctx.channel();
            MultiDecoder<Object> decoder = messageDecoder(cmd, firstLevel.getParts(), channel);
            if (decoder != null) {
                Object result = decoder.decode(firstLevel.getParts(), state());
                if (data != null) {
                    handleResult(cmd, null, result, true, channel);
                }
            }
        }
        if (state().getLevels().size() == 1) {
            StateLevel firstLevel = state().getLevels().get(0);
            if (firstLevel.getParts().isEmpty()) {
                state().resetLevel();
                decode(in, cmd, null, ctx.channel());
            } else {
                decodeList(in, cmd, null, ctx.channel(), firstLevel.getSize(), firstLevel.getParts());
            }
        }
    }

    private void decodeCommandBatch(ChannelHandlerContext ctx, ByteBuf in, QueueCommand data,
                    CommandsData commandBatch) {
        int i = state().getBatchIndex();

        RedisException error = null;
        while (in.writerIndex() > in.readerIndex()) {
            CommandData<Object, Object> cmd = null;
            try {
                checkpoint();
                state().setBatchIndex(i);
                cmd = (CommandData<Object, Object>) commandBatch.getCommands().get(i);
                decode(in, cmd, null, ctx.channel());
                i++;
            } catch (IOException e) {
                cmd.tryFailure(e);
            }
            if (!cmd.isSuccess()) {
                error = (RedisException) cmd.cause();
            }
        }

        if (i == commandBatch.getCommands().size()) {
            Promise<Void> promise = commandBatch.getPromise();
            if (error != null) {
                if (!promise.tryFailure(error) && promise.cause() instanceof RedisTimeoutException) {
                    log.warn("response has been skipped due to timeout! channel: {}, command: {}", ctx.channel(), data);
                }
            } else {
                if (!promise.trySuccess(null) && promise.cause() instanceof RedisTimeoutException) {
                    log.warn("response has been skipped due to timeout! channel: {}, command: {}", ctx.channel(), data);
                }
            }
            
            ctx.pipeline().get(CommandsQueue.class).sendNextCommand(ctx.channel());

            state(null);
        } else {
            checkpoint();
            state().setBatchIndex(i);
        }
    }

    private void decode(ByteBuf in, CommandData<Object, Object> data, List<Object> parts, Channel channel) throws IOException {
        int code = in.readByte();
        if (code == '+') {
            ByteBuf rb = in.readBytes(in.bytesBefore((byte) '\r'));
            try {
                String result = rb.toString(CharsetUtil.UTF_8);
                in.skipBytes(2);

                handleResult(data, parts, result, false, channel);
            } finally {
                rb.release();
            }
        } else if (code == '-') {
            ByteBuf rb = in.readBytes(in.bytesBefore((byte) '\r'));
            try {
                String error = rb.toString(CharsetUtil.UTF_8);
                in.skipBytes(2);

                if (error.startsWith("MOVED")) {
                    String[] errorParts = error.split(" ");
                    int slot = Integer.valueOf(errorParts[1]);
                    String addr = errorParts[2];
                    data.tryFailure(new RedisMovedException(slot, addr));
                } else if (error.startsWith("ASK")) {
                    String[] errorParts = error.split(" ");
                    int slot = Integer.valueOf(errorParts[1]);
                    String addr = errorParts[2];
                    data.tryFailure(new RedisAskException(slot, addr));
                } else if (error.startsWith("TRYAGAIN")) {
                    data.tryFailure(new RedisTryAgainException(error
                            + ". channel: " + channel + " data: " + data));
                } else if (error.startsWith("LOADING")) {
                    data.tryFailure(new RedisLoadingException(error
                            + ". channel: " + channel + " data: " + data));
                } else if (error.startsWith("OOM")) {
                    data.tryFailure(new RedisOutOfMemoryException(error.split("OOM ")[1]
                            + ". channel: " + channel + " data: " + data));
                } else if (error.contains("-OOM ")) {
                    data.tryFailure(new RedisOutOfMemoryException(error.split("-OOM ")[1]
                            + ". channel: " + channel + " data: " + data));
                } else {
                    if (data != null) {
                        data.tryFailure(new RedisException(error + ". channel: " + channel + " command: " + data));
                    } else {
                        log.error("Error: {} channel: {} data: {}", error, channel, data);
                    }
                }
            } finally {
                rb.release();
            }
        } else if (code == ':') {
            Long result = readLong(in);
            handleResult(data, parts, result, false, channel);
        } else if (code == '$') {
            ByteBuf buf = readBytes(in);
            Object result = null;
            if (buf != null) {
                Decoder<Object> decoder = selectDecoder(data, parts);
                result = decoder.decode(buf, state());
            }
            handleResult(data, parts, result, false, channel);
        } else if (code == '*') {
            int level = state().incLevel();
            
            long size = readLong(in);
            List<Object> respParts;
            if (state().getLevels().size()-1 >= level) {
                StateLevel stateLevel = state().getLevels().get(level);
                respParts = stateLevel.getParts();
                size = stateLevel.getSize();
            } else {
                respParts = new ArrayList<Object>();
                if (state().isMakeCheckpoint()) {
                    state().addLevel(new StateLevel(size, respParts));
                }
            }
            
            decodeList(in, data, parts, channel, size, respParts);
        } else {
            throw new IllegalStateException("Can't decode replay " + (char)code);
        }
    }

    private void decodeList(ByteBuf in, CommandData<Object, Object> data, List<Object> parts,
            Channel channel, long size, List<Object> respParts)
                    throws IOException {
        for (int i = respParts.size(); i < size; i++) {
            decode(in, data, respParts, channel);
            if (state().isMakeCheckpoint()) {
                checkpoint();
            }
        }

        MultiDecoder<Object> decoder = messageDecoder(data, respParts, channel);
        if (decoder == null) {
            return;
        }

        Object result = decoder.decode(respParts, state());
        if (data != null) {
            handleResult(data, parts, result, true, channel);
            return;
        }

        if (result instanceof Message) {
            // store current message index
            checkpoint();

            handleMultiResult(data, null, channel, result);
            // has next messages?
            if (in.writerIndex() > in.readerIndex()) {
                decode(in, data, null, channel);
            }
        }
    }

    private void handleMultiResult(CommandData<Object, Object> data, List<Object> parts,
            Channel channel, Object result) {
        if (result instanceof PubSubStatusMessage) {
            String channelName = ((PubSubStatusMessage) result).getChannel();
            String operation = ((PubSubStatusMessage) result).getType().name().toLowerCase();
            PubSubKey key = new PubSubKey(channelName, operation);
            CommandData<Object, Object> d = pubSubChannels.get(key);
            if (Arrays.asList("PSUBSCRIBE", "SUBSCRIBE").contains(d.getCommand().getName())) {
                pubSubChannels.remove(key);
                pubSubMessageDecoders.put(channelName, d.getMessageDecoder());
            }
            if (Arrays.asList("PUNSUBSCRIBE", "UNSUBSCRIBE").contains(d.getCommand().getName())) {
                pubSubChannels.remove(key);
                pubSubMessageDecoders.remove(channelName);
            }
        }

        RedisPubSubConnection pubSubConnection = RedisPubSubConnection.getFrom(channel);
        if (result instanceof PubSubStatusMessage) {
            pubSubConnection.onMessage((PubSubStatusMessage) result);
        } else if (result instanceof PubSubMessage) {
            pubSubConnection.onMessage((PubSubMessage) result);
        } else {
            pubSubConnection.onMessage((PubSubPatternMessage) result);
        }
    }

    private void handleResult(CommandData<Object, Object> data, List<Object> parts, Object result, boolean multiResult, Channel channel) {
        if (data != null) {
            if (multiResult) {
                result = data.getCommand().getConvertor().convertMulti(result);
            } else {
                result = data.getCommand().getConvertor().convert(result);
            }
        }
        if (parts != null) {
            parts.add(result);
        } else {
            if (!data.getPromise().trySuccess(result) && data.cause() instanceof RedisTimeoutException) {
                log.warn("response has been skipped due to timeout! channel: {}, command: {}, result: {}", channel, data, result);
            }
        }
    }

    private MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts, Channel channel) {
        if (data == null) {
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

    private Decoder<Object> selectDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            if (parts != null) {
                if (parts.size() == 2 && "message".equals(parts.get(0))) {
                    String channelName = (String) parts.get(1);
                    return pubSubMessageDecoders.get(channelName);
                }
                if (parts.size() == 3 && "pmessage".equals(parts.get(0))) {
                    String patternName = (String) parts.get(1);
                    return pubSubMessageDecoders.get(patternName);
                }
            }
            return StringCodec.INSTANCE.getValueDecoder();
        }

        Decoder<Object> decoder = data.getCommand().getReplayDecoder();
        if (parts != null) {
            MultiDecoder<Object> multiDecoder = data.getCommand().getReplayMultiDecoder();
            if (multiDecoder.isApplicable(parts.size(), state())) {
                decoder = multiDecoder;
            }
        }
        if (decoder == null) {
            if (data.getCommand().getOutParamType() == ValueType.MAP) {
                if (parts.size() % 2 != 0) {
                    decoder = data.getCodec().getMapValueDecoder();
                } else {
                    decoder = data.getCodec().getMapKeyDecoder();
                }
            } else if (data.getCommand().getOutParamType() == ValueType.MAP_KEY) {
                decoder = data.getCodec().getMapKeyDecoder();
            } else if (data.getCommand().getOutParamType() == ValueType.MAP_VALUE) {
                decoder = data.getCodec().getMapValueDecoder();
            } else {
                decoder = data.getCodec().getValueDecoder();
            }
        }
        return decoder;
    }

    public ByteBuf readBytes(ByteBuf is) throws IOException {
        long l = readLong(is);
        if (l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Java only supports arrays up to " + Integer.MAX_VALUE + " in size");
        }
        int size = (int) l;
        if (size == -1) {
            return null;
        }
        ByteBuf buffer = is.readSlice(size);
        int cr = is.readByte();
        int lf = is.readByte();
        if (cr != CR || lf != LF) {
            throw new IOException("Improper line ending: " + cr + ", " + lf);
        }
        return buffer;
    }

    public static long readLong(ByteBuf is) throws IOException {
        long size = 0;
        int sign = 1;
        int read = is.readByte();
        if (read == '-') {
            read = is.readByte();
            sign = -1;
        }
        do {
            if (read == CR) {
                if (is.readByte() == LF) {
                    break;
                }
            }
            int value = read - ZERO;
            if (value >= 0 && value < 10) {
                size *= 10;
                size += value;
            } else {
                throw new IOException("Invalid character in integer");
            }
            read = is.readByte();
        } while (true);
        return size * sign;
    }

}
