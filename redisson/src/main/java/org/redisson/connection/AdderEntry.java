package org.redisson.connection;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class AdderEntry {

    private final Set<String> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AtomicInteger usage = new AtomicInteger();

    public Set<String> getIds() {
        return ids;
    }

    public AtomicInteger getUsage() {
        return usage;
    }

}
