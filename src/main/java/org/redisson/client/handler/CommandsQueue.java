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

import java.util.List;
import java.util.Queue;

import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.QueueCommandHolder;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsQueue extends ChannelDuplexHandler {

    public static final AttributeKey<QueueCommand> REPLAY = AttributeKey.valueOf("promise");

    private final Queue<QueueCommandHolder> queue = PlatformDependent.newMpscQueue();

    public void sendNextCommand(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(CommandsQueue.REPLAY).remove();
        queue.poll();
        sendData(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof QueueCommand) {
            QueueCommand data = (QueueCommand) msg;
            if (queue.peek() != null && queue.peek().getCommand() == data) {
                super.write(ctx, msg, promise);
            } else {
                queue.add(new QueueCommandHolder(data, promise));
                sendData(ctx);
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void sendData(final ChannelHandlerContext ctx) throws Exception {
        QueueCommandHolder command = queue.peek();
        if (command != null && command.getSended().compareAndSet(false, true)) {
            QueueCommand data = command.getCommand();
            List<CommandData<Object, Object>> pubSubOps = data.getPubSubOperations();
            if (!pubSubOps.isEmpty()) {
                for (CommandData<Object, Object> cd : pubSubOps) {
                    for (Object channel : cd.getParams()) {
                        ctx.pipeline().get(CommandDecoder.class).addChannel(channel.toString(), cd);
                    }
                }
            } else {
                ctx.channel().attr(REPLAY).set(data);
            }
            command.getChannelPromise().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        sendNextCommand(ctx);
                    }
                }
            });
            ctx.channel().writeAndFlush(data, command.getChannelPromise());
        }
    }

}
