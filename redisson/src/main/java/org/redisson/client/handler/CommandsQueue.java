/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.QueueCommandHolder;
import org.redisson.misc.LogHelper;

import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsQueue extends ChannelDuplexHandler {

    public static final AttributeKey<Deque<QueueCommandHolder>> COMMANDS_QUEUE = AttributeKey.valueOf("COMMANDS_QUEUE");

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        ctx.channel().attr(COMMANDS_QUEUE).set(new ConcurrentLinkedDeque<>());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Queue<QueueCommandHolder> queue = ctx.channel().attr(COMMANDS_QUEUE).get();
        Iterator<QueueCommandHolder> iterator = queue.iterator();
        while (iterator.hasNext()) {
            QueueCommandHolder command = iterator.next();
            if (command.getCommand().isBlockingCommand()) {
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
                        try {
                            holder.getChannelPromise().addListener(future -> {
                                if (!future.isSuccess()) {
                                    queue.remove(holder);
                                }
                            });
                            ctx.writeAndFlush(data, holder.getChannelPromise());
                        } catch (Exception e) {
                            queue.remove(holder);
                            throw e;
                        }
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
