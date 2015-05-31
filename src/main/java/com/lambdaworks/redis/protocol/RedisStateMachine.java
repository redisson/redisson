// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.protocol;

import com.lambdaworks.redis.RedisException;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import static com.lambdaworks.redis.protocol.Charsets.buffer;
import static com.lambdaworks.redis.protocol.RedisStateMachine.State.Type.*;

/**
 * State machine that decodes redis server responses encoded according to the
 * <a href="http://redis.io/topics/protocol">Unified Request Protocol</a>.
 *
 * @author Will Glozer
 */
public class RedisStateMachine<K, V> {
    private static final ByteBuffer QUEUED = buffer("QUEUED");

    static class State {
        enum Type { SINGLE, ERROR, INTEGER, BULK, MULTI, BYTES }
        Type type  = null;
        int  count = -1;
    }

    private LinkedList<State> stack;

    /**
     * Initialize a new instance.
     */
    public RedisStateMachine() {
        stack = new LinkedList<State>();
    }

    /**
     * Attempt to decode a redis response and return a flag indicating whether a complete
     * response was read.
     *
     * @param buffer    Buffer containing data from the server.
     * @param output    Current command output.
     *
     * @return true if a complete response was read.
     */
    public boolean decode(ByteBuf buffer, CommandOutput<K, V, ?> output) {
        int length, end;
        ByteBuffer bytes;

        if (stack.isEmpty()) {
            stack.add(new State());
        }

        if (output == null) {
            return stack.isEmpty();
        }

        loop:

        while (!stack.isEmpty()) {
            State state = stack.peek();

            if (state.type == null) {
                if (!buffer.isReadable()) break;
                state.type = readReplyType(buffer);
                buffer.markReaderIndex();
            }

            switch (state.type) {
                case SINGLE:
                    if ((bytes = readLine(buffer)) == null) break loop;
                    if (!QUEUED.equals(bytes)) {
                        setCommandOutputSafely(output, bytes);
                    }
                    break;
                case ERROR:
                    if ((bytes = readLine(buffer)) == null) break loop;
                    output.setError(bytes);
                    break;
                case INTEGER:
                    if ((end = findLineEnd(buffer)) == -1) break loop;
                    setCommandOutputSafely(output, readLong(buffer, buffer.readerIndex(), end));
                    break;
                case BULK:
                    if ((end = findLineEnd(buffer)) == -1) break loop;
                    length = (int) readLong(buffer, buffer.readerIndex(), end);
                    if (length == -1) {
                        setCommandOutputSafely(output, null);
                    } else {
                        state.type = BYTES;
                        state.count = length + 2;
                        buffer.markReaderIndex();
                        continue loop;
                    }
                    break;
                case MULTI:
                    if (state.count == -1) {
                        if ((end = findLineEnd(buffer)) == -1) break loop;
                        length = (int) readLong(buffer, buffer.readerIndex(), end);
                        state.count = length;
                        buffer.markReaderIndex();
                    }

                    if (state.count <= 0) break;

                    state.count--;
                    stack.addFirst(new State());
                    continue loop;
                case BYTES:
                    if ((bytes = readBytes(buffer, state.count)) == null) break loop;
                    setCommandOutputSafely(output, bytes);
            }

            buffer.markReaderIndex();
            stack.remove();
            output.complete(stack.size());
        }

        return stack.isEmpty();
    }

    private int findLineEnd(ByteBuf buffer) {
        int start = buffer.readerIndex();
        int index = buffer.indexOf(start, buffer.writerIndex(), (byte) '\n');
        return (index > 0 && buffer.getByte(index - 1) == '\r') ? index : -1;
    }

    private State.Type readReplyType(ByteBuf buffer) {
        switch (buffer.readByte()) {
            case '+': return SINGLE;
            case '-': return ERROR;
            case ':': return INTEGER;
            case '$': return BULK;
            case '*': return MULTI;
            default:  throw new RedisException("Invalid first byte");
        }
    }

    private long readLong(ByteBuf buffer, int start, int end) {
        long value = 0;

        boolean negative = buffer.getByte(start) == '-';
        int offset = negative ? start + 1 : start;
        while (offset < end - 1) {
            int digit = buffer.getByte(offset++) - '0';
            value = value * 10 - digit;
        }
        if (!negative) value = -value;
        buffer.readerIndex(end + 1);

        return value;
    }

    private ByteBuffer readLine(ByteBuf buffer) {
        ByteBuffer bytes = null;
        int end = findLineEnd(buffer);
        if (end > -1) {
            int start = buffer.readerIndex();
            bytes = buffer.nioBuffer(start, end - start - 1);
            buffer.readerIndex(end + 1);
        }
        return bytes;
    }

    private ByteBuffer readBytes(ByteBuf buffer, int count) {
        ByteBuffer bytes = null;
        if (buffer.readableBytes() >= count) {
            bytes = buffer.nioBuffer(buffer.readerIndex(), count - 2);
            buffer.readerIndex(buffer.readerIndex() + count);
        }
        return bytes;
    }


    private boolean setCommandOutputSafely(CommandOutput output, ByteBuffer bytes) {
        boolean success = false;
        try {
            output.set(bytes);
            success = true;
        } catch (Throwable t) {
            output.setException(t);
        }
        return success;
    }

    private boolean setCommandOutputSafely(CommandOutput output, long value) {
        boolean success = false;
        try {
            output.set(value);
            success = true;
        } catch (Throwable t) {
            output.setException(t);
        }
        return success;
    }

}
