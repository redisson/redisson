/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.AsyncSemaphore;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TrackedConnectionsHolder extends ConnectionsHolder<RedisConnection> {

    private final ConnectionsHolder<RedisConnection> holder;

    private final AtomicReference<CompletableFuture<RedisConnection>> connectionFuture = new AtomicReference<>();

    private volatile ClientConnectionsEntry entry;

    private final AtomicInteger usage = new AtomicInteger();

    public TrackedConnectionsHolder(ConnectionsHolder<RedisConnection> holder) {
        super(null, 0, null, null, false);
        this.holder = holder;
    }

    @Override
    public <R extends RedisConnection> boolean remove(R connection) {
        return holder.remove(connection);
    }

    @Override
    public Queue<RedisConnection> getFreeConnections() {
        return holder.getFreeConnections();
    }

    @Override
    public AsyncSemaphore getFreeConnectionsCounter() {
        return holder.getFreeConnectionsCounter();
    }

    @Override
    public Queue<RedisConnection> getAllConnections() {
        return holder.getAllConnections();
    }

    @Override
    public CompletableFuture<Void> initConnections(int minimumIdleSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<RedisConnection> acquireConnection(RedisCommand<?> command) {
        CompletableFuture<RedisConnection> newFuture = new CompletableFuture<>();
        if (!connectionFuture.compareAndSet(null, newFuture)) {
            return connectionFuture.get();
        }

        CompletableFuture<RedisConnection> f = holder.acquireConnection(command);
        newFuture.whenComplete((r, e) -> {
            if (e != null) {
                f.completeExceptionally(e);
                connectionFuture.set(null);
            }
        });
        f.whenComplete((r, e) -> {
            if (e != null) {
                newFuture.completeExceptionally(e);
                connectionFuture.set(null);
                return;
            }

            newFuture.complete(r);
        });
        return newFuture;
    }

    @Override
    public void releaseConnection(ClientConnectionsEntry entry, RedisConnection connection) {
        this.entry = entry;
        //holder.releaseConnection(entry, connection);
    }

    public void reset() {
        if (connectionFuture.get() != null
                && connectionFuture.get().getNow(null) != null) {
            RedisConnection c = connectionFuture.get().getNow(null);
            RFuture<Void> f = c.async(RedisCommands.CLIENT_TRACKING, "OFF");
            f.whenComplete((res, ex) -> {
                holder.releaseConnection(entry, connectionFuture.get().getNow(null));
            });
        }
    }

    public void incUsage() {
        usage.incrementAndGet();
    }

    public int decUsage() {
        return usage.decrementAndGet();
    }

}
