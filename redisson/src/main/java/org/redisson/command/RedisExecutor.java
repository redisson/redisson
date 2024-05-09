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
package org.redisson.command;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.RedissonShutdownException;
import org.redisson.ScanResult;
import org.redisson.api.NodeType;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListMultiDecoder2;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 * @param <R> type of returned value
 */
@SuppressWarnings({"NestedIfDepth", "ParameterNumber"})
public class RedisExecutor<V, R> {

    static final Logger log = LoggerFactory.getLogger(RedisExecutor.class);

    final boolean readOnlyMode;
    final RedisCommand<V> command;
    final Object[] params;
    final CompletableFuture<R> mainPromise;
    final boolean ignoreRedirect;
    final RedissonObjectBuilder objectBuilder;
    final ConnectionManager connectionManager;
    final RedissonObjectBuilder.ReferenceType referenceType;
    final boolean noRetry;
    final int attempts;
    final int retryInterval;
    final int responseTimeout;
    final boolean trackChanges;

    CompletableFuture<RedisConnection> connectionFuture;
    boolean reuseConnection;
    NodeSource source;
    MasterSlaveEntry entry;
    Codec codec;
    volatile int attempt;
    volatile Optional<Timeout> timeout = Optional.empty();
    volatile BiConsumer<R, Throwable> mainPromiseListener;
    volatile ChannelFuture writeFuture;
    volatile RedisException exception;

