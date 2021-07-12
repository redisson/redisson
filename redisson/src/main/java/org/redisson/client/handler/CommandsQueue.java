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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.protocol.*;
import org.redisson.misc.LogHelper;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsQueue extends ChannelDuplexHandler {

    public static final AttributeKey<Queue<QueueCommandHolder>> COMMANDS_QUEUE = AttributeKey.valueOf("COMMANDS_QUEUE");

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        super.connect(ctx, remoteAddress, localAddress, promise);

        ctx.channel().attr(COMMANDS_QUEUE).set(new ConcurrentLinkedQueue<>());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Queue<QueueCommandHolder> queue = ctx.channel().attr(COMMANDS_QUEUE).get();
        Iterator<QueueCommandHolder> iterator = queue.iterator();
        while (iterator.hasNext()) {
            QueueCommandHolder command = iterator.next();

            CommandData cc = (CommandData) command.getCommand();
            RedisCommand cmd = cc.getCommand();
            if (RedisCommands.BLOCKING_COMMAND_NAMES.contains(cmd.getName())
                || RedisCommands.BLOCKING_COMMANDS.contains(cmd)) {
                continue;
            }

            iterator.remove();
            command.getChannelPromise().tryFailure(
                    new WriteRedisConnectionException("Channel has been closed! Can't write command: "
                                + LogHelper.toString(command.getCommand()) + " to channel: " + ctx.channel()));
        }

        super.channelInactive(ctx);
    }

    private final AtomicBoolean lock = new AtomicBoolean();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof QueueCommand) {
            QueueCommand data = (QueueCommand) msg;
            QueueCommandHolder holder = new QueueCommandHolder(data, promise);

            Queue<QueueCommandHolder> queue = ctx.channel().attr(COMMANDS_QUEUE).get();

            while (true) {
                if (lock.compareAndSet(false, true)) {
                    try {
                        queue.add(holder);
                        ctx.writeAndFlush(data, holder.getChannelPromise());
                    } finally {
                        lock.set(false);
                    }
                    break;
                }
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

}
