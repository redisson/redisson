package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Redisson Object Event listener for <b>lrem</b> event published by Redis.
 * <p>
 * Redis notify-keyspace-events setting should contain El letters
 *
 * @author Nikita Koksharov
 *
 */
public interface ListRemoveListener extends ObjectListener {

    /**
     * Invoked on event of removing element from list
     *
     * @param name - name of object
     */
    void onListRemove(String name);

}
