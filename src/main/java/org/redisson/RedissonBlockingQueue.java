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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.async.SyncInterruptedOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RBlockingQueue;

import com.lambdaworks.redis.RedisConnection;
import org.redisson.core.RScript;

/**
 * Offers blocking queue facilities through an intermediary
 * {@link LinkedBlockingQueue} where items are added as soon as
 * <code>blpop</code> returns. All {@link BlockingQueue} methods are actually
 * delegated to this intermediary queue.
 *
 * @author pdeschen@gmail.com
 * @author Nikita Koksharov
 */
public class RedissonBlockingQueue<V> extends RedissonQueue<V> implements RBlockingQueue<V> {

    protected RedissonBlockingQueue(ConnectionManager connection, String name) {
        super(connection, name);
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
        return connectionManager.write(getName(), new SyncInterruptedOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) throws InterruptedException {
                return conn.blpop(0, getName()).value;
            }
        });
    }

    @Override
    public V poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return connectionManager.write(getName(), new SyncInterruptedOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) throws InterruptedException {
                return conn.blpop(unit.toSeconds(timeout), getName()).value;
            }
        });
    }

    @Override
    public V pollLastAndOfferFirstTo(RBlockingQueue<V> queue, long timeout, TimeUnit unit)
            throws InterruptedException {
        return pollLastAndOfferFirstTo(queue.getName(), timeout, unit);
    }

    @Override
    public V pollLastAndOfferFirstTo(final String queueName, final long timeout, final TimeUnit unit) throws InterruptedException {
        return connectionManager.write(getName(), new SyncInterruptedOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) throws InterruptedException {
                return conn.brpoplpush(unit.toSeconds(timeout), getName(), queueName);
            }
        });
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        List<V> list = new RedissonScript(connectionManager).eval(
                "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
                        "redis.call('ltrim', KEYS[1], -1, 0); " +
                        "return vals",
                RScript.ReturnType.MAPVALUELIST,
                Collections.<Object>singletonList(getName()));
        c.addAll(list);
        return list.size();
    }

    @Override
    public int drainTo(Collection<? super V> c, final int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }
        if (c == null) {
            throw new NullPointerException();
        }

        List<V> list = new RedissonScript(connectionManager).evalR(
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                RScript.ReturnType.MAPVALUELIST,
                Collections.<Object>singletonList(getName()), Collections.emptyList(), Collections.singletonList(maxElements));
        c.addAll(list);
        return list.size();
    }
}