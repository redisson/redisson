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
import java.util.List;

import org.redisson.client.RedisException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.handler.RedisCommandsQueue.QueueCommands;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.MultiDecoder;
import org.redisson.client.protocol.PubSubMessage;
import org.redisson.client.protocol.PubSubPatternMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

public class RedisDecoder extends ReplayingDecoder<Void> {

    public static final char CR = '\r';
    public static final char LF = '\n';
    private static final char ZERO = '0';

    private MultiDecoder<Object> nextDecoder;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        RedisData<Object, Object> data = ctx.channel().attr(RedisCommandsQueue.REPLAY_PROMISE).getAndRemove();
        RedisPubSubConnection pubSubConnection = ctx.channel().attr(RedisPubSubConnection.CONNECTION).get();

        decode(in, data, null, pubSubConnection);

        ctx.pipeline().fireUserEventTriggered(QueueCommands.NEXT_COMMAND);
    }

    private void decode(ByteBuf in, RedisData<Object, Object> data, List<Object> parts, RedisPubSubConnection pubSubConnection) throws IOException {
        int code = in.readByte();
//        System.out.println("trying decode -- " + (char)code);
        if (code == '+') {
            Object result = data.getCommand().getReplayDecoder().decode(in);
            handleResult(data, parts, result);
        } else if (code == '-') {
            Object result = data.getCommand().getReplayDecoder().decode(in);
            data.getPromise().setFailure(new RedisException(result.toString()));
        } else if (code == ':') {
            String status = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);
            Object result = Long.valueOf(status);
            handleResult(data, parts, result);
        } else if (code == '$') {
            Object result = decoder(data, parts != null).decode(readBytes(in));
            handleResult(data, parts, result);
        } else if (code == '*') {
            long size = readLong(in);
            List<Object> respParts = new ArrayList<Object>();
            for (int i = 0; i < size; i++) {
                decode(in, data, respParts, pubSubConnection);
            }

            Object result = ((MultiDecoder<Object>)decoder(data, true)).decode(respParts);
            if (data != null) {
                if (Arrays.asList("PSUBSCRIBE", "SUBSCRIBE").contains(data.getCommand().getName())) {
                    nextDecoder = data.getNextDecoder();
                }
                data.getPromise().setSuccess(result);
            } else {
                if (result instanceof PubSubMessage) {
                    pubSubConnection.onMessage((PubSubMessage) result);
                } else {
                    pubSubConnection.onMessage((PubSubPatternMessage) result);
                }
            }
        } else {
            throw new IllegalStateException("Can't decode replay " + (char)code);
        }
    }

    private void handleResult(RedisData<Object, Object> data, List<Object> parts, Object result) {
        if (data != null) {
            result = data.getCommand().getConvertor().convert(result);
        }
        if (parts != null) {
            parts.add(result);
        } else {
            data.getPromise().setSuccess(result);
        }
    }

    private Decoder<Object> decoder(RedisData<Object, Object> data, boolean isMulti) {
        if (data == null) {
            return nextDecoder;
        }
        Decoder<Object> decoder = data.getCommand().getReplayDecoder();
        if (isMulti) {
            decoder = data.getCommand().getReplayMultiDecoder();
        }
        if (decoder == null) {
            decoder = data.getCodec();
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
