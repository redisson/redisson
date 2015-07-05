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
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

public class RedisCommandsQueue extends ChannelDuplexHandler {

    public enum QueueCommands {NEXT_COMMAND}

    public static final AttributeKey<Promise<Object>> REPLAY_PROMISE = AttributeKey.valueOf("promise");

    private final Queue<RedisData<Object>> queue = PlatformDependent.newMpscQueue();

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
        if (msg instanceof RedisData) {
            RedisData<Object> data = (RedisData<Object>) msg;
            if (data.getSend().get()) {
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
        RedisData<Object> data = queue.peek();
        if (data != null && data.getSend().compareAndSet(false, true)) {
            ctx.channel().attr(REPLAY_PROMISE).set(data.getPromise());
            ctx.channel().writeAndFlush(data);
        }
    }

}
