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
public class RedisPubSubAdapter<K, V> implements RedisPubSubListener<K, V> {
    @Override
    public void message(K channel, V message) {
    }

    @Override
    public void message(K pattern, K channel, V message) {
    }

    @Override
    public void subscribed(K channel, long count) {
    }

    @Override
    public void psubscribed(K pattern, long count) {
    }

    @Override
    public void unsubscribed(K channel, long count) {
    }

    @Override
    public void punsubscribed(K pattern, long count) {
    }
}
