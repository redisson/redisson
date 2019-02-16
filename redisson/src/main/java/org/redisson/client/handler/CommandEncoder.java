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
/**
 * Copyright 2012 Sam Pullara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.redisson.client.handler;

import org.redisson.client.ChannelName;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

/**
 * Redis protocol command encoder
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
            out.writeCharSequence(Long.toString(len), CharsetUtil.US_ASCII);
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
                String info = out.toString(CharsetUtil.UTF_8);
                if (RedisCommands.AUTH.equals(msg.getCommand())) {
                    info = info.substring(0, info.indexOf(RedisCommands.AUTH.getName()) + RedisCommands.AUTH.getName().length()) + "(password masked)";
                }
                log.trace("channel: {} message: {}", ctx.channel(), info);
            }
        } catch (Exception e) {
            msg.tryFailure(e);
            throw e;
        }
    }

    private ByteBuf encode(Object in) {
        if (in instanceof byte[]) {
            return Unpooled.wrappedBuffer((byte[]) in);
        }
        if (in instanceof ByteBuf) {
            return (ByteBuf) in;
        }
        if (in instanceof ChannelName) {
            return Unpooled.wrappedBuffer(((ChannelName) in).getName());
        }

        String payload = in.toString();
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(ByteBufUtil.utf8MaxBytes(payload));
        ByteBufUtil.writeUtf8(buf, payload);
        return buf;
    }
    
    private void writeArgument(ByteBuf out, byte[] arg) {
        out.writeByte(BYTES_PREFIX);
        out.writeCharSequence(Long.toString(arg.length), CharsetUtil.US_ASCII);
        out.writeBytes(CRLF);
        out.writeBytes(arg);
        out.writeBytes(CRLF);
    }
    
    private void writeArgument(ByteBuf out, ByteBuf arg) {
        out.writeByte(BYTES_PREFIX);
        out.writeCharSequence(Long.toString(arg.readableBytes()), CharsetUtil.US_ASCII);
        out.writeBytes(CRLF);
        out.writeBytes(arg, arg.readerIndex(), arg.readableBytes());
        out.writeBytes(CRLF);
    }

}
