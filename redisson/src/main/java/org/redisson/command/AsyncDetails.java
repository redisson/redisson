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
package org.redisson.command;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.misc.RPromise;

import io.netty.channel.ChannelFuture;
import io.netty.util.Timeout;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

public class AsyncDetails<V, R> {

    static final ConcurrentLinkedQueue<AsyncDetails> queue = new ConcurrentLinkedQueue<AsyncDetails>();

    RFuture<RedisConnection> connectionFuture;
    ConnectionManager connectionManager;
    RPromise<R> attemptPromise;
    boolean readOnlyMode;
    NodeSource source;
    Codec codec;
    RedisCommand<V> command;
    Object[] params;
    RPromise<R> mainPromise;
    int attempt;
    FutureListener<R> mainPromiseListener;

    private volatile ChannelFuture writeFuture;

    private volatile RedisException exception;

    private volatile Timeout timeout;

    public AsyncDetails() {
    }

    public static AsyncDetails acquire() {
//        AsyncDetails result = queue.poll();
//        if (result != null) {
//            return result;
//        }

        AsyncDetails details = new AsyncDetails();
        return details;
    }

    public static void release(AsyncDetails details) {
//        queue.add(details);
    }

    public void init(RFuture<RedisConnection> connectionFuture,
            RPromise<R> attemptPromise, boolean readOnlyMode, NodeSource source,
            Codec codec, RedisCommand<V> command, Object[] params,
            RPromise<R> mainPromise, int attempt) {
        this.connectionFuture = connectionFuture;
        this.attemptPromise = attemptPromise;
        this.readOnlyMode = readOnlyMode;
        this.source = source;
        this.codec = codec;
        this.command = command;
        this.params = params;
        this.mainPromise = mainPromise;
        this.attempt = attempt;
        this.writeFuture = writeFuture;
        this.exception = exception;
        this.timeout = timeout;
    }
    
    public ChannelFuture getWriteFuture() {
        return writeFuture;
    }
    public void setWriteFuture(ChannelFuture writeFuture) {
        this.writeFuture = writeFuture;
    }

    public RedisException getException() {
        return exception;
    }
    public void setException(RedisException exception) {
        this.exception = exception;
    }

    public Timeout getTimeout() {
        return timeout;
    }
    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public RFuture<RedisConnection> getConnectionFuture() {
        return connectionFuture;
    }

    public RPromise<R> getAttemptPromise() {
        return attemptPromise;
    }

    public boolean isReadOnlyMode() {
        return readOnlyMode;
    }

    public NodeSource getSource() {
        return source;
    }

    public Codec getCodec() {
        return codec;
    }

    public RedisCommand<V> getCommand() {
        return command;
    }

    public Object[] getParams() {
        return params;
    }

    public RPromise<R> getMainPromise() {
        return mainPromise;
    }

    public int getAttempt() {
        return attempt;
    }
    public void incAttempt() {
        attempt++;
    }

    public void setupMainPromiseListener(FutureListener<R> mainPromiseListener) {
        this.mainPromiseListener = mainPromiseListener;
        mainPromise.addListener(mainPromiseListener);
    }
    
    public void removeMainPromiseListener() {
        if (mainPromiseListener != null) {
            mainPromise.removeListener(mainPromiseListener);
            mainPromiseListener = null;
        }
    }

}
