// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.pubsub;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

import java.nio.ByteBuffer;

/**
 * One element of the redis pub/sub stream. May be a message or notification
 * of subscription details.
 *
 * @param <V> Value type.
 *
 * @author Will Glozer
 */
public class PubSubOutput<K, V> extends CommandOutput<K, V, V> {
    enum Type { message, pmessage, psubscribe, punsubscribe, subscribe, unsubscribe }

    private Type type;
    private K channel;
    private K pattern;
    private long count;

    public PubSubOutput(RedisCodec<K, V> codec) {
        super(codec, null);
    }

    public Type type() {
        return type;
    }

    public K channel() {
        return channel;
    }

    public K pattern() {
        return pattern;
    }

    public long count() {
        return count;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void set(ByteBuffer bytes) {
        if (type == null) {
            type = Type.valueOf(decodeAscii(bytes));
            return;
        }

        switch (type) {
            case pmessage:
                if (pattern == null) {
                    pattern = codec.decodeKey(bytes);
                    break;
                }
            case message:
                if (channel == null) {
                    channel = codec.decodeKey(bytes);
                    break;
                }
                output = codec.decodeValue(bytes);
                break;
            case psubscribe:
            case punsubscribe:
                pattern = codec.decodeKey(bytes);
                break;
            case subscribe:
            case unsubscribe:
                channel = codec.decodeKey(bytes);
                break;
        }
    }

    @Override
    public void set(long integer) {
        count = integer;
    }
}
