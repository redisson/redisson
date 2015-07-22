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

import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.Encoder;
import org.redisson.client.protocol.StringParamsEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.client.protocol.RedisCommand.ValueType;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
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
public class CommandEncoder extends MessageToByteEncoder<CommandData<Object, Object>> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Encoder paramsEncoder = new StringParamsEncoder();

    final char ARGS_PREFIX = '*';
    final char BYTES_PREFIX = '$';
    final byte[] CRLF = "\r\n".getBytes();

    @Override
    protected void encode(ChannelHandlerContext ctx, CommandData<Object, Object> msg, ByteBuf out) throws Exception {
        out.writeByte(ARGS_PREFIX);
        int len = 1 + msg.getParams().length;
        if (msg.getCommand().getSubName() != null) {
            len++;
        }
        out.writeBytes(toChars(len));
        out.writeBytes(CRLF);

        writeArgument(out, msg.getCommand().getName().getBytes("UTF-8"));
        if (msg.getCommand().getSubName() != null) {
            writeArgument(out, msg.getCommand().getSubName().getBytes("UTF-8"));
        }
        int i = 1;
        for (Object param : msg.getParams()) {
            Encoder encoder = paramsEncoder;
            if (msg.getCommand().getInParamType().size() == 1) {
                if (msg.getCommand().getInParamIndex() == i && msg.getCommand().getInParamType().get(0) == ValueType.OBJECT) {
                    encoder = msg.getCodec().getValueEncoder();
                } else if (msg.getCommand().getInParamIndex() <= i && msg.getCommand().getInParamType().get(0) != ValueType.OBJECT) {
                    encoder = encoder(msg, i - msg.getCommand().getInParamIndex());
                }
            } else {
                int paramNum = i - msg.getCommand().getInParamIndex();
                if (msg.getCommand().getInParamIndex() <= i) {
                    encoder = encoder(msg, paramNum);
                }
            }

            writeArgument(out, encoder.encode(param));

            i++;
        }

        if (log.isTraceEnabled()) {
            log.trace("channel: {} message: {}", ctx.channel(), out.toString(CharsetUtil.UTF_8));
        }
    }

    private Encoder encoder(CommandData<Object, Object> msg, int param) {
        int typeIndex = 0;
        if (msg.getCommand().getInParamType().size() > 1) {
            typeIndex = param;
        }
        if (msg.getCommand().getInParamType().get(typeIndex) == ValueType.MAP) {
            if (param % 2 != 0) {
                return msg.getCodec().getMapValueEncoder();
            } else {
                return msg.getCodec().getMapKeyEncoder();
            }
        }
        if (msg.getCommand().getInParamType().get(typeIndex) == ValueType.MAP_KEY) {
            return msg.getCodec().getMapKeyEncoder();
        }
        if (msg.getCommand().getInParamType().get(typeIndex) == ValueType.MAP_VALUE) {
            return msg.getCodec().getMapValueEncoder();
        }
        if (msg.getCommand().getInParamType().get(typeIndex) == ValueType.OBJECTS) {
            return msg.getCodec().getValueEncoder();
        }
        throw new IllegalStateException();
    }

    private void writeArgument(ByteBuf out, byte[] arg) {
        out.writeByte(BYTES_PREFIX);
        out.writeBytes(toChars(arg.length));
        out.writeBytes(CRLF);
        out.writeBytes(arg);
        out.writeBytes(CRLF);
    }

    final static char[] DigitTens = {'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1',
            '1', '1', '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3', '3',
            '3', '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5',
            '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6', '6', '7',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
            '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',};

    final static char[] DigitOnes = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3',
            '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2',
            '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1',
            '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',};

    final static char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
            'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
            'y', 'z'};

    final static int[] sizeTable = {9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999,
            Integer.MAX_VALUE};

    // Requires positive x
    static int stringSize(long x) {
        for (int i = 0;; i++)
            if (x <= sizeTable[i])
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
            buf[--charPos] = (byte) DigitOnes[(int)r];
            buf[--charPos] = (byte) DigitTens[(int)r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (;;) {
            q = (i * 52429) >>> (16 + 3);
            r = i - ((q << 3) + (q << 1)); // r = i-(q*10) ...
            buf[--charPos] = (byte) digits[(int)r];
            i = q;
            if (i == 0)
                break;
        }
        if (sign != 0) {
            buf[--charPos] = sign;
        }
    }

    public static byte[] toChars(long i) {
        int size = (i < 0) ? stringSize(-i) + 1 : stringSize(i);
        byte[] buf = new byte[size];
        getChars(i, size, buf);
        return buf;
    }


}
