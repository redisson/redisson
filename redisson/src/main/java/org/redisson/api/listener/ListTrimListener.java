package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Redisson Object Event listener for <b>ltrim</b> event published by Redis.
 * <p>
 * Redis notify-keyspace-events setting should contain El letters
 *
 * @author Nikita Koksharov
 *
 */
public interface ListTrimListener extends ObjectListener {

    /**
     * Invoked on list trimming event
     *
     * @param name - name of object
     */
    void onListTrim(String name);

}
