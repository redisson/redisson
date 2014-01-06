package org.redisson.misc.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class ThreadLocalSemaphore {

    private final ThreadLocal<Semaphore> semaphore;
    private final Set<Semaphore> allValues = Collections.newSetFromMap(new ConcurrentHashMap<Semaphore, Boolean>());

    public ThreadLocalSemaphore() {
        semaphore = new ThreadLocal<Semaphore>() {
            @Override protected Semaphore initialValue() {
                Semaphore value = new Semaphore(1);
                value.acquireUninterruptibly();
                allValues.add(value);
                return value;
            }
        };
    }

    public Semaphore get() {
        return semaphore.get();
    }

    public void remove() {
        allValues.remove(get());
        semaphore.remove();
    }

    public Collection<Semaphore> getAll() {
        return allValues;
    }

}
