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
package org.redisson;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RBatch;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RRemoteService;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.remote.RRemoteAsync;
import org.redisson.remote.RRemoteServiceResponse;
import org.redisson.remote.RemoteServiceAck;
import org.redisson.remote.RemoteServiceAckTimeoutException;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceKey;
import org.redisson.remote.RemoteServiceMethod;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RemoteServiceResponse;
import org.redisson.remote.RemoteServiceTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRemoteService implements RRemoteService {

    private static final Logger log = LoggerFactory.getLogger(RedissonRemoteService.class);

    private final Map<RemoteServiceKey, RemoteServiceMethod> beans = PlatformDependent.newConcurrentHashMap();

    protected final Codec codec;
    protected final Redisson redisson;
    protected final String name;
    protected final CommandExecutor commandExecutor;

    public RedissonRemoteService(Redisson redisson, CommandExecutor commandExecutor) {
        this(redisson, "redisson_rs", commandExecutor);
    }

    public RedissonRemoteService(Redisson redisson, String name, CommandExecutor commandExecutor) {
        this(null, redisson, name, commandExecutor);
    }

    public RedissonRemoteService(Codec codec, Redisson redisson, CommandExecutor commandExecutor) {
        this(codec, redisson, "redisson_rs", commandExecutor);
    }

    public RedissonRemoteService(Codec codec, Redisson redisson, String name, CommandExecutor commandExecutor) {
        this.codec = codec;
        this.redisson = redisson;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public <T> void register(Class<T> remoteInterface, T object) {
        register(remoteInterface, object, 1);
    }

    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workersAmount) {
        register(remoteInterface, object, workersAmount, null);
    }

    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workersAmount, ExecutorService executor) {
        if (workersAmount < 1) {
            throw new IllegalArgumentException("executorsAmount can't be lower than 1");
        }
        for (Method method : remoteInterface.getMethods()) {
            RemoteServiceMethod value = new RemoteServiceMethod(method, object);
            RemoteServiceKey key = new RemoteServiceKey(remoteInterface, method.getName());
            if (beans.put(key, value) != null) {
                return;
            }
        }

        for (int i = 0; i < workersAmount; i++) {
            String requestQueueName = getRequestQueueName(remoteInterface);
            RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName, getCodec());
            subscribe(remoteInterface, requestQueue, executor);
        }
    }

    private String getCancelRequestQueueName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId + ":cancel";
    }

    private String getAckName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId + ":ack";
    }

    private String getResponseQueueName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId;
    }

    private String getRequestQueueName(Class<?> remoteInterface) {
        return "{" + name + ":" + remoteInterface.getName() + "}";
    }

    private Codec getCodec() {
        if (codec != null) {
            return codec;
        }
        return redisson.getConfig().getCodec();
    }

    protected byte[] encode(Object obj) {
        try {
            return getCodec().getValueEncoder().encode(obj);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private <T> void subscribe(final Class<T> remoteInterface, final RBlockingQueue<RemoteServiceRequest> requestQueue,
            final ExecutorService executor) {
        Future<RemoteServiceRequest> take = requestQueue.takeAsync();
        take.addListener(new FutureListener<RemoteServiceRequest>() {
            @Override
            public void operationComplete(Future<RemoteServiceRequest> future) throws Exception {
                if (!future.isSuccess()) {
                    if (future.cause() instanceof RedissonShutdownException) {
                        return;
                    }
                    // re-subscribe after a failed takeAsync
                    subscribe(remoteInterface, requestQueue, executor);
                    return;
                }

                // do not subscribe now, see
                // https://github.com/mrniko/redisson/issues/493
                // subscribe(remoteInterface, requestQueue);

                final RemoteServiceRequest request = future.getNow();
                // check the ack only if expected
                if (request.getOptions().isAckExpected() && System.currentTimeMillis() - request.getDate() > request
                        .getOptions().getAckTimeoutInMillis()) {
                    log.debug("request: {} has been skipped due to ackTimeout");
                    // re-subscribe after a skipped ackTimeout
                    subscribe(remoteInterface, requestQueue, executor);
                    return;
                }

                final RemoteServiceMethod method = beans
                        .get(new RemoteServiceKey(remoteInterface, request.getMethodName()));
                final String responseName = getResponseQueueName(remoteInterface, request.getRequestId());

                // send the ack only if expected
                if (request.getOptions().isAckExpected()) {
                    String ackName = getAckName(remoteInterface, request.getRequestId());
                    Future<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(responseName,
                            LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                                "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                                    + "redis.call('pexpire', KEYS[1], ARGV[2]);"
                                    + "redis.call('rpush', KEYS[2], ARGV[1]);"
                                    + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                    + "return 1;" 
                                + "end;" 
                                + "return 0;",
                            Arrays.<Object> asList(ackName, responseName),
                            encode(new RemoteServiceAck()), request.getOptions().getAckTimeoutInMillis());

                    ackClientsFuture.addListener(new FutureListener<Boolean>() {
                        @Override
                        public void operationComplete(Future<Boolean> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error("Can't send ack for request: " + request, future.cause());
                                if (future.cause() instanceof RedissonShutdownException) {
                                    return;
                                }
                                // re-subscribe after a failed send (ack)
                                subscribe(remoteInterface, requestQueue, executor);
                                return;
                            }

                            if (!future.getNow()) {
                                subscribe(remoteInterface, requestQueue, executor);
                                return;
                            }

                            runMethod(remoteInterface, requestQueue, executor, request, method, responseName);
                        }
                    });
                } else {
                    runMethod(remoteInterface, requestQueue, executor, request, method, responseName);
                }
            }

        });
    }

    private <T> void runMethod(final Class<T> remoteInterface, final RBlockingQueue<RemoteServiceRequest> requestQueue,
            final ExecutorService executor, final RemoteServiceRequest request, final RemoteServiceMethod method,
            final String responseName) {
        
        if (executor != null) {
            RBlockingQueue<RemoteServiceCancelRequest> cancelRequestQueue = 
                    redisson.getBlockingQueue(getCancelRequestQueueName(remoteInterface, request.getRequestId()), getCodec());
            final Future<RemoteServiceCancelRequest> cancelRequestFuture = cancelRequestQueue.takeAsync();

            final AtomicReference<RRemoteServiceResponse> responseHolder = new AtomicReference<RRemoteServiceResponse>();
            
            final java.util.concurrent.Future<?> submitFuture = executor.submit(new Runnable() {
                @Override
                public void run() {
                    invokeMethod(remoteInterface, requestQueue, request, method, responseName, executor,
                            cancelRequestFuture, responseHolder);
                }
            });
            
            cancelRequestFuture.addListener(new FutureListener<RemoteServiceCancelRequest>() {
                @Override
                public void operationComplete(Future<RemoteServiceCancelRequest> future) throws Exception {
                    if (!future.isSuccess()) {
                        return;
                    }

                    boolean res = submitFuture.cancel(future.getNow().isMayInterruptIfRunning());
                    if (res) {
                        responseHolder.compareAndSet(null, new RemoteServiceCancelResponse());
                    }
                }
            });
        } else {
            final AtomicReference<RRemoteServiceResponse> responseHolder = new AtomicReference<RRemoteServiceResponse>();
            invokeMethod(remoteInterface, requestQueue, request, method, responseName, executor, null, responseHolder);
        }
    }

    private <T> void invokeMethod(final Class<T> remoteInterface,
            final RBlockingQueue<RemoteServiceRequest> requestQueue, final RemoteServiceRequest request,
            RemoteServiceMethod method, String responseName, final ExecutorService executor,
            Future<RemoteServiceCancelRequest> cancelRequestFuture, AtomicReference<RRemoteServiceResponse> responseHolder) {
        try {
            Object result = method.getMethod().invoke(method.getBean(), request.getArgs());

            RemoteServiceResponse response = new RemoteServiceResponse(result);
            responseHolder.compareAndSet(null, response);
        } catch (Exception e) {
            RemoteServiceResponse response = new RemoteServiceResponse(e.getCause());
            responseHolder.compareAndSet(null, response);
            log.error("Can't execute: " + request, e);
        }

        if (cancelRequestFuture != null) {
            cancelRequestFuture.cancel(false);
        }

        // send the response only if expected
        if (request.getOptions().isResultExpected()) {
            Future<List<?>> clientsFuture = send(request.getOptions().getExecutionTimeoutInMillis(), responseName,
                    responseHolder.get());
            clientsFuture.addListener(new FutureListener<List<?>>() {
                @Override
                public void operationComplete(Future<List<?>> future) throws Exception {
                    if (!future.isSuccess()) {
                        log.error("Can't send response: " + responseHolder.get() + " for request: " + request,
                                future.cause());
                        if (future.cause() instanceof RedissonShutdownException) {
                            return;
                        }
                    }
                    // re-subscribe anyways (fail or success) after the send
                    // (response)
                    subscribe(remoteInterface, requestQueue, executor);
                }
            });
        } else {
            // re-subscribe anyways after the method invocation
            subscribe(remoteInterface, requestQueue, executor);
        }
    }

    @Override
    public <T> T get(Class<T> remoteInterface) {
        return get(remoteInterface, RemoteInvocationOptions.defaults());
    }

    @Override
    public <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit) {
        return get(remoteInterface,
                RemoteInvocationOptions.defaults().expectResultWithin(executionTimeout, executionTimeUnit));
    }

    @Override
    public <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit, long ackTimeout,
            TimeUnit ackTimeUnit) {
        return get(remoteInterface, RemoteInvocationOptions.defaults().expectAckWithin(ackTimeout, ackTimeUnit)
                .expectResultWithin(executionTimeout, executionTimeUnit));
    }

    @Override
    public <T> T get(Class<T> remoteInterface, RemoteInvocationOptions options) {
        for (Annotation annotation : remoteInterface.getAnnotations()) {
            if (annotation.annotationType() == RRemoteAsync.class) {
                Class<T> syncInterface = (Class<T>) ((RRemoteAsync) annotation).value();
                for (Method m : remoteInterface.getMethods()) {
                    try {
                        syncInterface.getMethod(m.getName(), m.getParameterTypes());
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException("Method '" + m.getName() + "' with params '"
                                + Arrays.toString(m.getParameterTypes()) + "' isn't defined in " + syncInterface);
                    } catch (SecurityException e) {
                        throw new IllegalArgumentException(e);
                    }
                    if (!m.getReturnType().getClass().isInstance(Future.class)) {
                        throw new IllegalArgumentException(
                                m.getReturnType().getClass() + " isn't allowed as return type");
                    }
                }
                return async(remoteInterface, options, syncInterface);
            }
        }

        return sync(remoteInterface, options);
    }

    private <T> T async(final Class<T> remoteInterface, final RemoteInvocationOptions options,
            final Class<?> syncInterface) {
        // local copy of the options, to prevent mutation
        final RemoteInvocationOptions optionsCopy = new RemoteInvocationOptions(options);
        final String toString = getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-"
                + generateRequestId();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("toString")) {
                    return toString;
                } else if (method.getName().equals("equals")) {
                    return proxy == args[0];
                } else if (method.getName().equals("hashCode")) {
                    return toString.hashCode();
                }

                if (!optionsCopy.isResultExpected() && !(method.getReturnType().equals(Void.class)
                        || method.getReturnType().equals(Void.TYPE) || method.getReturnType().equals(Future.class))) {
                    throw new IllegalArgumentException("The noResult option only supports void return value");
                }

                final String requestId = generateRequestId();

                final String requestQueueName = getRequestQueueName(syncInterface);
                final String responseName = getResponseQueueName(syncInterface, requestId);
                final String ackName = getAckName(syncInterface, requestId);

                final RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName,
                        getCodec());
                final RemoteServiceRequest request = new RemoteServiceRequest(requestId, method.getName(), args,
                        optionsCopy, System.currentTimeMillis());

                final RemotePromise<Object> result = new RemotePromise<Object>(commandExecutor.getConnectionManager().newPromise()) {

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        if (isCancelled()) {
                            return true;
                        }
                        
                        if (isDone()) {
                            return false;
                        }
                        
                        if (optionsCopy.isAckExpected()) {
                            Future<Boolean> future = commandExecutor.evalWriteAsync(responseName, LongCodec.INSTANCE,
                                    RedisCommands.EVAL_BOOLEAN,
                                    "if redis.call('setnx', KEYS[1], 1) == 1 then "
                                        + "redis.call('pexpire', KEYS[1], ARGV[2]);"
                                        + "redis.call('lrem', KEYS[3], 1, ARGV[1]);"
                                        + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                        + "return 1;" 
                                    + "end;"
                                    + "return 0;",
                                    Arrays.<Object> asList(ackName, responseName, requestQueueName), 
                                    encode(request), request.getOptions().getAckTimeoutInMillis());

                            boolean ackNotSent = commandExecutor.get(future);
                            if (ackNotSent) {
                                return true;
                            }

                            return cancel(syncInterface, requestId, request, mayInterruptIfRunning);
                        }

                        boolean removed = requestQueue.remove(request);
                        if (removed) {
                            return true;
                        }

                        return cancel(syncInterface, requestId, request, mayInterruptIfRunning);
                    }

                    private boolean cancel(Class<?> remoteInterface, String requestId, RemoteServiceRequest request,
                            boolean mayInterruptIfRunning) {
                        RBlockingQueueAsync<RemoteServiceCancelRequest> cancelRequestQueue = redisson.getBlockingQueue(getCancelRequestQueueName(remoteInterface, requestId), getCodec());
                        cancelRequestQueue.putAsync(new RemoteServiceCancelRequest(mayInterruptIfRunning));
                        cancelRequestQueue.expireAsync(60, TimeUnit.SECONDS);

                        awaitUninterruptibly();
                        return isCancelled();
                    }
                };

                Future<Boolean> addFuture = addAsync(requestQueue, request, result);
                addFuture.addListener(new FutureListener<Boolean>() {

                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        if (optionsCopy.isAckExpected()) {
                            RBlockingQueue<RemoteServiceAck> responseQueue = redisson.getBlockingQueue(responseName, getCodec());
                            Future<RemoteServiceAck> ackFuture = responseQueue.pollAsync(optionsCopy.getAckTimeoutInMillis(), TimeUnit.MILLISECONDS);
                            ackFuture.addListener(new FutureListener<RemoteServiceAck>() {
                                @Override
                                public void operationComplete(Future<RemoteServiceAck> future) throws Exception {
                                    if (!future.isSuccess()) {
                                        result.tryFailure(future.cause());
                                        return;
                                    }

                                    RemoteServiceAck ack = future.getNow();
                                    if (ack == null) {
                                        Future<RemoteServiceAck> ackFutureAttempt = 
                                                                    tryPollAckAgainAsync(optionsCopy, responseQueue, ackName);
                                        ackFutureAttempt.addListener(new FutureListener<RemoteServiceAck>() {

                                            @Override
                                            public void operationComplete(Future<RemoteServiceAck> future)
                                                    throws Exception {
                                                if (!future.isSuccess()) {
                                                    result.tryFailure(future.cause());
                                                    return;
                                                }

                                                if (future.getNow() == null) {
                                                    Exception ex = new RemoteServiceAckTimeoutException(
                                                            "No ACK response after "
                                                                    + optionsCopy.getAckTimeoutInMillis()
                                                                    + "ms for request: " + request);
                                                    result.tryFailure(ex);
                                                    return;
                                                }

                                                invokeAsync(optionsCopy, result, request, responseName, ackName);
                                            }
                                        });
                                    } else {
                                        invokeAsync(optionsCopy, result, request, responseName);
                                    }
                                }

                            });
                        } else {
                            invokeAsync(optionsCopy, result, request, responseName);
                        }
                    }
                });

                return result;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    private void invokeAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, final String responseName, final String ackName) {
        Future<Boolean> deleteFuture = redisson.getBucket(ackName).deleteAsync();
        deleteFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                invokeAsync(optionsCopy, result, request, responseName);
            }
        });
    }

    private void invokeAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, final String responseName) {
        // poll for the response only if expected
        if (optionsCopy.isResultExpected()) {
            RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseName, getCodec());
            Future<RRemoteServiceResponse> responseFuture = responseQueue
                                                                .pollAsync(optionsCopy.getExecutionTimeoutInMillis(), TimeUnit.MILLISECONDS);
            responseFuture.addListener(new FutureListener<RRemoteServiceResponse>() {

                @Override
                public void operationComplete(Future<RRemoteServiceResponse> future) throws Exception {
                    if (!future.isSuccess()) {
                        result.tryFailure(future.cause());
                        return;
                    }

                    if (future.getNow() == null) {
                        RemoteServiceTimeoutException e = new RemoteServiceTimeoutException("No response after "
                                + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + request);
                        result.tryFailure(e);
                        return;
                    }

                    if (future.getNow() instanceof RemoteServiceCancelResponse) {
                        result.doCancel();
                        return;
                    }
                    
                    RemoteServiceResponse response = (RemoteServiceResponse) future.getNow();
                    if (response.getError() != null) {
                        result.tryFailure(response.getError());
                        return;
                    }

                    result.trySuccess(response.getResult());
                }
            });
        }
    }

    private <T> T sync(final Class<T> remoteInterface, final RemoteInvocationOptions options) {
        // local copy of the options, to prevent mutation
        final RemoteInvocationOptions optionsCopy = new RemoteInvocationOptions(options);
        final String toString = getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-"
                + generateRequestId();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("toString")) {
                    return toString;
                } else if (method.getName().equals("equals")) {
                    return proxy == args[0];
                } else if (method.getName().equals("hashCode")) {
                    return toString.hashCode();
                }

                if (!optionsCopy.isResultExpected()
                        && !(method.getReturnType().equals(Void.class) || method.getReturnType().equals(Void.TYPE)))
                    throw new IllegalArgumentException("The noResult option only supports void return value");

                String requestId = generateRequestId();

                String requestQueueName = getRequestQueueName(remoteInterface);
                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName,
                        getCodec());
                RemoteServiceRequest request = new RemoteServiceRequest(requestId, method.getName(), args, optionsCopy,
                        System.currentTimeMillis());
                requestQueue.add(request);

                RBlockingQueue<RRemoteServiceResponse> responseQueue = null;
                if (optionsCopy.isAckExpected() || optionsCopy.isResultExpected()) {
                    String responseName = getResponseQueueName(remoteInterface, requestId);
                    responseQueue = redisson.getBlockingQueue(responseName, getCodec());
                }

                // poll for the ack only if expected
                if (optionsCopy.isAckExpected()) {
                    String ackName = getAckName(remoteInterface, requestId);
                    RemoteServiceAck ack = (RemoteServiceAck) responseQueue.poll(optionsCopy.getAckTimeoutInMillis(),
                            TimeUnit.MILLISECONDS);
                    if (ack == null) {
                        ack = tryPollAckAgain(optionsCopy, responseQueue, ackName);
                        if (ack == null) {
                            throw new RemoteServiceAckTimeoutException("No ACK response after "
                                    + optionsCopy.getAckTimeoutInMillis() + "ms for request: " + request);
                        }
                    }
                    redisson.getBucket(ackName).delete();
                }

                // poll for the response only if expected
                if (optionsCopy.isResultExpected()) {
                    RemoteServiceResponse response = (RemoteServiceResponse) responseQueue
                            .poll(optionsCopy.getExecutionTimeoutInMillis(), TimeUnit.MILLISECONDS);
                    if (response == null) {
                        throw new RemoteServiceTimeoutException("No response1 after "
                                + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + request);
                    }
                    if (response.getError() != null) {
                        throw response.getError();
                    }
                    return response.getResult();
                }

                return null;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    private RemoteServiceAck tryPollAckAgain(RemoteInvocationOptions optionsCopy,
            RBlockingQueue<? extends RRemoteServiceResponse> responseQueue, String ackName)
            throws InterruptedException {
        Future<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                        + "redis.call('pexpire', KEYS[1], ARGV[1]);"
                        + "return 0;" 
                    + "end;" 
                    + "redis.call('del', KEYS[1]);" 
                    + "return 1;",
                Arrays.<Object> asList(ackName), optionsCopy.getAckTimeoutInMillis());

        ackClientsFuture.sync();
        if (ackClientsFuture.getNow()) {
            return (RemoteServiceAck) responseQueue.poll();
        }
        return null;
    }

    private Future<RemoteServiceAck> tryPollAckAgainAsync(RemoteInvocationOptions optionsCopy,
            final RBlockingQueue<RemoteServiceAck> responseQueue, String ackName)
            throws InterruptedException {
        final Promise<RemoteServiceAck> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        Future<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                        + "redis.call('pexpire', KEYS[1], ARGV[1]);"
                        + "return 0;" 
                    + "end;" 
                    + "redis.call('del', KEYS[1]);" 
                    + "return 1;",
                Arrays.<Object> asList(ackName), optionsCopy.getAckTimeoutInMillis());
        ackClientsFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    Future<RemoteServiceAck> pollFuture = responseQueue.pollAsync();
                    pollFuture.addListener(new FutureListener<RemoteServiceAck>() {
                        @Override
                        public void operationComplete(Future<RemoteServiceAck> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.setFailure(future.cause());
                                return;
                            }

                            promise.setSuccess(future.getNow());
                        }
                    });
                } else {
                    promise.setSuccess(null);
                }
            }
        });
        return promise;
    }

    private String generateRequestId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    private <T extends RRemoteServiceResponse> Future<List<?>> send(long timeout, String responseName, T response) {
        RBatch batch = redisson.createBatch();
        RBlockingQueueAsync<T> queue = batch.getBlockingQueue(responseName, getCodec());
        queue.putAsync(response);
        queue.expireAsync(timeout, TimeUnit.MILLISECONDS);
        return batch.executeAsync();
    }

    protected Future<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request,
            RemotePromise<Object> result) {
        Future<Boolean> future = requestQueue.addAsync(request);
        result.setAddFuture(future);
        return future;
    }

}
