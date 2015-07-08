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
import java.util.List;

import org.redisson.client.RedisException;
import org.redisson.client.handler.RedisCommandsQueue.QueueCommands;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

public class RedisDecoder extends ReplayingDecoder<Void> {

    public static final char CR = '\r';
    public static final char LF = '\n';
    private static final char ZERO = '0';

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        RedisData<Object, Object> data = ctx.channel().attr(RedisCommandsQueue.REPLAY_PROMISE).getAndRemove();

        decode(in, data, null);

        ctx.pipeline().fireUserEventTriggered(QueueCommands.NEXT_COMMAND);
    }

    private void decode(ByteBuf in, RedisData<Object, Object> data, List<Object> parts) throws IOException {
        int code = in.readByte();
        if (code == '+') {
            Object result = data.getCommand().getReponseDecoder().decode(in);
            if (parts != null) {
                parts.add(result);
            } else {
                data.getPromise().setSuccess(result);
            }
        } else if (code == '-') {
            Object result = data.getCommand().getReponseDecoder().decode(in);
            data.getPromise().setFailure(new RedisException(result.toString()));
        } else if (code == ':') {
            String status = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
            in.skipBytes(2);
            Long result = Long.valueOf(status);
            if (parts != null) {
                parts.add(result);
            } else {
                data.getPromise().setSuccess(result);
            }
        } else if (code == '$') {
            Decoder<Object> decoder = data.getCommand().getReponseDecoder();
            if (decoder == null) {
                decoder = data.getCodec();
            }
            Object result = decoder.decode(readBytes(in));
            if (parts != null) {
                parts.add(result);
            } else {
                data.getPromise().setSuccess(result);
            }
        } else if (code == '*') {
            long size = readLong(in);
            List<Object> respParts = new ArrayList<Object>();
            for (int i = 0; i < size; i++) {
                decode(in, data, respParts);
            }

            Decoder<Object> decoder = data.getCommand().getReponseDecoder();
            if (decoder == null) {
                decoder = data.getCodec();
            }
            Object result = decoder.decode(respParts);
            data.getPromise().setSuccess(result);
        } else {
            throw new IllegalStateException("Can't decode replay " + (char)code);
        }
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
