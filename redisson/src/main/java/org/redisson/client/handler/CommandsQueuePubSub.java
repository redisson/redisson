/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import io.netty.channel.*;
import io.netty.util.AttributeKey;
import org.redisson.client.ChannelName;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.QueueCommandHolder;
import org.redisson.misc.LogHelper;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsQueuePubSub extends ChannelDuplexHandler {

    public static final AttributeKey<QueueCommand> CURRENT_COMMAND = AttributeKey.valueOf("promise");

    private final Queue<QueueCommandHolder> queue = new ConcurrentLinkedQueue<>();

    private final ChannelFutureListener listener = future -> {
        if (!future.isSuccess() && future.channel().isActive()) {
            sendNextCommand(future.channel());
        }
    };

    public void sendNextCommand(Channel channel) {
        QueueCommand command = channel.attr(CommandsQueuePubSub.CURRENT_COMMAND).getAndSet(null);
        if (command != null) {
            queue.poll();
        } else {
            QueueCommandHolder c = queue.peek();
            if (c != null) {
                QueueCommand data = c.getCommand();
                List<CommandData<Object, Object>> pubSubOps = data.getPubSubOperations();
                if (!pubSubOps.isEmpty()) {
                    queue.poll();
                }
            }
        }
        sendData(channel);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        while (true) {
            QueueCommandHolder command = queue.poll();
            if (command == null) {
                break;
            }

            command.getChannelPromise().tryFailure(
                    new WriteRedisConnectionException("Channel has been closed! Can't write command: "
                                + LogHelper.toString(command.getCommand()) + " to channel: " + ctx.channel()));
        }

        super.channelInactive(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof QueueCommand) {
            QueueCommand data = (QueueCommand) msg;
            QueueCommandHolder holder = queue.peek();
            if (holder != null && holder.getCommand() == data) {
                super.write(ctx, msg, promise);
            } else {
                queue.add(new QueueCommandHolder(data, promise));
                sendData(ctx.channel());
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void sendData(Channel ch) {
        QueueCommandHolder command = queue.peek();
        if (command != null && command.trySend()) {
            QueueCommand data = command.getCommand();
            List<CommandData<Object, Object>> pubSubOps = data.getPubSubOperations();
            if (!pubSubOps.isEmpty()) {
                for (CommandData<Object, Object> cd : pubSubOps) {
                    for (Object channel : cd.getParams()) {
                        ch.pipeline().get(CommandPubSubDecoder.class).addPubSubCommand((ChannelName) channel, cd);
                    }
                }
            } else {
                ch.attr(CURRENT_COMMAND).set(data);
            }

            command.getChannelPromise().addListener(listener);
            ch.writeAndFlush(data, command.getChannelPromise());
        }
    }

}
