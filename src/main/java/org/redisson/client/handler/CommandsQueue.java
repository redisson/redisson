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

import java.util.Queue;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

public class CommandsQueue extends ChannelDuplexHandler {

    public enum QueueCommands {NEXT_COMMAND}

    public static final AttributeKey<CommandData<Object, Object>> REPLAY = AttributeKey.valueOf("promise");

    private final Queue<CommandData<Object, Object>> queue = PlatformDependent.newMpscQueue();

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == QueueCommands.NEXT_COMMAND) {
            queue.poll();
            sendData(ctx);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof CommandData) {
            CommandData<Object, Object> data = (CommandData<Object, Object>) msg;
            if (data.getSended().get()) {
                super.write(ctx, msg, promise);
            } else {
                queue.add(data);
                sendData(ctx);
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void sendData(ChannelHandlerContext ctx) throws Exception {
        CommandData<Object, Object> data = queue.peek();
        if (data != null && data.getSended().compareAndSet(false, true)) {
            ctx.channel().attr(REPLAY).set(data);
            ctx.channel().writeAndFlush(data);
        }
    }

}
