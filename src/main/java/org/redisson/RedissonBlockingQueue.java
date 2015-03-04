package org.redisson;

import java.util.*;
import java.util.concurrent.*;

import org.redisson.async.*;
import org.redisson.connection.*;
import org.redisson.core.*;

import com.lambdaworks.redis.*;

/**
 * Offers blocking queue facilities through an intermediary
 * {@link LinkedBlockingQueue} where items are added as soon as
 * <code>blpop</code> returns. All {@link BlockingQueue} methods are actually
 * delegated to this intermediary queue.
 * 
 * @author pdeschen@gmail.com
 */
public class RedissonBlockingQueue<V> extends RedissonQueue<V> implements RBlockingQueue<V> {

    private final static int BLPOP_TIMEOUT_IN_MS = 1000;

    private final LinkedBlockingQueue<V> blockingQueue = new LinkedBlockingQueue();

    public RedissonBlockingQueue(final ConnectionManager connection, final String name) {
        super(connection, name);
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final java.util.concurrent.Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    V item = connection.write(name, new SyncOperation<V, V>() {
                        @Override
                        public V execute(RedisConnection<Object, V> conn) {
                            // Get this timeout from config?
                            return conn.blpop(BLPOP_TIMEOUT_IN_MS, name).value;
                        }
                    });
                    blockingQueue.add(item);
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread("redis-blpop-shutdown-hook-thread") {
            @Override
            public void run() {
                future.cancel(true);
                executor.shutdown();
            }
        });
    }

    @Override
    public void put(V e) throws InterruptedException {
        offer(e);
    }

    @Override
    public boolean offer(V e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    @Override
    public V take() throws InterruptedException {
        return blockingQueue.take();
    }

    @Override
    public V poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return blockingQueue.poll(timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return blockingQueue.remainingCapacity();
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        return blockingQueue.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainTo(c, maxElements);
    }
}