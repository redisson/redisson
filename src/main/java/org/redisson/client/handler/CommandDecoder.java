/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import org.redisson.client.RedisException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.decoder.MultiDecoder;
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

    // no need concurrent map responses are coming consecutive
    private final Map<String, MultiDecoder<Object>> messageDecoders = new HashMap<String, MultiDecoder<Object>>();
    private final Map<String, CommandData<Object, Object>> channels = PlatformDependent.newConcurrentHashMap();

    public void addChannel(String channel, CommandData<Object, Object> data) {
        channels.put(channel, data);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        QueueCommand data = ctx.channel().attr(CommandsQueue.REPLAY).get();

        Decoder<Object> currentDecoder = null;
        if (data == null) {
            currentDecoder = new Decoder<Object>() {
                @Override
                public Object decode(ByteBuf buf, State state) {
                    return buf.toString(CharsetUtil.UTF_8);
                }
            };
        }

        if (log.isTraceEnabled()) {
            log.trace("channel: {} message: {}", ctx.channel(), in.toString(0, in.writerIndex(), CharsetUtil.UTF_8));
        }

        if (state() == null) {
            state(new State());
        }
        state().setDecoderState(null);

        if (data == null) {
              decode(in, null, null, ctx.channel(), currentDecoder);
        } else if (data instanceof CommandData) {
            CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
            try {
//                if (state().getSize() > 0) {
//                    decodeMulti(in, cmd, null, ctx.channel(), currentDecoder, state().getSize(), state().getRespParts());
//                } else {
                    decode(in, cmd, null, ctx.channel(), currentDecoder);
//                }
            } catch (IOException e) {
                cmd.getPromise().setFailure(e);
            }
        } else if (data instanceof CommandsData) {
            CommandsData commands = (CommandsData)data;

            int i = state().getIndex();

            while (in.writerIndex() > in.readerIndex()) {
                CommandData<Object, Object> cmd = null;
                try {
                    checkpoint();
                    state().setIndex(i);
                    cmd = (CommandData<Object, Object>) commands.getCommands().get(i);
                    decode(in, cmd, null, ctx.channel(), currentDecoder);
                    i++;
                } catch (IOException e) {
                    cmd.getPromise().setFailure(e);
                }
            }

            if (i == commands.getCommands().size()) {
                Promise<Void> promise = commands.getPromise();
                if (!promise.trySuccess(null) && promise.isCancelled()) {
                    log.warn("response has been skipped due to timeout! channel: {}, command: {}", ctx.channel(), data);
                }

                ctx.pipeline().get(CommandsQueue.class).sendNextCommand(ctx);

                state(null);
            } else {
                checkpoint();
                state().setIndex(i);
            }
            return;
        }

        ctx.pipeline().get(CommandsQueue.class).sendNextCommand(ctx);

        state(null);
    }

    private void decode(ByteBuf in, CommandData<Object, Object> data, List<Object> parts, Channel channel, Decoder<Object> currentDecoder) throws IOException {
        int code = in.readByte();
        if (code == '+') {
            String result = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);

            handleResult(data, parts, result, false, channel);
        } else if (code == '-') {
            String error = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);

            if (error.startsWith("MOVED")) {
                String[] errorParts = error.split(" ");
                int slot = Integer.valueOf(errorParts[1]);
                data.getPromise().setFailure(new RedisMovedException(slot));
            } else if (error.startsWith("(error) ASK")) {
                String[] errorParts = error.split(" ");
                int slot = Integer.valueOf(errorParts[2]);
                data.getPromise().setFailure(new RedisMovedException(slot));
            } else {
                data.getPromise().setFailure(new RedisException(error + ". channel: " + channel));
            }
        } else if (code == ':') {
            String status = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);
            Object result = Long.valueOf(status);
            handleResult(data, parts, result, false, channel);
        } else if (code == '$') {
            ByteBuf buf = readBytes(in);
            Object result = null;
            if (buf != null) {
                result = decoder(data, parts, currentDecoder).decode(buf, state());
            }
            handleResult(data, parts, result, false, channel);
        } else if (code == '*') {
            long size = readLong(in);
//            state().setSize(size);

            List<Object> respParts = new ArrayList<Object>();
//            state().setRespParts(respParts);

            decodeMulti(in, data, parts, channel, currentDecoder, size, respParts);
        } else {
            throw new IllegalStateException("Can't decode replay " + (char)code);
        }
    }

    private void decodeMulti(ByteBuf in, CommandData<Object, Object> data, List<Object> parts,
            Channel channel, Decoder<Object> currentDecoder, long size, List<Object> respParts)
                    throws IOException {
        for (int i = respParts.size(); i < size; i++) {
            decode(in, data, respParts, channel, currentDecoder);
        }

        Object result = messageDecoder(data, respParts).decode(respParts, state());

        if (result instanceof Message) {
            handleMultiResult(data, null, channel, result);
            // has next messages?
            if (in.writerIndex() > in.readerIndex()) {
                decode(in, data, null, channel, currentDecoder);
            }
        } else {
            handleMultiResult(data, parts, channel, result);
        }
    }

    private void handleMultiResult(CommandData<Object, Object> data, List<Object> parts,
            Channel channel, Object result) {
        if (data == null) {
            if (result instanceof PubSubStatusMessage) {
                String channelName = ((PubSubStatusMessage) result).getChannel();
                CommandData<Object, Object> d = channels.get(channelName);
                if (Arrays.asList("PSUBSCRIBE", "SUBSCRIBE").contains(d.getCommand().getName())) {
                    channels.remove(channelName);
                    messageDecoders.put(channelName, d.getMessageDecoder());
                }
                if (Arrays.asList("PUNSUBSCRIBE", "UNSUBSCRIBE").contains(d.getCommand().getName())) {
                    channels.remove(channelName);
                    messageDecoders.remove(channelName);
                }
            }
        }

        if (data != null) {
            handleResult(data, parts, result, true, channel);
        } else {
            RedisPubSubConnection pubSubConnection = (RedisPubSubConnection)channel.attr(RedisPubSubConnection.CONNECTION).get();
            if (result instanceof PubSubStatusMessage) {
                pubSubConnection.onMessage((PubSubStatusMessage) result);
            } else if (result instanceof PubSubMessage) {
                pubSubConnection.onMessage((PubSubMessage) result);
            } else {
                pubSubConnection.onMessage((PubSubPatternMessage) result);
            }
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
            if (!data.getPromise().trySuccess(result) && data.getPromise().isCancelled()) {
                log.warn("response has been skipped due to timeout! channel: {}, command: {}, result: {}", channel, data, result);
            }
        }
    }

    private MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            if (Arrays.asList("subscribe", "psubscribe", "punsubscribe", "unsubscribe").contains(parts.get(0))) {
                String channelName = (String) parts.get(1);
                return channels.get(channelName).getCommand().getReplayMultiDecoder();
            } else if (parts.get(0).equals("message")) {
                String channelName = (String) parts.get(1);
                return messageDecoders.get(channelName);
            } else if (parts.get(0).equals("pmessage")) {
                String patternName = (String) parts.get(1);
                return messageDecoders.get(patternName);
            }
        }

        return data.getCommand().getReplayMultiDecoder();
    }

    private Decoder<Object> decoder(CommandData<Object, Object> data, List<Object> parts, Decoder<Object> currentDecoder) {
        if (data == null) {
            if (parts.size() == 2 && parts.get(0).equals("message")) {
                String channelName = (String) parts.get(1);
                return messageDecoders.get(channelName);
            }
            if (parts.size() == 3 && parts.get(0).equals("pmessage")) {
                String patternName = (String) parts.get(1);
                return messageDecoders.get(patternName);
            }
            return currentDecoder;
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
                    decoder = data.getCodec().getMapKeyDecoder();
                } else {
                    decoder = data.getCodec().getMapValueDecoder();
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
