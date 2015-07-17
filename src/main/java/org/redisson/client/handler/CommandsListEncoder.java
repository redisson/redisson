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
import org.redisson.client.protocol.CommandsData;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsListEncoder extends MessageToByteEncoder<CommandsData> {

    @Override
    protected void encode(ChannelHandlerContext ctx, CommandsData msg, ByteBuf out) throws Exception {
        for (CommandData<?, ?> commandData : msg.getCommands()) {
            ctx.pipeline().get(CommandEncoder.class).encode(ctx, (CommandData<Object, Object>)commandData, out);
        }
    }

}
