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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RBlockingQueue;

import com.lambdaworks.redis.RedisConnection;

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
        return connectionManager.write(getName(), new SyncOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) {
                return conn.blpop(0, getName()).value;
            }
        });
    }

    @Override
    public V poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return connectionManager.read(getName(), new SyncOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) {
                return conn.blpop(unit.toSeconds(timeout), getName()).value;
            }
        });
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        List<V> list = connectionManager.write(getName(), new SyncOperation<V, List<V>>() {
            @Override
            public List<V> execute(RedisConnection<Object, V> conn) {
                while (true) {
                    conn.watch(getName());
                    conn.multi();
                    conn.lrange(getName(), 0, -1);
                    conn.ltrim(getName(), 0, -1);
                    List<Object> res = conn.exec();
                    if (res.size() == 2) {
                        List<V> items = (List<V>) res.get(0);
                        return items;
                    }
                }
            }
        });
        c.addAll(list);
        return list.size();
    }

    @Override
    public int drainTo(Collection<? super V> c, final int maxElements) {
        List<V> list = connectionManager.write(getName(), new SyncOperation<V, List<V>>() {
            @Override
            public List<V> execute(RedisConnection<Object, V> conn) {
                while (true) {
                    conn.watch(getName());
                    Long len = Math.min(conn.llen(getName()), maxElements);
                    if (len == 0) {
                        conn.unwatch();
                        return Collections.emptyList();
                    }
                    conn.multi();
                    conn.lrange(getName(), 0, len);
                    conn.ltrim(getName(), 0, len);
                    List<Object> res = conn.exec();
                    if (res.size() == 2) {
                        List<V> items = (List<V>) res.get(0);
                        return items;
                    }
                }
            }
        });
        c.addAll(list);
        return list.size();
    }
}