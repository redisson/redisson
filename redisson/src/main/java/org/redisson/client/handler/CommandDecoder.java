/**
 * Copyright 2018 Nikita Koksharov
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
/**
 * Copyright 2012 Sam Pullara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.redisson.client.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.redisson.client.RedisAskException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.RedisTryAgainException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

/**
 * Redis protocol command decoder
 *
 * @author Nikita Koksharov
 *
 */
public class CommandDecoder extends ReplayingDecoder<State> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char ZERO = '0';

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        QueueCommand data = ctx.channel().attr(CommandsQueue.CURRENT_COMMAND).get();

        if (log.isTraceEnabled()) {
            log.trace("reply: {}, channel: {}, command: {}", in.toString(0, in.writerIndex(), CharsetUtil.UTF_8), ctx.channel(), data);
        }
        if (state() == null) {
            boolean makeCheckpoint = false;
// commented out due to https://github.com/redisson/redisson/issues/1632. Reproduced with RedissonMapCacheTest
//            
//            boolean makeCheckpoint = data != null;
//            if (data != null) {
//                if (data instanceof CommandsData) {
//                    makeCheckpoint = false;
//                } else {
//                    CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
//                    MultiDecoder<Object> decoder = cmd.getCommand().getReplayMultiDecoder();
//                    if (decoder != null 
//                            && (decoder instanceof SlotsDecoder
//                                    || decoder instanceof ListMultiDecoder)) {
//                        makeCheckpoint = false;
//                    }
//                }
//            }
            state(new State(makeCheckpoint));
        }

        state().setDecoderState(null);

        decodeCommand(ctx, in, data);
    }

    protected void sendNext(ChannelHandlerContext ctx, QueueCommand data) {
        if (data != null) {
            if (data.isExecuted()) {
                sendNext(ctx);
            }
        } else {
            sendNext(ctx);
        }
    }

    protected void decodeCommand(ChannelHandlerContext ctx, ByteBuf in, QueueCommand data) throws Exception {
        if (data instanceof CommandData) {
            CommandData<Object, Object> cmd = (CommandData<Object, Object>)data;
            try {
                if (state().isMakeCheckpoint()) {
                    decodeFromCheckpoint(ctx, in, data, cmd);
                } else {
                    decode(in, cmd, null, ctx, false);
                }
                sendNext(ctx, data);
            } catch (Exception e) {
                log.error("Unable to decode data. reply: {}, channel: {}, command: {}", in.toString(0, in.writerIndex(), CharsetUtil.UTF_8), ctx.channel(), data, e);
                cmd.tryFailure(e);
                sendNext(ctx);
                throw e;
            }
        } else if (data instanceof CommandsData) {
            CommandsData commands = (CommandsData)data;
            try {
                decodeCommandBatch(ctx, in, data, commands);
            } catch (Exception e) {
                commands.getPromise().tryFailure(e);
                sendNext(ctx);
                throw e;
            }
        } else {
            try {
                while (in.writerIndex() > in.readerIndex()) {
                    decode(in, null, null, ctx, false);
                }
                sendNext(ctx);
            } catch (Exception e) {
                log.error("Unable to decode data. channel: {} message: {}", ctx.channel(), in.toString(0, in.writerIndex(), CharsetUtil.UTF_8), e);
                sendNext(ctx);
                throw e;
            }
        }
    }

    protected void sendNext(ChannelHandlerContext ctx) {
        ctx.pipeline().get(CommandsQueue.class).sendNextCommand(ctx.channel());
        state(null);
    }

    protected void decodeFromCheckpoint(ChannelHandlerContext ctx, ByteBuf in, QueueCommand data,
            CommandData<Object, Object> cmd) throws IOException {
        StateLevel level = state().getLastLevel();
        
        List<Object> prevParts = null;
        if (state().getLevels().size() > 1) {
            StateLevel prevLevel = state().getLevels().get(state().getLevel() - 1);
            prevParts = prevLevel.getParts();
        }
        
        decodeList(in, cmd, prevParts, ctx, level.getSize(), level.getParts(), false);
        
        if (state().getLastLevel() == level) {
            state().removeLastLevel();
        }
    }
    
    ThreadLocal<List<CommandData<?, ?>>> commandsData = new ThreadLocal<List<CommandData<?, ?>>>();

    private void decodeCommandBatch(ChannelHandlerContext ctx, ByteBuf in, QueueCommand data,
                    CommandsData commandBatch) throws Exception {
        int i = state().getBatchIndex();

        Throwable error = null;
        while (in.writerIndex() > in.readerIndex()) {
            CommandData<Object, Object> commandData = null;
            try {
                checkpoint();
                state().setBatchIndex(i);
                RedisCommand<?> cmd = commandBatch.getCommands().get(i).getCommand();
                boolean skipConvertor = commandBatch.isQueued();
                if (!commandBatch.isAtomic()
                        || RedisCommands.EXEC.getName().equals(cmd.getName())
                        || RedisCommands.WAIT.getName().equals(cmd.getName())) {
                    commandData = (CommandData<Object, Object>) commandBatch.getCommands().get(i);
                    if (RedisCommands.EXEC.getName().equals(cmd.getName())) {
                        skipConvertor = false;
                        if (commandBatch.getAttachedCommands() != null) {
                            commandsData.set(commandBatch.getAttachedCommands());
                        } else {
                            commandsData.set(commandBatch.getCommands());
                        }
                    }
                }
                
                try {
                    decode(in, commandData, null, ctx, skipConvertor);
                } finally {
                    if (commandData != null && RedisCommands.EXEC.getName().equals(commandData.getCommand().getName())) {
                        commandsData.remove();
                    }
                }
                
                if (commandData != null && RedisCommands.EXEC.getName().equals(commandData.getCommand().getName())
                        && commandData.getPromise().isSuccess()) {
                    List<Object> objects = (List<Object>) commandData.getPromise().getNow();
                    Iterator<Object> iter = objects.iterator();
                    boolean multiFound = false; 
                    for (CommandData<?, ?> command : commandBatch.getCommands()) {
                        if (multiFound) {
                            if (!iter.hasNext()) {
                                break;
                            }
                            Object res = iter.next();
                            
                            completeResponse((CommandData<Object, Object>) command, res, ctx.channel());
                        }
                        
                        if (RedisCommands.MULTI.getName().equals(command.getCommand().getName())) {
                            multiFound = true;
                        }
                    }
                }
            } catch (Exception e) {
                if (commandData != null) {
                    commandData.tryFailure(e);
                }
                throw e;
            }
            i++;
            if (commandData != null && !commandData.isSuccess()) {
                error = commandData.cause();
            }
        }

        if (commandBatch.isSkipResult() || i == commandBatch.getCommands().size()) {
            RPromise<Void> promise = commandBatch.getPromise();
            if (error != null) {
                if (!promise.tryFailure(error) && promise.cause() instanceof RedisTimeoutException) {
                    log.warn("response has been skipped due to timeout! channel: {}, command: {}",ctx.channel(), LogHelper.toString(data));
                }
            } else {
                if (!promise.trySuccess(null) && promise.cause() instanceof RedisTimeoutException) {
                    log.warn("response has been skipped due to timeout! channel: {}, command: {}", ctx.channel(), LogHelper.toString(data));
                }
            }
            
            sendNext(ctx);
        } else {
            checkpoint();
            state().setBatchIndex(i);
        }
    }

    protected void decode(ByteBuf in, CommandData<Object, Object> data, List<Object> parts, ChannelHandlerContext ctx, boolean skipConvertor) throws IOException {
        int code = in.readByte();
        Channel channel = ctx.channel();
        if (code == '+') {
            ByteBuf rb = in.readBytes(in.bytesBefore((byte) '\r'));
            try {
                String result = rb.toString(CharsetUtil.UTF_8);
                in.skipBytes(2);

                handleResult(data, parts, result, skipConvertor, channel);
            } finally {
                rb.release();
            }
        } else if (code == '-') {
            ByteBuf rb = in.readBytes(in.bytesBefore((byte) '\r'));
            try {
                String error = rb.toString(CharsetUtil.UTF_8);
                in.skipBytes(2);

                if (error.startsWith("MOVED")) {
                    String[] errorParts = error.split(" ");
                    int slot = Integer.valueOf(errorParts[1]);
                    String addr = errorParts[2];
                    data.tryFailure(new RedisMovedException(slot, addr));
                } else if (error.startsWith("ASK")) {
                    String[] errorParts = error.split(" ");
                    int slot = Integer.valueOf(errorParts[1]);
                    String addr = errorParts[2];
                    data.tryFailure(new RedisAskException(slot, addr));
                } else if (error.startsWith("TRYAGAIN")) {
                    data.tryFailure(new RedisTryAgainException(error
                            + ". channel: " + channel + " data: " + data));
                } else if (error.startsWith("LOADING")) {
                    data.tryFailure(new RedisLoadingException(error
                            + ". channel: " + channel + " data: " + data));
                } else if (error.startsWith("OOM")) {
                    data.tryFailure(new RedisOutOfMemoryException(error.split("OOM ")[1]
                            + ". channel: " + channel + " data: " + data));
                } else if (error.contains("-OOM ")) {
                    data.tryFailure(new RedisOutOfMemoryException(error.split("-OOM ")[1]
                            + ". channel: " + channel + " data: " + data));
                } else {
                    if (data != null) {
                        data.tryFailure(new RedisException(error + ". channel: " + channel + " command: " + LogHelper.toString(data)));
                    } else {
                        log.error("Error message from Redis: {} channel: {}", error, channel);
                    }
                }
            } finally {
                rb.release();
            }
        } else if (code == ':') {
            Long result = readLong(in);
            handleResult(data, parts, result, false, channel);
        } else if (code == '$') {
            ByteBuf buf = readBytes(in);
            Object result = null;
            if (buf != null) {
                Decoder<Object> decoder = selectDecoder(data, parts);
                result = decoder.decode(buf, state());
            }
            handleResult(data, parts, result, false, channel);
        } else if (code == '*') {
            long size = readLong(in);
            final List<Object> respParts = new ArrayList<Object>(Math.max((int)size, 0));
            
            StateLevel lastLevel = null;
            if (state().isMakeCheckpoint()) {
                lastLevel = new StateLevel(size, respParts);
                state().addLevel(lastLevel);
            }
            
            decodeList(in, data, parts, ctx, size, respParts, skipConvertor);
            
            if (state().isMakeCheckpoint()) {
                if (lastLevel == state().getLastLevel() && lastLevel.isFull()) {
                    state().removeLastLevel();
                }
            }
            
        } else {
            String dataStr = in.toString(0, in.writerIndex(), CharsetUtil.UTF_8);
            throw new IllegalStateException("Can't decode replay: " + dataStr);
        }
    }

    @SuppressWarnings("unchecked")
    private void decodeList(ByteBuf in, CommandData<Object, Object> data, List<Object> parts,
            ChannelHandlerContext ctx, long size, List<Object> respParts, boolean skipConvertor)
                    throws IOException {
        if (parts == null && commandsData.get() != null) {
            List<CommandData<?, ?>> commands = commandsData.get();
            for (int i = respParts.size(); i < size; i++) {
                int suffix = 0;
                if (RedisCommands.MULTI.getName().equals(commands.get(0).getCommand().getName())) {
                    suffix = 1;
                }
                CommandData<Object, Object> commandData = (CommandData<Object, Object>) commands.get(i+suffix);
                decode(in, commandData, respParts, ctx, skipConvertor);
                if (commandData.getPromise().isDone() && !commandData.getPromise().isSuccess()) {
                    data.tryFailure(commandData.cause());
                }

                if (state().isMakeCheckpoint()) {
                    checkpoint();
                }
            }
        } else {
            for (int i = respParts.size(); i < size; i++) {
                decode(in, data, respParts, ctx, skipConvertor);
                if (state().isMakeCheckpoint()) {
                    checkpoint();
                }
            }
        }

        MultiDecoder<Object> decoder = messageDecoder(data, respParts);
        if (decoder == null) {
            return;
        }

        Object result = decoder.decode(respParts, state());
        decodeResult(data, parts, ctx, result);
    }

    protected void decodeResult(CommandData<Object, Object> data, List<Object> parts, ChannelHandlerContext ctx,
            Object result) throws IOException {
        if (data != null) {
            handleResult(data, parts, result, true, ctx.channel());
        }
    }

    private void handleResult(CommandData<Object, Object> data, List<Object> parts, Object result, boolean skipConvertor, Channel channel) {
        if (data != null && !skipConvertor) {
            result = data.getCommand().getConvertor().convert(result);
        }
        if (parts != null) {
            parts.add(result);
        } else {
            completeResponse(data, result, channel);
        }
    }

    protected void completeResponse(CommandData<Object, Object> data, Object result, Channel channel) {
        if (data != null && !data.getPromise().trySuccess(result) && data.cause() instanceof RedisTimeoutException) {
            log.warn("response has been skipped due to timeout! channel: {}, command: {}, result: {}", channel, LogHelper.toString(data), LogHelper.toString(result));
        }
    }

    protected MultiDecoder<Object> messageDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            if (parts.isEmpty()) {
                return null;
            }
        }
        return data.getCommand().getReplayMultiDecoder();
    }

    protected Decoder<Object> selectDecoder(CommandData<Object, Object> data, List<Object> parts) {
        if (data == null) {
            return StringCodec.INSTANCE.getValueDecoder();
        }

        if (parts != null) {
            MultiDecoder<Object> multiDecoder = data.getCommand().getReplayMultiDecoder();
            if (multiDecoder != null) {
                Decoder<Object> mDecoder = multiDecoder.getDecoder(parts.size(), state());
                if (mDecoder != null) {
                    return mDecoder;
                }
            }
        }

        Decoder<Object> decoder = data.getCommand().getReplayDecoder();
        if (decoder == null) {
            if (data.getCodec() == null) {
                return StringCodec.INSTANCE.getValueDecoder();
            }
            if (data.getCommand().getOutParamType() == ValueType.MAP) {
                if (parts != null && parts.size() % 2 != 0) {
                    return data.getCodec().getMapValueDecoder();
                } else {
                    return data.getCodec().getMapKeyDecoder();
                }
            } else if (data.getCommand().getOutParamType() == ValueType.MAP_KEY) {
                return data.getCodec().getMapKeyDecoder();
            } else if (data.getCommand().getOutParamType() == ValueType.MAP_VALUE) {
                return data.getCodec().getMapValueDecoder();
            } else {
                return data.getCodec().getValueDecoder();
            }
        }
        return decoder;
    }

    public ByteBuf readBytes(ByteBuf is) throws IOException {
        long l = readLong(is);
        if (l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Java only supports arrays up to " + Integer.MAX_VALUE + " in size");
        }
        int size = (int) l;
        if (size == -1) {
            return null;
        }
        ByteBuf buffer = is.readSlice(size);
        int cr = is.readByte();
        int lf = is.readByte();
        if (cr != CR || lf != LF) {
            throw new IOException("Improper line ending: " + cr + ", " + lf);
        }
        return buffer;
    }

    public static long readLong(ByteBuf is) throws IOException {
        long size = 0;
        int sign = 1;
        int read = is.readByte();
        if (read == '-') {
            read = is.readByte();
            sign = -1;
        }
        do {
            if (read == CR) {
                if (is.readByte() == LF) {
                    break;
                }
            }
            int value = read - ZERO;
            if (value >= 0 && value < 10) {
                size *= 10;
                size += value;
            } else {
                throw new IOException("Invalid character in integer");
            }
            read = is.readByte();
        } while (true);
        return size * sign;
    }

}
