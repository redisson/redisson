// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.pubsub;

/**
 * Interface for redis pub/sub listeners.
 *
 * @param <V> Value type.
 *
 * @author Will Glozer
 */
public interface RedisPubSubListener<V> {
    /**
     * Message received from a channel subscription.
     *
     * @param channel   Channel.
     * @param message   Message.
     */
    void message(String channel, V message);

    /**
     * Message received from a pattern subscription.
     *
     * @param pattern   Pattern.
     * @param channel   Channel.
     * @param message   Message.
     */
    void message(String pattern, String channel, V message);

    /**
     * Subscribed to a channel.
     *
     * @param channel   Channel
     * @param count     Subscription count.
     */
    void subscribed(String channel, long count);

    /**
     * Subscribed to a pattern.
     *
     * @param pattern   Pattern.
     * @param count     Subscription count.
     */
    void psubscribed(String pattern, long count);

    /**
     * Unsubscribed from a channel.
     *
     * @param channel   Channel
     * @param count     Subscription count.
     */
    void unsubscribed(String channel, long count);

    /**
     * Unsubscribed from a pattern.
     *
     * @param pattern   Channel
     * @param count     Subscription count.
     */
    void punsubscribed(String pattern, long count);
}
