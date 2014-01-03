package org.redisson.core;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

public class RedisPubSubTopicListener<K, V> extends RedisPubSubAdapter<K, V> {

    private final MessageListener<V> listener;

    public RedisPubSubTopicListener(MessageListener<V> listener) {
        super();
        this.listener = listener;
    }

    @Override
    public void message(K channel, V message) {
        listener.onMessage(message);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((listener == null) ? 0 : listener.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RedisPubSubTopicListener other = (RedisPubSubTopicListener) obj;
        if (listener == null) {
            if (other.listener != null)
                return false;
        } else if (!listener.equals(other.listener))
            return false;
        return true;
    }



}
