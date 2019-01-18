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

import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Sharable
public class CommandBatchEncoder extends MessageToByteEncoder<CommandsData> {

    public static final CommandBatchEncoder INSTANCE = new CommandBatchEncoder();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (acceptOutboundMessage(msg)) {
            if (!promise.setUncancellable()) {
                return;
            }
        }

        super.write(ctx, msg, promise);
    }
    
    @Override
    protected void encode(ChannelHandlerContext ctx, CommandsData msg, ByteBuf out) throws Exception {
        CommandEncoder encoder = ctx.pipeline().get(CommandEncoder.class);
        for (CommandData<?, ?> commandData : msg.getCommands()) {
            encoder.encode(ctx, commandData, out);
        }
    }

}
