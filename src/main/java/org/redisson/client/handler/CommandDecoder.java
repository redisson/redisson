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
import org.redisson.client.handler.CommandsQueue.QueueCommands;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.decoder.MultiDecoder;
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

/**
 * Code parts from Sam Pullara
 *
 * @author Nikita Koksharov
 *
 */
public class CommandDecoder extends ReplayingDecoder<Void> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final char CR = '\r';
    public static final char LF = '\n';
    private static final char ZERO = '0';

    private final Map<String, MultiDecoder<Object>> messageDecoders = new HashMap<String, MultiDecoder<Object>>();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        CommandData<Object, Object> data = ctx.channel().attr(CommandsQueue.REPLAY).get();

        Decoder<Object> currentDecoder = null;
        if (data == null) {
            currentDecoder = new Decoder<Object>() {
                @Override
                public Object decode(ByteBuf buf) {
                    return buf.toString(CharsetUtil.UTF_8);
                }
            };
        }

        if (log.isTraceEnabled()) {
            log.trace("channel: {} message: {}", ctx.channel(), in.toString(0, in.writerIndex(), CharsetUtil.UTF_8));
        }

        try {
            decode(in, data, null, ctx.channel(), currentDecoder);
        } catch (IOException e) {
            data.getPromise().setFailure(e);
        }

        ctx.channel().attr(CommandsQueue.REPLAY).remove();
        ctx.pipeline().fireUserEventTriggered(QueueCommands.NEXT_COMMAND);
    }

    private void decode(ByteBuf in, CommandData<Object, Object> data, List<Object> parts, Channel channel, Decoder<Object> currentDecoder) throws IOException {
        int code = in.readByte();
        if (code == '+') {
            String result = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);

            handleResult(data, parts, result, false);
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
                data.getPromise().setFailure(new RedisException(error));
            }
        } else if (code == ':') {
            String status = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);
            Object result = Long.valueOf(status);
            handleResult(data, parts, result, false);
        } else if (code == '$') {
            ByteBuf buf = readBytes(in);
            Object result = null;
            if (buf != null) {
                result = decoder(data, parts, currentDecoder).decode(buf);
            }
            handleResult(data, parts, result, false);
        } else if (code == '*') {
            long size = readLong(in);
            List<Object> respParts = new ArrayList<Object>();
            for (int i = 0; i < size; i++) {
                decode(in, data, respParts, channel, currentDecoder);
            }

            Object result = messageDecoder(data, respParts).decode(respParts);
            if (result instanceof PubSubStatusMessage) {
                if (parts == null) {
                    parts = new ArrayList<Object>();
                }
                parts.add(result);
                // has next status messages
                if (in.writerIndex() > in.readerIndex()) {
                    decode(in, data, parts, channel, currentDecoder);
                } else {
                    handleMultiResult(data, null, channel, parts);
                }
            } else {
                handleMultiResult(data, parts, channel, result);
            }
        } else {
            throw new IllegalStateException("Can't decode replay " + (char)code);
        }
    }

    private void handleMultiResult(CommandData<Object, Object> data, List<Object> parts,
            Channel channel, Object result) {
        if (data != null) {
            if (Arrays.asList("PSUBSCRIBE", "SUBSCRIBE").contains(data.getCommand().getName())) {
                for (Object param : data.getParams()) {
                    messageDecoders.put(param.toString(), data.getMessageDecoder());
                }
            }
            if (Arrays.asList("PUNSUBSCRIBE", "UNSUBSCRIBE").contains(data.getCommand().getName())) {
                for (Object param : data.getParams()) {
                    messageDecoders.remove(param.toString());
                }
            }

            handleResult(data, parts, result, true);
        } else {
            RedisPubSubConnection pubSubConnection = (RedisPubSubConnection)channel.attr(RedisPubSubConnection.CONNECTION).get();
            if (result instanceof PubSubMessage) {
                pubSubConnection.onMessage((PubSubMessage) result);
            } else {
                pubSubConnection.onMessage((PubSubPatternMessage) result);
            }
        }
    }

    private void handleResult(CommandData<Object, Object> data, List<Object> parts, Object result, boolean multiResult) {
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
            data.getPromise().setSuccess(result);
        }
    }

    private MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            if (parts.get(0).equals("message")) {
                String channelName = (String) parts.get(1);
                return messageDecoders.get(channelName);
            }
            if (parts.get(0).equals("pmessage")) {
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
            if (multiDecoder.isApplicable(parts.size())) {
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
