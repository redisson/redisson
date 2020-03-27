/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
import org.redisson.RedissonReference;
import org.redisson.RedissonShutdownException;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.*;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
            ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder) {
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

        RPromise<R> attemptPromise = new RedissonPromise<R>();
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

            releaseConnection(attemptPromise, connectionFuture);
        });

        attemptPromise.onComplete((r, e) -> {
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
                        exception = new RedisTimeoutException("Unable to acquire connection! Increase connection pool size and/or retryInterval settings "
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

                                        exception = new RedisTimeoutException("Command still hasn't been written into connection! Increase nettyThreads and/or retryInterval settings. Payload size in bytes: " + totalSize
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
                    attemptPromise.tryFailure(exception);
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
                boolean found = false;
                for (Object param : params) {
                    if (found) {
                        popTimeout = Long.valueOf(param.toString()) / 1000;
                        break;
                    }
                    if ("BLOCK".equals(param)) {
                        found = true; 
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
        TimerTask timeoutTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (attempt < attempts) {
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
                    return;
                }

                attemptPromise.tryFailure(
                        new RedisResponseTimeoutException("Redis server response timeout (" + timeoutAmount + " ms) occured"
                                + " after " + attempt + " retry attempts. Increase nettyThreads and/or timeout settings. Try to define pingConnectionInterval setting. Command: "
                                + LogHelper.toString(command, params) + ", channel: " + connection.getChannel()));
            }
        };

        timeout = connectionManager.newTimeout(timeoutTask, timeoutTime, TimeUnit.MILLISECONDS);
    }

    private void handleBlockingOperations(RPromise<R> attemptPromise, RedisConnection connection, Long popTimeout) {
        FutureListener<Void> listener = f -> {
            mainPromise.tryFailure(new RedissonShutdownException("Redisson is shutdown"));
        };

        Timeout scheduledFuture;
        if (popTimeout != 0) {
            // handling cases when connection has been lost
            scheduledFuture = connectionManager.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    if (attemptPromise.trySuccess(null)) {
                        connection.forceFastReconnectAsync();
                    }
                }
            }, popTimeout, TimeUnit.SECONDS);
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
                    || attemptFuture.cause() instanceof RedisTryAgainException) {
                if (attempt < attempts) {
                    onException();
                    connectionManager.newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            attempt++;
                            execute();
                        }
                    }, Math.min(responseTimeout, 1000), TimeUnit.MILLISECONDS);
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
        promise.trySuccess((R) tryHandleReference(objectBuilder, res));
    }
    
    public static Object tryHandleReference(RedissonObjectBuilder objectBuilder, Object o) throws ReflectiveOperationException {
        boolean hasConversion = false;
        if (o instanceof List) {
            List<Object> r = (List<Object>) o;
            for (int i = 0; i < r.size(); i++) {
                Object ref = tryHandleReference0(objectBuilder, r.get(i));
                if (ref != r.get(i)) {
                    r.set(i, ref);
                }
            }
            return o;
        } else if (o instanceof Set) {
            Set<Object> set = (Set<Object>) o;
            Set<Object> r = (Set<Object>) o;
            boolean useNewSet = o instanceof LinkedHashSet;
            try {
                set = (Set<Object>) o.getClass().getConstructor().newInstance();
            } catch (Exception exception) {
                set = new LinkedHashSet<Object>();
            }
            for (Object i : r) {
                Object ref = tryHandleReference0(objectBuilder, i);
                //Not testing for ref changes because r.add(ref) below needs to
                //fail on the first iteration to be able to perform fall back 
                //if failure happens.
                //
                //Assuming the failure reason is systematic such as put method
                //is not supported or implemented, and not an occasional issue 
                //like only one element fails.
                if (useNewSet) {
                    set.add(ref);
                } else {
                    try {
                        r.add(ref);
                        set.add(i);
                    } catch (Exception e) {
                        //r is not supporting add operation, like 
                        //LinkedHashMap$LinkedEntrySet and others.
                        //fall back to use a new set.
                        useNewSet = true;
                        set.add(ref);
                    }
                }
                hasConversion |= ref != i;
            }

            if (!hasConversion) {
                return o;
            } else if (useNewSet) {
                return set;
            } else if (!set.isEmpty()) {
                r.removeAll(set);
            }
            return o;
        } else if (o instanceof Map) {
            Map<Object, Object> r = (Map<Object, Object>) o;
            for (Map.Entry<Object, Object> e : r.entrySet()) {
                if (e.getKey() instanceof RedissonReference
                        || e.getValue() instanceof RedissonReference) {
                    Object key = e.getKey();
                    Object value = e.getValue();
                    if (e.getKey() instanceof RedissonReference) {
                        key = fromReference(objectBuilder, e.getKey());
                        r.remove(e.getKey());
                    }
                    if (e.getValue() instanceof RedissonReference) {
                        value = fromReference(objectBuilder, e.getValue());
                    }
                    r.put(key, value);
                }
            }

            return o;
        } else if (o instanceof ListScanResult) {
            tryHandleReference(objectBuilder, ((ListScanResult) o).getValues());
            return o;
        } else if (o instanceof MapScanResult) {
            MapScanResult scanResult = (MapScanResult) o;
            Map oldMap = ((MapScanResult) o).getMap();
            Map map = (Map) tryHandleReference(objectBuilder, oldMap);
            if (map != oldMap) {
                MapScanResult<Object, Object> newScanResult
                        = new MapScanResult<Object, Object>(scanResult.getPos(), map);
                newScanResult.setRedisClient(scanResult.getRedisClient());
                return newScanResult;
            } else {
                return o;
            }
        } else {
            return tryHandleReference0(objectBuilder, o);
        }
    }

    private static Object tryHandleReference0(RedissonObjectBuilder objectBuilder, Object o) throws ReflectiveOperationException {
        if (o instanceof RedissonReference) {
            return fromReference(objectBuilder, o);
        } else if (o instanceof ScoredEntry && ((ScoredEntry) o).getValue() instanceof RedissonReference) {
            ScoredEntry<?> se = (ScoredEntry<?>) o;
            return new ScoredEntry(se.getScore(), fromReference(objectBuilder, se.getValue()));
        } else if (o instanceof Map.Entry) {
            Map.Entry old = (Map.Entry) o;
            Object key = tryHandleReference0(objectBuilder, old.getKey());
            Object value = tryHandleReference0(objectBuilder, old.getValue());
            if (value != old.getValue() || key != old.getKey()) {
                return new AbstractMap.SimpleEntry(key, value);
            }
        }
        return o;
    }

    private static Object fromReference(RedissonObjectBuilder objectBuilder, Object res) throws ReflectiveOperationException {
        if (objectBuilder == null) {
            return res;
        }
        
        return objectBuilder.fromReference((RedissonReference) res);
    }
    
    protected void sendCommand(RPromise<R> attemptPromise, RedisConnection connection) {
        if (source.getRedirect() == Redirect.ASK) {
            List<CommandData<?, ?>> list = new ArrayList<CommandData<?, ?>>(2);
            RPromise<Void> promise = new RedissonPromise<Void>();
            list.add(new CommandData<Void, Void>(promise, codec, RedisCommands.ASKING, new Object[]{}));
            list.add(new CommandData<V, R>(attemptPromise, codec, command, params));
            RPromise<Void> main = new RedissonPromise<Void>();
            writeFuture = connection.send(new CommandsData(main, list, false));
        } else {
            if (log.isDebugEnabled()) {
                log.debug("acquired connection for command {} and params {} from slot {} using node {}... {}",
                        command, LogHelper.toString(params), source, connection.getRedisClient().getAddr(), connection);
            }
            writeFuture = connection.send(new CommandData<V, R>(attemptPromise, codec, command, params));
        }
    }
    
    protected void releaseConnection(RPromise<R> attemptPromise, RFuture<RedisConnection> connectionFuture) {
        attemptPromise.onComplete((res, e) -> {
            if (!connectionFuture.isSuccess()) {
                return;
            }

            RedisConnection connection = connectionFuture.getNow();
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
        });
    }

    protected RFuture<RedisConnection> getConnection() {
        RFuture<RedisConnection> connectionFuture;
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

        if (codec.getClassLoader() != codec.getClass().getClassLoader()) {
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
            Map<Codec, Codec> map = CODECS.get(threadClassLoader);
            if (map == null) {
                synchronized (CODECS) {
                    map = CODECS.get(threadClassLoader);
                    if (map == null) {
                        map = new LRUCacheMap<>(200, 0, 0);
                        CODECS.put(threadClassLoader, map);
                    }
                }
            }
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
