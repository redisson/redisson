package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Redisson Object Event listener for <b>rpush</b> event published by Redis.
 * <p>
 * Redis notify-keyspace-events setting should contain El letters
 *
 * @author Nikita Koksharov
 *
 */
public interface ListAddListener extends ObjectListener {

    /**
     * Invoked on event of adding element to list
     *
     * @param name - name of object
     */
    void onListAdd(String name);

}
