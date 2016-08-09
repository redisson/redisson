/**
 * Copyright 2016 Nikita Koksharov
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

import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;

import io.netty.channel.ChannelFuture;
import io.netty.util.Timeout;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class AsyncDetails<V, R> {

    static final ConcurrentLinkedQueue<AsyncDetails> queue = new ConcurrentLinkedQueue<AsyncDetails>();

    Future<RedisConnection> connectionFuture;
    ConnectionManager connectionManager;
    Promise<R> attemptPromise;
    boolean readOnlyMode;
    NodeSource source;
    Codec codec;
    RedisCommand<V> command;
    Object[] params;
    Promise<R> mainPromise;
    int attempt;


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

    public void init(Future<RedisConnection> connectionFuture,
            Promise<R> attemptPromise, boolean readOnlyMode, NodeSource source,
            Codec codec, RedisCommand<V> command, Object[] params,
            Promise<R> mainPromise, int attempt) {
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

    public Future<RedisConnection> getConnectionFuture() {
        return connectionFuture;
    }

    public Promise<R> getAttemptPromise() {
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

    public Promise<R> getMainPromise() {
        return mainPromise;
    }

    public int getAttempt() {
        return attempt;
    }



}