    public RedisExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
                         Object[] params, CompletableFuture<R> mainPromise, boolean ignoreRedirect,
                         ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder,
                         RedissonObjectBuilder.ReferenceType referenceType, boolean noRetry,
                         int retryAttempts, int retryInterval, int responseTimeout,
                         boolean trackChanges) {
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
        this.noRetry = noRetry;

        this.attempts = retryAttempts;
        this.retryInterval = retryInterval;
        this.responseTimeout = responseTimeout;
        this.referenceType = referenceType;
        this.trackChanges = trackChanges;
    }

    public void execute() {
        if (mainPromise.isCancelled()) {
            free();
            return;
        }

        if (getClass() == RedisExecutor.class) {
            connectionManager.getServiceManager().addFuture(mainPromise);
            mainPromise.whenComplete((r, e) -> {
                connectionManager.getServiceManager().removeFuture(mainPromise);
            });
        }

        if (connectionManager.getServiceManager().isShuttingDown()) {
            free();
            mainPromise.completeExceptionally(new RedissonShutdownException("Redisson is shutdown"));
            return;
        }

        try {
            codec = getCodec(codec);

            CompletableFuture<R> attemptPromise = new CompletableFuture<>();
            CompletableFuture<RedisConnection> connectionFuture = getConnection(attemptPromise);
            mainPromiseListener = (r, e) -> {
                if (!mainPromise.isCompletedExceptionally()) {
                    return;
                }

                if (connectionFuture.completeExceptionally(new CancellationException())) {
                    log.debug("Connection obtaining canceled for {}", command);
                    timeout.ifPresent(Timeout::cancel);
                    if (attemptPromise.completeExceptionally(new CancellationException())) {
                        free();
                    }
                    return;
                }

                if (command.isBlockingCommand()) {
                    if (writeFuture.cancel(false)) {
                        attemptPromise.completeExceptionally(new CancellationException());
                    } else {
                        RedisConnection c = connectionFuture.getNow(null);
                        c.forceFastReconnectAsync().whenComplete((res, ex) -> {
                            attemptPromise.completeExceptionally(new CancellationException());
                        });
                    }
                }
            };

            if (attempt == 0) {
                mainPromise.whenComplete((r, e) -> {
                    if (this.mainPromiseListener != null) {
                        this.mainPromiseListener.accept(r, e);
                    }
                });
            }

            scheduleRetryTimeout(connectionFuture, attemptPromise);

            scheduleConnectionTimeout(attemptPromise, connectionFuture);

            connectionFuture.whenComplete((connection, e) -> {
                if (connectionFuture.isCancelled()) {
                    return;
                }

                if (connectionFuture.isDone() && connectionFuture.isCompletedExceptionally()) {
                    exception = convertException(connectionFuture);
                    tryComplete(attemptPromise, exception);
                    return;
                }

                try {
                    sendCommand(attemptPromise, connection);
                } catch (Exception ex) {
                    free();
                    handleError(connectionFuture, e);
                    return;
                }

                scheduleWriteTimeout(attemptPromise);

                writeFuture.addListener((ChannelFutureListener) future -> {
                    checkWriteFuture(writeFuture, attemptPromise, connection);
                });
            });

            attemptPromise.whenComplete((r, e) -> {
                releaseConnection(attemptPromise, connectionFuture);

                checkAttemptPromise(attemptPromise, connectionFuture);
            }).whenComplete((r, e) -> {
                if (e != null) {
                    log.error(e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            free();
            handleError(connectionFuture, e);
            throw e;
        }
    }

    private void scheduleConnectionTimeout(CompletableFuture<R> attemptPromise, CompletableFuture<RedisConnection> connectionFuture) {
        if (retryInterval > 0 && attempts > 0) {
            return;
        }

        timeout.ifPresent(Timeout::cancel);

        TimerTask task = timeout -> {
            if (connectionFuture.completeExceptionally(new CancellationException())) {
                exception = new RedisTimeoutException("Unable to acquire connection! " + this.connectionFuture +
                        "Increase connection pool size or timeout. "
                        + "Node source: " + source
                        + ", command: " + LogHelper.toString(command, params)
                        + " after " + attempt + " retry attempts");

                attemptPromise.completeExceptionally(exception);
            }
        };

        timeout = Optional.of(connectionManager.getServiceManager().newTimeout(task, responseTimeout, TimeUnit.MILLISECONDS));
    }

    private void scheduleWriteTimeout(CompletableFuture<R> attemptPromise) {
        if (retryInterval > 0 && attempts > 0) {
            return;
        }

        timeout.ifPresent(Timeout::cancel);

        TimerTask task = timeout -> {
            if (writeFuture.cancel(false)) {
                exception = new RedisTimeoutException("Command still hasn't been written into connection! " +
                        "Check CPU usage of the JVM. Check that there are no blocking invocations in async/reactive/rx listeners or subscribeOnElements method. Check connection with Redis node: " + connectionFuture.join().getRedisClient().getAddr() +
                        " for TCP packet drops. Try to increase nettyThreads setting. "
                        + " Node source: " + source + ", connection: " + connectionFuture.join()
                        + ", command: " + LogHelper.toString(command, params)
                        + " after " + attempt + " retry attempts");
                attemptPromise.completeExceptionally(exception);
            }
        };

        timeout = Optional.of(connectionManager.getServiceManager().newTimeout(task, responseTimeout, TimeUnit.MILLISECONDS));
    }

    private void scheduleRetryTimeout(CompletableFuture<RedisConnection> connectionFuture, CompletableFuture<R> attemptPromise) {
        if (retryInterval == 0 || attempts == 0) {
            return;
        }

        TimerTask retryTimerTask = new TimerTask() {

            @Override
            public void run(Timeout t) throws Exception {
                if (attemptPromise.isDone()) {
                    return;
                }

                if (connectionFuture.completeExceptionally(new CancellationException())) {
                    exception = new RedisTimeoutException("Unable to acquire connection! " + connectionFuture +
                                "Increase connection pool size. "
                                + "Node source: " + source
                                + ", command: " + LogHelper.toString(command, params)
                                + " after " + attempt + " retry attempts");
                } else {
                    if (connectionFuture.isDone() && !connectionFuture.isCompletedExceptionally()) {
                        if (writeFuture == null || !writeFuture.isDone()) {
                            if (attempt == attempts) {
                                if (writeFuture != null && writeFuture.cancel(false)) {
                                    if (exception == null) {
                                        exception = new RedisTimeoutException("Command still hasn't been written into connection! " +
                                                "Check CPU usage of the JVM. Check that there are no blocking invocations in async/reactive/rx listeners or subscribeOnElements method. Check connection with Redis node: " + getNow(connectionFuture).getRedisClient().getAddr() +
                                                " for TCP packet drops. Try to increase nettyThreads setting. "
                                                + " Node source: " + source + ", connection: " + getNow(connectionFuture)
                                                + ", command: " + LogHelper.toString(command, params)
                                                + " after " + attempt + " retry attempts");
                                    }
                                    attemptPromise.completeExceptionally(exception);
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

                if (mainPromise.isCompletedExceptionally()) {
                    Throwable c = cause(mainPromise);
                    if (c instanceof CancellationException || c instanceof RedissonShutdownException) {
                        if (attemptPromise.completeExceptionally(new CancellationException())) {
                            free();
                        }
                    }
                    return;
                }

                if (attempt == attempts) {
                    // filled out in connectionFuture or writeFuture handler
                    if (exception != null) {
                        attemptPromise.completeExceptionally(exception);
                    }
                    return;
                }
                if (!attemptPromise.completeExceptionally(new CancellationException())) {
                    return;
                }

                attempt++;
                if (log.isDebugEnabled()) {
                    log.debug("attempt {} for command {} and params {} to {}",
                            attempt, command, LogHelper.toString(params), source);
                }

                mainPromiseListener = null;

                execute();
            }

        };

        timeout = Optional.of(connectionManager.getServiceManager().newTimeout(retryTimerTask, retryInterval, TimeUnit.MILLISECONDS));
    }
    
    protected void free() {
        free(params);
    }
    
    protected void free(Object[] params) {
        for (Object obj : params) {
            ReferenceCountUtil.safeRelease(obj);
        }
    }
    
    private void checkWriteFuture(ChannelFuture future, CompletableFuture<R> attemptPromise, RedisConnection connection) {
        if (future.isCancelled() || attemptPromise.isDone()) {
            return;
        }

        if (!future.isSuccess()) {
            exception = new WriteRedisConnectionException(
                    "Unable to write command into connection! Check CPU usage of the JVM. Try to increase nettyThreads setting. Node source: "
                    + source + ", connection: " + connection +
                    ", command: " + LogHelper.toString(command, params)
                    + " after " + attempt + " retry attempts", future.cause());
            tryComplete(attemptPromise, exception);
            return;
        }

        scheduleResponseTimeout(attemptPromise, connection);
    }

    private void tryComplete(CompletableFuture<R> attemptPromise, RedisException exception) {
        if (attempt == attempts) {
            attemptPromise.completeExceptionally(exception);
        } else if (retryInterval == 0) {
            attempt++;

            if (log.isDebugEnabled()) {
                log.debug("attempt {} for command {} and params {} to {}",
                        attempt, command, LogHelper.toString(params), source);
            }

            mainPromiseListener = null;
            execute();
        }
    }

    private void scheduleResponseTimeout(CompletableFuture<R> attemptPromise, RedisConnection connection) {
        timeout.ifPresent(Timeout::cancel);

        long timeoutTime = responseTimeout;
        if (command != null && command.isBlockingCommand()) {
            long popTimeout = 0;
            if (RedisCommands.BLOCKING_COMMANDS.contains(command)) {
                for (int i = 0; i < params.length-1; i++) {
                    if ("BLOCK".equals(params[i])) {
                        popTimeout = Long.valueOf(params[i+1].toString());
                        break;
                    }
                }
            } else {
                if (RedisCommands.BZMPOP.getName().equals(command.getName())) {
                    popTimeout = Long.valueOf(params[0].toString()) * 1000;
                } else {
                    popTimeout = Long.valueOf(params[params.length - 1].toString()) * 1000;
                }
            }

            handleBlockingOperations(attemptPromise, connection, popTimeout);
            if (popTimeout == 0) {
                return;
            }
            timeoutTime += popTimeout;
            // add 1 second due to issue https://github.com/antirez/redis/issues/874
            timeoutTime += 1000;
        }

        long timeoutAmount = timeoutTime;
        TimerTask timeoutResponseTask = timeout -> {
            if (isResendAllowed(attempt, attempts)) {
                if (!attemptPromise.completeExceptionally(new CancellationException())) {
                    return;
                }

                connectionManager.getServiceManager().newTimeout(t -> {
                    attempt++;
                    if (log.isDebugEnabled()) {
                        log.debug("response timeout. new attempt {} for command {} and params {} node {}",
                                attempt, command, LogHelper.toString(params), source);
                    }

                    mainPromiseListener = null;
                    execute();
                }, retryInterval, TimeUnit.MILLISECONDS);
                return;
            }

            attemptPromise.completeExceptionally(
                    new RedisResponseTimeoutException("Redis server response timeout (" + timeoutAmount + " ms) occured"
                            + " after " + attempt + " retry attempts,"
                            + " is non-idempotent command: " + (command != null && command.isNoRetry())
                            + " Check connection with Redis node: " + connection.getRedisClient().getAddr() + " for TCP packet drops or bandwidth limits. "
                            + " Try to increase nettyThreads and/or timeout settings. Command: "
                            + LogHelper.toString(command, params) + ", channel: " + connection.getChannel()));
        };

        timeout = Optional.of(connectionManager.getServiceManager().newTimeout(timeoutResponseTask, timeoutTime, TimeUnit.MILLISECONDS));
    }

    private boolean isResendAllowed(int attempt, int attempts) {
        return attempt < attempts
                && !noRetry
                    && (command == null || (!command.isBlockingCommand() && !command.isNoRetry()));
    }

    private void handleBlockingOperations(CompletableFuture<R> attemptPromise, RedisConnection connection, long popTimeout) {
        Timeout scheduledFuture;
        if (popTimeout != 0) {
            // handling cases when connection has been lost
            scheduledFuture = connectionManager.getServiceManager().newTimeout(timeout -> {
                R res = null;
                if (command.getReplayMultiDecoder() instanceof ObjectListReplayDecoder
                        || command.getReplayMultiDecoder() instanceof ListMultiDecoder2) {
                    res = (R) Collections.emptyList();
                }
                if (attemptPromise.complete(res)) {
                    connection.forceFastReconnectAsync();
                }
            }, popTimeout + 3000, TimeUnit.MILLISECONDS);
        } else {
            scheduledFuture = null;
        }

        mainPromise.whenComplete((res, e) -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel();
            }

            // handling cancel operation for blocking commands
            if ((mainPromise.isCancelled()
                    || e instanceof  InterruptedException)
                        && !attemptPromise.isDone()) {
                log.debug("Canceled blocking operation {} used {}", command, connection);
                connection.forceFastReconnectAsync().whenComplete((r, ex) -> {
                    attemptPromise.completeExceptionally(new CancellationException());
                });
                return;
            }

            if (e instanceof RedissonShutdownException) {
                attemptPromise.completeExceptionally(e);
            }
        });
    }

    protected final Throwable cause(CompletableFuture<?> future) {
        try {
            future.getNow(null);
            return null;
        } catch (CompletionException ex2) {
            return ex2.getCause();
        } catch (CancellationException ex1) {
            return ex1;
        }
    }

    protected void checkAttemptPromise(CompletableFuture<R> attemptFuture, CompletableFuture<RedisConnection> connectionFuture) {
        timeout.ifPresent(Timeout::cancel);

        if (attemptFuture.isCancelled()) {
            return;
        }

        try {
            mainPromiseListener = null;

            Throwable cause = cause(attemptFuture);
            if (cause instanceof RedisWrongPasswordException) {
                if (attempt < attempts) {
                    onException();

                    reuseConnection = true;
                    CompletionStage<Void> f = connectionFuture.join().forceFastReconnectAsync();
                    f.thenAccept(v -> {
                        attempt++;
                        execute();
                    });
                    return;
                }
            }

            if (cause instanceof RedisMovedException && !ignoreRedirect) {
                RedisMovedException ex = (RedisMovedException) cause;
                if (source.getRedirect() == Redirect.MOVED
                        && source.getAddr().equals(ex.getUrl())) {
                    mainPromise.completeExceptionally(new RedisException("MOVED redirection loop detected. Node " + source.getAddr() + " has further redirect to " + ex.getUrl()));
                    return;
                }

                onException();

                CompletableFuture<RedisURI> ipAddrFuture = connectionManager.getServiceManager().resolveIP(ex.getUrl());
                ipAddrFuture.whenComplete((ip, e) -> {
                    if (e != null) {
                        free();
                        handleError(connectionFuture, e);
                        return;
                    }
                    source = new NodeSource(ex.getSlot(), ip, Redirect.MOVED);
                    execute();
                });
                return;
            }

            if (cause instanceof RedisAskException && !ignoreRedirect) {
                RedisAskException ex = (RedisAskException) cause;

                onException();

                CompletableFuture<RedisURI> ipAddrFuture = connectionManager.getServiceManager().resolveIP(ex.getUrl());
                ipAddrFuture.whenComplete((ip, e) -> {
                    if (e != null) {
                        free();
                        handleError(connectionFuture, e);
                        return;
                    }
                    source = new NodeSource(ex.getSlot(), ip, Redirect.ASK);
                    execute();
                });
                return;
            }

            if (cause instanceof RedisLoadingException) {
                RedisConnection connection = connectionFuture.getNow(null);
                if (connection != null) {
                    ClientConnectionsEntry ce = entry.getEntry(connection.getRedisClient());
                    if (ce != null && ce.getNodeType() == NodeType.SLAVE) {
                        source = new NodeSource(entry.getClient());
                        execute();
                        return;
                    }
                }
            }

            if (cause instanceof RedisRetryException
                    || cause instanceof RedisReadonlyException) {
                if (attempt < attempts) {
                    onException();
                    connectionManager.getServiceManager().newTimeout(timeout -> {
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

    protected void handleResult(CompletableFuture<R> attemptPromise, CompletableFuture<RedisConnection> connectionFuture) throws ReflectiveOperationException {
        R res;
        try {
            res = attemptPromise.getNow(null);
        } catch (CompletionException e) {
            handleError(connectionFuture, e.getCause());
            return;
        } catch (CancellationException e) {
            handleError(connectionFuture, e);
            return;
        }

        if (res instanceof ScanResult) {
            ((ScanResult) res).setRedisClient(getNow(connectionFuture).getRedisClient());
        }

        handleSuccess(mainPromise, connectionFuture, res);
    }

    protected void onException() {
    }

    protected void handleError(CompletableFuture<RedisConnection> connectionFuture, Throwable cause) {
        mainPromise.completeExceptionally(cause);
        if (connectionFuture == null) {
            return;
        }

        RedisClient client = connectionFuture.join().getRedisClient();
        FailedNodeDetector detector = client.getConfig().getFailedNodeDetector();
        detector.onCommandFailed(cause);
        if (detector.isNodeFailed()) {
            log.error("Redis node {} has been marked as failed as failed according to the detection logic defined in {}",
                            entry.getClient().getAddr(), detector);
            entry.shutdownAndReconnectAsync(client, cause);
        }
    }

    protected void handleSuccess(CompletableFuture<R> promise, CompletableFuture<RedisConnection> connectionFuture, R res) throws ReflectiveOperationException {
        if (objectBuilder != null) {
            promise.complete((R) objectBuilder.tryHandleReference(res, referenceType));
        } else {
            promise.complete(res);
        }
        connectionFuture.join().getRedisClient().getConfig().getFailedNodeDetector().onCommandSuccessful();
    }

    protected void sendCommand(CompletableFuture<R> attemptPromise, RedisConnection connection) {
        if (source.getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<>(2);
            CompletableFuture<Void> promise = new CompletableFuture<>();
            list.add(new CommandData<>(promise, codec, RedisCommands.ASKING, new Object[]{}));
            list.add(new CommandData<>(attemptPromise, codec, command, params));
            CompletableFuture<Void> main = new CompletableFuture<>();
            writeFuture = connection.send(new CommandsData(main, list, false, false));
        } else {
            if (log.isDebugEnabled()) {
                String connectionType = " ";
                if (connection instanceof RedisPubSubConnection) {
                    connectionType = " pubsub ";
                }
                log.debug("acquired{}connection for command {} and params {} from slot {} using node {}... {}",
                        connectionType, command, LogHelper.toString(params), source, connection.getRedisClient().getAddr(), connection);
            }
            writeFuture = connection.send(new CommandData<>(attemptPromise, codec, command, params));

            if (connectionManager.getServiceManager().getConfig().getMasterConnectionPoolSize() < 10
                    && !command.isBlockingCommand()) {
                release(connection);
            }
        }
    }

    protected void releaseConnection(CompletableFuture<R> attemptPromise, CompletableFuture<RedisConnection> connectionFuture) {
        if (connectionFuture.isDone() && connectionFuture.isCompletedExceptionally()) {
            return;
        }

        Throwable cause = cause(attemptPromise);
        if (cause instanceof RedisWrongPasswordException
                && attempt < attempts) {
            return;
        }

        RedisConnection connection = getNow(connectionFuture);
        if (connectionManager.getServiceManager().getConfig().getMasterConnectionPoolSize() < 10) {
            if (source.getRedirect() == Redirect.ASK
                    || getClass() != RedisExecutor.class
                        || (command != null && command.isBlockingCommand())) {
                release(connection);
            }
        } else {
            release(connection);
        }

        if (log.isDebugEnabled()) {
            String connectionType = " ";
            if (connection instanceof RedisPubSubConnection) {
                connectionType = " pubsub ";
            }

            log.debug("connection{}released for command {} and params {} from slot {} using connection {}",
                    connectionType, command, LogHelper.toString(params), source, connection);
        }
    }

    private void release(RedisConnection connection) {
        if (readOnlyMode) {
            entry.releaseRead(connection, trackChanges);
        } else {
            entry.releaseWrite(connection);
        }
    }

    public RedisClient getRedisClient() {
        return getNow(connectionFuture).getRedisClient();
    }

    protected CompletableFuture<RedisConnection> getConnection(CompletableFuture<R> attemptPromise) {
        if (reuseConnection) {
            reuseConnection = false;
            return connectionFuture;
        }
        if (readOnlyMode) {
            connectionFuture = connectionReadOp(command, attemptPromise);
        } else {
            connectionFuture = connectionWriteOp(command, attemptPromise);
        }
        return connectionFuture;
    }

    private static final Map<ClassLoader, Map<Codec, Codec>> CODECS = new LRUCacheMap<>(25, 0, 0);

    protected final Codec getCodec(Codec codec) {
        if (codec == null) {
            return null;
        }

        if (!connectionManager.getServiceManager().getCfg().isUseThreadClassLoader()) {
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

    protected final <T> T getNow(CompletableFuture<T> future) {
        try {
            return future.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> RedisException convertException(CompletableFuture<T> future) {
        Throwable cause = cause(future);
        if (cause instanceof RedisException) {
            return (RedisException) cause;
        }
        return new RedisException("Unexpected exception while processing command", cause);
    }

    final CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, CompletableFuture<R> attemptPromise) {
        try {
            // TODO make the method async
            entry = getEntry(true);
        } catch (Exception e) {
            attemptPromise.completeExceptionally(e);
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        if (entry == null) {
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(connectionManager.getServiceManager().createNodeNotFoundException(source));
            return f;
        }

        if (source.getRedirect() != null) {
            return entry.connectionReadOp(command, source.getAddr());
        }
        if (source.getRedisClient() != null) {
            return entry.connectionReadOp(command, source.getRedisClient(), trackChanges);
        }

        return entry.connectionReadOp(command, trackChanges);
    }

    final CompletableFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command, CompletableFuture<R> attemptPromise) {
        try {
            // TODO make the method async
            entry = getEntry(false);
        } catch (Exception e) {
            attemptPromise.completeExceptionally(e);
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        if (entry == null) {
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(connectionManager.getServiceManager().createNodeNotFoundException(source));
            return f;
        }
        // fix for https://github.com/redisson/redisson/issues/1548
        if (source.getRedirect() != null
                && !source.getAddr().equals(entry.getClient().getAddr())
                && entry.hasSlave(source.getAddr())) {
            return entry.redirectedConnectionWriteOp(command, source.getAddr());
        }
        return entry.connectionWriteOp(command);
    }

    private MasterSlaveEntry getEntry(boolean read) {
        if (source.getRedirect() != null) {
            return connectionManager.getEntry(source.getAddr());
        }

        MasterSlaveEntry entry = source.getEntry();
        if (source.getRedisClient() != null) {
            entry = connectionManager.getEntry(source.getRedisClient());
        }
        if (entry == null && source.getSlot() != null) {
            if (read) {
                entry = connectionManager.getReadEntry(source.getSlot());
            } else {
                entry = connectionManager.getWriteEntry(source.getSlot());
            }
        }
        return entry;
    }

}
