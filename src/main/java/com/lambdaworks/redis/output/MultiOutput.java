// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.output;

import com.lambdaworks.redis.RedisException;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandOutput;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Output of all commands within a MULTI block.
 *
 * @author Will Glozer
 */
public class MultiOutput<K, V> extends CommandOutput<K, V, List<Object>> {
    private Queue<Command<K, V, ?>> queue;

    public MultiOutput(RedisCodec<K, V> codec) {
        super(codec, new ArrayList<Object>());
        queue = new LinkedList<Command<K, V, ?>>();
    }

    public void add(Command<K, V, ?> cmd) {
        queue.add(cmd);
    }

    public void cancel() {
        for (Command<K, V, ?> c : queue) {
            c.complete();
        }
    }

    @Override
    public void set(long integer) {
        queue.peek().getOutput().set(integer);
    }

    @Override
    public void set(ByteBuffer bytes) {
        queue.peek().getOutput().set(bytes);
    }

    @Override
    public void setError(ByteBuffer error) {
        CommandOutput<K, V, ?> output = queue.isEmpty() ? this : queue.peek().getOutput();
        output.setError(decodeAscii(error));
    }

    @Override
    public void complete(int depth) {
        if (depth == 1) {
            Command<K, V, ?> cmd = queue.remove();
            CommandOutput<K, V, ?> o = cmd.getOutput();
            output.add(!o.hasError() ? o.get() : new RedisException(o.getError()));
            cmd.complete();
        } else if (depth == 0 && !queue.isEmpty()) {
            for (Command<K, V, ?> cmd : queue) {
                cmd.complete();
            }
        }
    }
}
