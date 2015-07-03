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
package org.redisson;

import org.redisson.core.MessageListener;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K>
 * @param <V>
 */
public class RedisPubSubTopicListenerWrapper<V> extends RedisPubSubAdapter<V> {

    private final MessageListener<V> listener;
    private final String name;

    public String getName() {
        return name;
    }

    public RedisPubSubTopicListenerWrapper(MessageListener<V> listener, String name) {
        super();
        this.listener = listener;
        this.name = name;
    }

    @Override
    public void message(String channel, V message) {
        // could be subscribed to multiple channels
        if (name.equals(channel)) {
            listener.onMessage(message);
        }
    }

  	@Override
  	public void message(String pattern, String channel, V message) {
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
        RedisPubSubTopicListenerWrapper other = (RedisPubSubTopicListenerWrapper) obj;
        if (listener == null) {
            if (other.listener != null)
                return false;
        } else if (!listener.equals(other.listener))
            return false;
        return true;
    }



}
