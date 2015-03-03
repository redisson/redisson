package org.redisson.core;

import java.util.concurrent.*;

/**
 * {@link BlockingQueue} backed by Redis
 * 
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingQueue<V> extends BlockingQueue<V>, RExpirable {

}
