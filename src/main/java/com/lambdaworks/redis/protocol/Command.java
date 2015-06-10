// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.protocol;

import com.lambdaworks.redis.RedisException;
import com.lambdaworks.redis.RedisMovedException;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Promise;

/**
 * A redis command and its result. All successfully executed commands will
 * eventually return a {@link CommandOutput} object.
 *
 * @param <T> Command output type.
 *
 * @author Will Glozer
 */
public class Command<K, V, T> {
    private static final byte[] CRLF = "\r\n".getBytes(Charsets.ASCII);

    private final Promise<T> promise;
    public final CommandType type;
    protected CommandArgs<K, V> args;
    protected final CommandOutput<K, V, T> output;
    protected int completeAmount;

    /**
     * Create a new command with the supplied type and args.
     *
     * @param type      Command type.
     * @param output    Command output.
     * @param args      Command args, if any.
     * @param multi     Flag indicating if MULTI active.
     */
    public Command(CommandType type, CommandOutput<K, V, T> output, CommandArgs<K, V> args, boolean multi, Promise<T> proimse) {
        this.type   = type;
        this.output = output;
        this.args   = args;
        this.completeAmount = multi ? 2 : 1;
        this.promise = proimse;
    }

    public Promise<T> getPromise() {
        return promise;
    }

    /**
     * Get the object that holds this command's output.
     *
     * @return  The command output object.
     */
    public CommandOutput<K, V, T> getOutput() {
        return output;
    }

    public void cancel() {
        promise.cancel(true);
    }

    public void complete() {
        completeAmount--;
        if (completeAmount == 0) {
            Object res = output.get();
            if (promise.isCancelled()) {
                return;
            }
            if (res instanceof RedisException) {
                promise.setFailure((Exception)res);
            } else if (output.hasError()) {
                if (output.getError().startsWith("MOVED")) {
                    String[] parts = output.getError().split(" ");
                    int slot = Integer.valueOf(parts[1]);
                    promise.setFailure(new RedisMovedException(slot));
                } else if (output.getError().startsWith("(error) ASK")) {
                    String[] parts = output.getError().split(" ");
                    int slot = Integer.valueOf(parts[2]);
                    promise.setFailure(new RedisMovedException(slot));
                } else {
                    promise.setFailure(new RedisException(output.getError()));
                }
            } else if (output.hasException()) {
                promise.setFailure(output.getException());
            } else {
                promise.setSuccess((T)res);
            }
        }
    }

    /**
     * Encode and write this command to the supplied buffer using the new
     * <a href="http://redis.io/topics/protocol">Unified Request Protocol</a>.
     *
     * @param buf Buffer to write to.
     */
    void encode(ByteBuf buf) {
        buf.writeByte('*');
        writeInt(buf, 1 + (args != null ? args.count() : 0));
        buf.writeBytes(CRLF);
        buf.writeByte('$');
        writeInt(buf, type.bytes.length);
        buf.writeBytes(CRLF);
        buf.writeBytes(type.bytes);
        buf.writeBytes(CRLF);
        if (args != null) {
            buf.writeBytes(args.buffer());
        }
    }

    /**
     * Write the textual value of a positive integer to the supplied buffer.
     *
     * @param buf   Buffer to write to.
     * @param value Value to write.
     */
    protected static void writeInt(ByteBuf buf, int value) {
        if (value < 10) {
            buf.writeByte('0' + value);
            return;
        }

        StringBuilder sb = new StringBuilder(8);
        while (value > 0) {
            int digit = value % 10;
            sb.append((char) ('0' + digit));
            value /= 10;
        }

        for (int i = sb.length() - 1; i >= 0; i--) {
            buf.writeByte(sb.charAt(i));
        }
    }
}
