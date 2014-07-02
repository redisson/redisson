// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.pubsub;

/**
 * Convenience adapter with an empty implementation of all
 * {@link RedisPubSubListener} callback methods.
 *
 * @param <V> Value type.
 *
 * @author Will Glozer
 */
public class RedisPubSubAdapter<V> implements RedisPubSubListener<V> {
    @Override
    public void message(String channel, V message) {
    }

    @Override
    public void message(String pattern, String channel, V message) {
    }

    @Override
    public void subscribed(String channel, long count) {
    }

    @Override
    public void psubscribed(String pattern, long count) {
    }

    @Override
    public void unsubscribed(String channel, long count) {
    }

    @Override
    public void punsubscribed(String pattern, long count) {
    }
}
