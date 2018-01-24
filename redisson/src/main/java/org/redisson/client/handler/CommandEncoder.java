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

import java.util.HashMap;
import java.util.Map;

import org.redisson.client.protocol.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

/**
 * Redis protocol command encoder
 *
 * Code parts from Sam Pullara
 *
 * @author Nikita Koksharov
 *
 */
@Sharable
public class CommandEncoder extends MessageToByteEncoder<CommandData<?, ?>> {

    public static final CommandEncoder INSTANCE = new CommandEncoder();
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final char ARGS_PREFIX = '*';
    private static final char BYTES_PREFIX = '$';
    private static final byte[] CRLF = "\r\n".getBytes();

    private static final Map<Long, byte[]> longCache = new HashMap<Long, byte[]>();
    
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (acceptOutboundMessage(msg)) {
            if (!promise.setUncancellable()) {
                return;
            }
        }

        try {
            super.write(ctx, msg, promise);
        } catch (Exception e) {
            promise.tryFailure(e);
            throw e;
        }
    }
    
    @Override
    protected void encode(ChannelHandlerContext ctx, CommandData<?, ?> msg, ByteBuf out) throws Exception {
        try {
            out.writeByte(ARGS_PREFIX);
            int len = 1 + msg.getParams().length;
            if (msg.getCommand().getSubName() != null) {
                len++;
            }
            out.writeBytes(convert(len));
            out.writeBytes(CRLF);
            
            writeArgument(out, msg.getCommand().getName().getBytes(CharsetUtil.UTF_8));
            if (msg.getCommand().getSubName() != null) {
                writeArgument(out, msg.getCommand().getSubName().getBytes(CharsetUtil.UTF_8));
            }

            for (Object param : msg.getParams()) {
                ByteBuf buf = encode(param);
                writeArgument(out, buf);
                if (!(param instanceof ByteBuf)) {
                    buf.release();
                }
            }
            
            if (log.isTraceEnabled()) {
                log.trace("channel: {} message: {}", ctx.channel(), out.toString(CharsetUtil.UTF_8));
            }
        } catch (Exception e) {
            msg.tryFailure(e);
            throw e;
        }
    }

    private ByteBuf encode(Object in) {
        if (in instanceof byte[]) {
            byte[] payload = (byte[])in;
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer(payload.length);
            out.writeBytes(payload);
            return out;
        }
        if (in instanceof ByteBuf) {
            return (ByteBuf) in;
        }

        String payload = in.toString();
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(ByteBufUtil.utf8MaxBytes(payload));
        ByteBufUtil.writeUtf8(buf, payload);
        return buf;
    }
    
    private void writeArgument(ByteBuf out, byte[] arg) {
        out.writeByte(BYTES_PREFIX);
        out.writeBytes(convert(arg.length));
        out.writeBytes(CRLF);
        out.writeBytes(arg);
        out.writeBytes(CRLF);
    }
    
    private void writeArgument(ByteBuf out, ByteBuf arg) {
        out.writeByte(BYTES_PREFIX);
        out.writeBytes(convert(arg.readableBytes()));
        out.writeBytes(CRLF);
        out.writeBytes(arg, arg.readerIndex(), arg.readableBytes());
        out.writeBytes(CRLF);
    }


    static final char[] DIGITTENS = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1', '1', '1',
            '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3', '3', '3', '3', '3', '3',
            '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5', '5', '5', '5', '5', '5', '5',
            '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6', '6', '7', '7', '7', '7', '7', '7', '7', '7', '7',
            '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', };

    static final char[] DIGITONES = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', };

    static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };

    static final int[] SIZETABLE = { 9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE };

    // Requires positive x
    static int stringSize(long x) {
        for (int i = 0;; i++)
            if (x <= SIZETABLE[i])
                return i + 1;
    }

    static void getChars(long i, int index, byte[] buf) {
        long q, r;
        int charPos = index;
        byte sign = 0;

        if (i < 0) {
            sign = '-';
            i = -i;
        }

        // Generate two digits per iteration
        while (i >= 65536) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = i - ((q << 6) + (q << 5) + (q << 2));
            i = q;
            buf[--charPos] = (byte) DIGITONES[(int) r];
            buf[--charPos] = (byte) DIGITTENS[(int) r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (;;) {
            q = (i * 52429) >>> (16 + 3);
            r = i - ((q << 3) + (q << 1)); // r = i-(q*10) ...
            buf[--charPos] = (byte) DIGITS[(int) r];
            i = q;
            if (i == 0)
                break;
        }
        if (sign != 0) {
            buf[--charPos] = sign;
        }
    }

    public static byte[] convert(long i) {
        if (i >= 0 && i <= 255) {
            return longCache.get(i);
        }
        return toChars(i);
    }
    
    public static byte[] toChars(long i) {
        int size = (i < 0) ? stringSize(-i) + 1 : stringSize(i);
        byte[] buf = new byte[size];
        getChars(i, size, buf);
        return buf;
    }

    static {
        for (long i = 0; i < 256; i++) {
            byte[] value = toChars(i);
            longCache.put(i, value);
        }
    }
    

}
