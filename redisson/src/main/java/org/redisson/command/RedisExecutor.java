/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.FutureListener;
import org.redisson.RedissonShutdownException;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 * @param <R> type of returned value
 */
@SuppressWarnings({"NestedIfDepth"})
public class RedisExecutor<V, R> {
    
    static final Logger log = LoggerFactory.getLogger(RedisExecutor.class);
    
    final boolean readOnlyMode; 
    final RedisCommand<V> command; 
    final Object[] params; 
    final RPromise<R> mainPromise; 
    final boolean ignoreRedirect;
    final RedissonObjectBuilder objectBuilder;
    final ConnectionManager connectionManager;
    final RedissonObjectBuilder.ReferenceType referenceType;

    RFuture<RedisConnection> connectionFuture;
    NodeSource source;
    Codec codec;
    volatile int attempt;
    volatile Timeout timeout;
    volatile BiConsumer<R, Throwable> mainPromiseListener;
    volatile ChannelFuture writeFuture;
    volatile RedisException exception;
    
    int attempts;
    long retryInterval;
    long responseTimeout;
    
    public RedisExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
                         Object[] params, RPromise<R> mainPromise, boolean ignoreRedirect,
                         ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder,
                         RedissonObjectBuilder.ReferenceType referenceType) {
        super();
        this.readOnlyMode = readOnlyMode;
        this.source = source;
        this.codec = codec;
        this.command = command;
        this.params = params;
        this.mainPromise = mainPromise;
        this.ignoreRedirect = ignoreRedirect;
        this.connectionManager = connectionManager;
        this.objectBuilder = objectBuilder;
        
        this.attempts = connectionManager.getConfig().getRetryAttempts();
        this.retryInterval = connectionManager.getConfig().getRetryInterval();
        this.responseTimeout = connectionManager.getConfig().getTimeout();
        this.referenceType = referenceType;
    }

    public void execute() {
        if (mainPromise.isCancelled()) {
            free();
            return;
        }

        if (!connectionManager.getShutdownLatch().acquire()) {
            free();
            mainPromise.tryFailure(new RedissonShutdownException("Redisson is shutdown"));
            return;
        }

        codec = getCodec(codec);
        
        RFuture<RedisConnection> connectionFuture = getConnection();

        RPromise<R> attemptPromise = new RedissonPromise<>();
        mainPromiseListener = (r, e) -> {
            if (mainPromise.isCancelled() && connectionFuture.cancel(false)) {
                log.debug("Connection obtaining canceled for {}", command);
                timeout.cancel();
                if (attemptPromise.cancel(false)) {
                    free();
                }
            }
        };
        
        if (attempt == 0) {
            mainPromise.onComplete((r, e) -> {
                if (this.mainPromiseListener != null) {
                    this.mainPromiseListener.accept(r, e);
                }
            });
        }

        scheduleRetryTimeout(connectionFuture, attemptPromise);

        connectionFuture.onComplete((connection, e) -> {
            if (connectionFuture.isCancelled()) {
                connectionManager.getShutdownLatch().release();
                return;
            }

            if (!connectionFuture.isSuccess()) {
                connectionManager.getShutdownLatch().release();
                exception = convertException(connectionFuture);
                return;
            }

            sendCommand(attemptPromise, connection);

            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    checkWriteFuture(writeFuture, attemptPromise, connection);
                }
            });
        });

        attemptPromise.onComplete((r, e) -> {
            releaseConnection(attemptPromise, connectionFuture);

            checkAttemptPromise(attemptPromise, connectionFuture);
        });
    }

    private void scheduleRetryTimeout(RFuture<RedisConnection> connectionFuture, RPromise<R> attemptPromise) {
        TimerTask retryTimerTask = new TimerTask() {

            @Override
            public void run(Timeout t) throws Exception {
                if (attemptPromise.isDone()) {
                    return;
                }

                if (connectionFuture.cancel(false)) {
                    if (exception == null) {
                        exception = new RedisTimeoutException("Unable to acquire connection! " + connectionFuture +
                                    "Increase connection pool size. "
                                    + "Node source: " + source
                                    + ", command: " + LogHelper.toString(command, params)
                                    + " after " + attempt + " retry attempts");
                    }
                } else {
                    if (connectionFuture.isSuccess()) {
                        if (writeFuture == null || !writeFuture.isDone()) {
                            if (attempt == attempts) {
                                if (writeFuture != null && writeFuture.cancel(false)) {
                                    if (exception == null) {
                                        long totalSize = 0;
                                        if (params != null) {
                                            for (Object param : params) {
                                                if (param instanceof ByteBuf) {
                                                    totalSize += ((ByteBuf) param).readableBytes();
                                                }
                                            }
                                        }

                                        exception = new RedisTimeoutException("Command still hasn't been written into connection! " +
                                                "Try to increase nettyThreads setting. Payload size in bytes: " + totalSize
                                                + ". Node source: " + source + ", connection: " + connectionFuture.getNow()
                                                + ", command: " + LogHelper.toString(command, params)
                                                + " after " + attempt + " retry attempts");
                                    }
                                    attemptPromise.tryFailure(exception);
                                }
                                return;
                            }
                            attempt++;

                            scheduleRetryTimeout(connectionFuture, attemptPromise);
                            return;
                        }

                        if (writeFuture.isSuccess()) {
                            return;
                        }
                    }
                }

                if (mainPromise.isCancelled()) {
                    if (attemptPromise.cancel(false)) {
                        free();
                    }
                    return;
                }

                if (attempt == attempts) {
                    // filled out in connectionFuture or writeFuture handler
                    if (exception != null) {
                        attemptPromise.tryFailure(exception);
                    }
                    return;
                }
                if (!attemptPromise.cancel(false)) {
                    return;
                }

                attempt++;
                if (log.isDebugEnabled()) {
                    log.debug("attempt {} for command {} and params {}",
                            attempt, command, LogHelper.toString(params));
                }
                
                mainPromiseListener = null;

                execute();
            }

        };

        timeout = connectionManager.newTimeout(retryTimerTask, retryInterval, TimeUnit.MILLISECONDS);
    }
    
    protected void free() {
        free(params);
    }
    
    protected void free(Object[] params) {
        for (Object obj : params) {
            ReferenceCountUtil.safeRelease(obj);
        }
    }
    
    private void checkWriteFuture(ChannelFuture future, RPromise<R> attemptPromise, RedisConnection connection) {
        if (future.isCancelled() || attemptPromise.isDone()) {
            return;
        }

        if (!future.isSuccess()) {
            exception = new WriteRedisConnectionException(
                    "Unable to write command into connection! Node source: " + source + ", connection: " + connection +
                    ", command: " + LogHelper.toString(command, params)
                    + " after " + attempt + " retry attempts", future.cause());
            if (attempt == attempts) {
                attemptPromise.tryFailure(exception);
            }
            return;
        }

        timeout.cancel();

        scheduleResponseTimeout(attemptPromise, connection);
    }

    private void scheduleResponseTimeout(RPromise<R> attemptPromise, RedisConnection connection) {
        long timeoutTime = responseTimeout;
        if (command != null
                && (RedisCommands.BLOCKING_COMMAND_NAMES.contains(command.getName())
                        || RedisCommands.BLOCKING_COMMANDS.contains(command))) {
            Long popTimeout = null;
            if (RedisCommands.BLOCKING_COMMANDS.contains(command)) {
                for (int i = 0; i < params.length-1; i++) {
                    if ("BLOCK".equals(params[i])) {
                        popTimeout = Long.valueOf(params[i+1].toString()) / 1000;
                        break;
                    }
                }
            } else {
                popTimeout = Long.valueOf(params[params.length - 1].toString());
            }
            
            handleBlockingOperations(attemptPromise, connection, popTimeout);
            if (popTimeout == 0) {
                return;
            }
            timeoutTime += popTimeout * 1000;
            // add 1 second due to issue https://github.com/antirez/redis/issues/874
            timeoutTime += 1000;
        }

        long timeoutAmount = timeoutTime;
        TimerTask timeoutResponseTask = timeout -> {
            if (isResendAllowed(attempt, attempts)) {
                if (!attemptPromise.cancel(false)) {
                    return;
                }

                connectionManager.newTimeout(t -> {
                    attempt++;
                    if (log.isDebugEnabled()) {
                        log.debug("attempt {} for command {} and params {}",
                                attempt, command, LogHelper.toString(params));
                    }

                    mainPromiseListener = null;
                    execute();
                }, retryInterval, TimeUnit.MILLISECONDS);
                return;
            }

            attemptPromise.tryFailure(
                    new RedisResponseTimeoutException("Redis server response timeout (" + timeoutAmount + " ms) occured"
                            + " after " + attempt + " retry attempts. Increase nettyThreads and/or timeout settings. Try to define pingConnectionInterval setting. Command: "
                            + LogHelper.toString(command, params) + ", channel: " + connection.getChannel()));
        };

        timeout = connectionManager.newTimeout(timeoutResponseTask, timeoutTime, TimeUnit.MILLISECONDS);
    }

    protected boolean isResendAllowed(int attempt, int attempts) {
        return attempt < attempts;
    }

    private void handleBlockingOperations(RPromise<R> attemptPromise, RedisConnection connection, Long popTimeout) {
        FutureListener<Void> listener = f -> {
            mainPromise.tryFailure(new RedissonShutdownException("Redisson is shutdown"));
        };

        Timeout scheduledFuture;
        if (popTimeout != 0) {
            // handling cases when connection has been lost
            scheduledFuture = connectionManager.newTimeout(timeout -> {
                if (attemptPromise.trySuccess(null)) {
                    connection.forceFastReconnectAsync();
                }
            }, popTimeout + 1, TimeUnit.SECONDS);
        } else {
            scheduledFuture = null;
        }

        mainPromise.onComplete((res, e) -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel();
            }

            synchronized (listener) {
                connectionManager.getShutdownPromise().removeListener(listener);
            }

            // handling cancel operation for blocking commands
            if ((mainPromise.isCancelled()
                    || mainPromise.cause() instanceof  InterruptedException)
                        && !attemptPromise.isDone()) {
                log.debug("Canceled blocking operation {} used {}", command, connection);
                connection.forceFastReconnectAsync().onComplete((r, ex) -> {
                    attemptPromise.cancel(true);
                });
                return;
            }

            if (e instanceof RedissonShutdownException) {
                attemptPromise.tryFailure(e);
            }
        });

        synchronized (listener) {
            if (!mainPromise.isDone()) {
                connectionManager.getShutdownPromise().addListener(listener);
            }
        }
    }
    
    protected void checkAttemptPromise(RPromise<R> attemptFuture, RFuture<RedisConnection> connectionFuture) {
        timeout.cancel();
        if (attemptFuture.isCancelled()) {
            return;
        }

        try {
            mainPromiseListener = null;
            
            if (attemptFuture.cause() instanceof RedisMovedException && !ignoreRedirect) {
                RedisMovedException ex = (RedisMovedException) attemptFuture.cause();
                if (source.getRedirect() == Redirect.MOVED) {
                    mainPromise.tryFailure(new RedisException("MOVED redirection loop detected. Node " + source.getAddr() + " has further redirect to " + ex.getUrl()));
                    return;
                }

                onException();
                
                source = new NodeSource(ex.getSlot(), connectionManager.applyNatMap(ex.getUrl()), Redirect.MOVED);
                execute();
                return;
            }
            
            if (attemptFuture.cause() instanceof RedisAskException && !ignoreRedirect) {
                RedisAskException ex = (RedisAskException) attemptFuture.cause();
                
                onException();
                
                source = new NodeSource(ex.getSlot(), connectionManager.applyNatMap(ex.getUrl()), Redirect.ASK);
                execute();
                return;
            }
            
            if (attemptFuture.cause() instanceof RedisLoadingException
                    || attemptFuture.cause() instanceof RedisTryAgainException
                        || attemptFuture.cause() instanceof RedisClusterDownException
                            || attemptFuture.cause() instanceof RedisBusyException) {
                if (attempt < attempts) {
                    onException();
                    connectionManager.newTimeout(timeout -> {
                        attempt++;
                        execute();
                    }, retryInterval, TimeUnit.MILLISECONDS);
                    return;
                }
            }
            
            free();
            
            handleResult(attemptFuture, connectionFuture);
            
        } catch (Exception e) {
            handleError(connectionFuture, e);
        }
    }

    protected void handleResult(RPromise<R> attemptPromise, RFuture<RedisConnection> connectionFuture) throws ReflectiveOperationException {
        if (attemptPromise.isSuccess()) {
            R res = attemptPromise.getNow();
            if (res instanceof ScanResult) {
                ((ScanResult) res).setRedisClient(connectionFuture.getNow().getRedisClient());
            }
            
            handleSuccess(mainPromise, connectionFuture, res);
        } else {
            handleError(connectionFuture, attemptPromise.cause());
        }
    }
    
    protected void onException() {
    }

    protected void handleError(RFuture<RedisConnection> connectionFuture, Throwable cause) {
        mainPromise.tryFailure(cause);
    }

    protected void handleSuccess(RPromise<R> promise, RFuture<RedisConnection> connectionFuture, R res) throws ReflectiveOperationException {
        if (objectBuilder != null) {
            handleReference(promise, res);
        } else {
            promise.trySuccess(res);
        }
    }

    private void handleReference(RPromise<R> promise, R res) throws ReflectiveOperationException {
        if (objectBuilder != null) {
            promise.trySuccess((R) objectBuilder.tryHandleReference(res, referenceType));
        } else {
            promise.trySuccess(res);
        }
    }

    protected void sendCommand(RPromise<R> attemptPromise, RedisConnection connection) {
        if (source.getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<>(2);
            RPromise<Void> promise = new RedissonPromise<>();
            list.add(new CommandData<>(promise, codec, RedisCommands.ASKING, new Object[]{}));
            list.add(new CommandData<>(attemptPromise, codec, command, params));
            RPromise<Void> main = new RedissonPromise<>();
            writeFuture = connection.send(new CommandsData(main, list, false, false));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("acquired connection for command {} and params {} from slot {} using node {}... {}",
                        command, LogHelper.toString(params), source, connection.getRedisClient().getAddr(), connection);
            }
            writeFuture = connection.send(new CommandData<>(attemptPromise, codec, command, params));
        }
    }
    
    protected void releaseConnection(RPromise<R> attemptPromise, RFuture<RedisConnection> connectionFuture) {
        if (!connectionFuture.isSuccess()) {
            return;
        }

        RedisConnection connection = connectionFuture.getNow();
        connection.setQueued(false);
        connectionManager.getShutdownLatch().release();
        if (readOnlyMode) {
            connectionManager.releaseRead(source, connection);
        } else {
            connectionManager.releaseWrite(source, connection);
        }

        if (log.isDebugEnabled()) {
            log.debug("connection released for command {} and params {} from slot {} using connection {}",
                    command, LogHelper.toString(params), source, connection);
        }
    }

    public RedisClient getRedisClient() {
        return connectionFuture.getNow().getRedisClient();
    }

    protected RFuture<RedisConnection> getConnection() {
        if (readOnlyMode) {
            connectionFuture = connectionManager.connectionReadOp(source, command);
        } else {
            connectionFuture = connectionManager.connectionWriteOp(source, command);
        }
        return connectionFuture;
    }
    
    private static final Map<ClassLoader, Map<Codec, Codec>> CODECS = new LRUCacheMap<>(25, 0, 0);

    protected Codec getCodec(Codec codec) {
        if (codec == null) {
            return null;
        }

        if (!connectionManager.getCfg().isUseThreadClassLoader()) {
            return codec;
        }

        for (Class<?> clazz : BaseCodec.SKIPPED_CODECS) {
            if (clazz.isAssignableFrom(codec.getClass())) {
                return codec;
            }
        }

        Codec codecToUse = codec;
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        if (threadClassLoader != null) {
            Map<Codec, Codec> map = CODECS.computeIfAbsent(threadClassLoader, k ->
                                            new LRUCacheMap<>(200, 0, 0));
            codecToUse = map.get(codec);
            if (codecToUse == null) {
                try {
                    codecToUse = codec.getClass().getConstructor(ClassLoader.class, codec.getClass()).newInstance(threadClassLoader, codec);
                } catch (NoSuchMethodException e) {
                    codecToUse = codec;
                    // skip
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                map.put(codec, codecToUse);
            }
        }
        return codecToUse;
    }
    
    protected <T> RedisException convertException(RFuture<T> future) {
        if (future.cause() instanceof RedisException) {
            return (RedisException) future.cause();
        }
        return new RedisException("Unexpected exception while processing command", future.cause());
    }

    
}
