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

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Queue;

import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.QueueCommandHolder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsQueue extends ChannelOutboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandsQueue.class);

    private static final Pattern IGNORABLE_ERROR_MESSAGE = 
              Pattern.compile("^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

    public static final AttributeKey<QueueCommand> CURRENT_COMMAND = AttributeKey.valueOf("promise");

    private final Queue<QueueCommandHolder> queue = PlatformDependent.newMpscQueue();

    private final ChannelFutureListener listener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                sendNextCommand(future.channel());
            }
        }
    };

    public void sendNextCommand(Channel channel) {
        channel.attr(CommandsQueue.CURRENT_COMMAND).remove();
        queue.poll();
        sendData(channel);
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
                        ch.pipeline().get(CommandDecoder.class).addPubSubCommand(channel.toString(), cd);
                    }
                }
            } else {
                ch.attr(CURRENT_COMMAND).set(data);
            }

            command.getChannelPromise().addListener(listener);
            ch.writeAndFlush(data, command.getChannelPromise());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            String message = String.valueOf(cause.getMessage()).toLowerCase();
            if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
                return;
            }
        }

//        QueueCommand command = ctx.channel().attr(CommandsQueue.CURRENT_COMMAND).get();
//        if (command != null) {
//            if (!command.tryFailure(cause)) {
//                log.error("Exception occured. Channel: " + ctx.channel() + " Command: " + command, cause);
//            }
//            sendNextCommand(ctx.channel());
//            return;
//        }
        log.error("Exception occured. Channel: " + ctx.channel(), cause);
    }

}
