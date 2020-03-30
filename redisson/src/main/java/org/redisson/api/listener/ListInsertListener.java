package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Redisson Object Event listener for <b>lset</b> event published by Redis.
 * <p>
 * Redis notify-keyspace-events setting should contain El letters
 *
 * @author Nikita Koksharov
 *
 */
public interface ListInsertListener extends ObjectListener {

    /**
     * Invoked on event of setting element to list
     *
     * @param name - name of object
     */
    void onListInsert(String name);

}
