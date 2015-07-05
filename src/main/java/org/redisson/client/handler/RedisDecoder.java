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

import org.redisson.client.handler.RedisCommandsQueue.QueueCommands;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

public class RedisDecoder extends ReplayingDecoder<Void> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int code = in.readByte();
        String status = in.readBytes(in.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
        in.skipBytes(2);
        out.add(status);
        System.out.println("status: " + status);

        ctx.channel().attr(RedisCommandsQueue.REPLAY_PROMISE).getAndRemove().setSuccess(status);

        ctx.pipeline().fireUserEventTriggered(QueueCommands.NEXT_COMMAND);
//        switch (code) {
//          case StatusReply.MARKER: {
//            String status = is.readBytes(is.bytesBefore((byte) '\r')).toString(Charsets.UTF_8);
//            is.skipBytes(2);
//            return new StatusReply(status);
//          }
//          case ErrorReply.MARKER: {
//            String error = is.readBytes(is.bytesBefore((byte) '\r')).toString(Charsets.UTF_8);
//            is.skipBytes(2);
//            return new ErrorReply(error);
//          }
//          case IntegerReply.MARKER: {
//            return new IntegerReply(readLong(is));
//          }
//          case BulkReply.MARKER: {
//            return new BulkReply(readBytes(is));
//          }
//          case MultiBulkReply.MARKER: {
//            if (reply == null) {
//              return decodeMultiBulkReply(is);
//            } else {
//              return new RedisReplyDecoder(false).decodeMultiBulkReply(is);
//            }
//          }
//          default: {
//            throw new IOException("Unexpected character in stream: " + code);
//          }
//        }
    }

}
