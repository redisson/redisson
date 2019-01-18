/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.connection;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.client.RedisConnection;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.pubsub.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;

public class IdleConnectionWatcher {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static class Entry {

        private final int minimumAmount;
        private final int maximumAmount;
        private final AsyncSemaphore freeConnectionsCounter;
        private final Collection<? extends RedisConnection> connections;

        public Entry(int minimumAmount, int maximumAmount, Collection<? extends RedisConnection> connections, AsyncSemaphore freeConnectionsCounter) {
            super();
            this.minimumAmount = minimumAmount;
            this.maximumAmount = maximumAmount;
            this.connections = connections;
            this.freeConnectionsCounter = freeConnectionsCounter;
        }

    };

    private final Queue<Entry> entries = new ConcurrentLinkedQueue<Entry>();
    private final ScheduledFuture<?> monitorFuture;

    public IdleConnectionWatcher(final ConnectionManager manager, final MasterSlaveServersConfig config) {
        monitorFuture = manager.getGroup().scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                long currTime = System.currentTimeMillis();
                for (Entry entry : entries) {
                    if (!validateAmount(entry)) {
                        continue;
                    }

                    for (final RedisConnection c : entry.connections) {
                        final long timeInPool = currTime - c.getLastUsageTime();
                        if (timeInPool > config.getIdleConnectionTimeout()
                                && validateAmount(entry) && entry.connections.remove(c)) {
                            ChannelFuture future = c.closeAsync();
                            future.addListener(new FutureListener<Void>() {
                                @Override
                                public void operationComplete(Future<Void> future) throws Exception {
                                    log.debug("Connection {} has been closed due to idle timeout. Not used for {} ms", c.getChannel(), timeInPool);
                                }
                            });
                        }
                    }
                }
            }

        }, config.getIdleConnectionTimeout(), config.getIdleConnectionTimeout(), TimeUnit.MILLISECONDS);
    }

    private boolean validateAmount(Entry entry) {
        return entry.maximumAmount - entry.freeConnectionsCounter.getCounter() + entry.connections.size() > entry.minimumAmount;
    }

    public void add(int minimumAmount, int maximumAmount, Collection<? extends RedisConnection> connections, AsyncSemaphore freeConnectionsCounter) {
        entries.add(new Entry(minimumAmount, maximumAmount, connections, freeConnectionsCounter));
    }
    
    public void stop() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
    }

}
