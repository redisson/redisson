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
package org.redisson.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.redisson.client.*;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Protocol;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseConnectionHandler<C extends RedisConnection> extends ChannelInboundHandlerAdapter {

    final RedisClient redisClient;
    final CompletableFuture<C> connectionPromise = new CompletableFuture<>();
    C connection;
    
    public BaseConnectionHandler(RedisClient redisClient) {
        super();
        this.redisClient = redisClient;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (connection == null) {
            connection = createConnection(ctx);
        }
        super.channelRegistered(ctx);
    }

    abstract C createConnection(ChannelHandlerContext ctx);
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        List<CompletableFuture<Object>> futures = new ArrayList<>(5);

        InetSocketAddress addr = redisClient.resolveAddr().getNow(null);
        RedisClientConfig config = redisClient.getConfig();
        CompletionStage<Object> f = config.getCredentialsResolver().resolve(addr)
                .thenCompose(credentials -> {
                    String password = Objects.toString(config.getAddress().getPassword(),
                            Objects.toString(credentials.getPassword(), config.getPassword()));
                    if (password != null) {
                        CompletionStage<Object> future;
                        String username = Objects.toString(config.getAddress().getUsername(),
                                Objects.toString(credentials.getUsername(), config.getUsername()));
                        if (username != null) {
                            future = connection.async(RedisCommands.AUTH, username, password);
                        } else {
                            future = connection.async(RedisCommands.AUTH, password);
                        }
                        return future;
                    }
                    return CompletableFuture.completedFuture(null);
                });
        futures.add(f.toCompletableFuture());

        if (redisClient.getConfig().getProtocol() == Protocol.RESP3) {
            CompletionStage<Object> f1 = connection.async(RedisCommands.HELLO, "3");
            futures.add(f1.toCompletableFuture());
        }

        if (config.getDatabase() != 0) {
            CompletionStage<Object> future = connection.async(RedisCommands.SELECT, config.getDatabase());
            futures.add(future.toCompletableFuture());
        }
        if (config.getClientName() != null) {
            CompletionStage<Object> future = connection.async(RedisCommands.CLIENT_SETNAME, config.getClientName());
            futures.add(future.toCompletableFuture());
        }
        if (config.isReadOnly()) {
            CompletionStage<Object> future = connection.async(RedisCommands.READONLY);
            futures.add(future.toCompletableFuture());
        }
        if (config.getPingConnectionInterval() > 0) {
            CompletionStage<Object> future = connection.async(RedisCommands.PING);
            futures.add(future.toCompletableFuture());
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        future.whenComplete((res, e) -> {
            if (e != null) {
                if (e instanceof RedisRetryException) {
                    ctx.executor().schedule(() -> {
                        channelActive(ctx);
                    }, 1, TimeUnit.SECONDS);
                    return;
                }
                connection.closeAsync();
                connectionPromise.completeExceptionally(e);
                return;
            }

            ctx.fireChannelActive();
            connectionPromise.complete(connection);
        });
    }
    
}
