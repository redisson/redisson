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
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.RPromise;
import org.redisson.remote.RRemoteServiceResponse;
import org.redisson.remote.RemoteServiceAck;
import org.redisson.remote.RemoteServiceAckTimeoutException;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RemoteServiceResponse;
import org.redisson.remote.RemoteServiceTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseRemoteService {

    private static final Logger log = LoggerFactory.getLogger(BaseRemoteService.class);

    protected final Codec codec;
    protected final RedissonClient redisson;
    protected final String name;
    protected final CommandExecutor commandExecutor;

    public BaseRemoteService(RedissonClient redisson, CommandExecutor commandExecutor) {
        this(redisson, "redisson_rs", commandExecutor);
    }

    public BaseRemoteService(RedissonClient redisson, String name, CommandExecutor commandExecutor) {
        this(null, redisson, name, commandExecutor);
    }

    public BaseRemoteService(Codec codec, RedissonClient redisson, CommandExecutor commandExecutor) {
        this(codec, redisson, "redisson_rs", commandExecutor);
    }

    public BaseRemoteService(Codec codec, RedissonClient redisson, String name, CommandExecutor commandExecutor) {
        this.codec = codec;
        this.redisson = redisson;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    protected String getCancelRequestQueueName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId + ":cancel";
    }

    protected String getAckName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId + ":ack";
    }

    protected String getResponseQueueName(Class<?> remoteInterface, String requestId) {
        return "{" + name + ":" + remoteInterface.getName() + "}:" + requestId;
    }

    protected String getRequestQueueName(Class<?> remoteInterface) {
        return "{" + name + ":" + remoteInterface.getName() + "}";
    }

    protected Codec getCodec() {
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

    public <T> T get(Class<T> remoteInterface) {
        return get(remoteInterface, RemoteInvocationOptions.defaults());
    }

    public <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit) {
        return get(remoteInterface,
                RemoteInvocationOptions.defaults().expectResultWithin(executionTimeout, executionTimeUnit));
    }

    public <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit, long ackTimeout,
            TimeUnit ackTimeUnit) {
        return get(remoteInterface, RemoteInvocationOptions.defaults().expectAckWithin(ackTimeout, ackTimeUnit)
                .expectResultWithin(executionTimeout, executionTimeUnit));
    }

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
                    if (!m.getReturnType().getClass().isInstance(RFuture.class)) {
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
                        || method.getReturnType().equals(Void.TYPE) || method.getReturnType().equals(RFuture.class))) {
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
                            RFuture<Boolean> future = commandExecutor.evalWriteAsync(responseName, LongCodec.INSTANCE,
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
                                super.cancel(mayInterruptIfRunning);
                                return true;
                            }

                            return cancel(syncInterface, requestId, request, mayInterruptIfRunning);
                        }

                        boolean removed = remove(requestQueue, request);
                        if (removed) {
                            super.cancel(mayInterruptIfRunning);
                            return true;
                        }

                        return cancel(syncInterface, requestId, request, mayInterruptIfRunning);
                    }

                    private boolean cancel(Class<?> remoteInterface, String requestId, RemoteServiceRequest request,
                            boolean mayInterruptIfRunning) {
                        if (isCancelled()) {
                            return true;
                        }
                        
                        if (isDone()) {
                            return false;
                        }

                        String canceRequestName = getCancelRequestQueueName(remoteInterface, requestId);
                        cancelExecution(optionsCopy, responseName, request, mayInterruptIfRunning, canceRequestName, this);

                        awaitUninterruptibly();
                        return isCancelled();
                    }
                };

                result.setRequestId(requestId);
                
                RFuture<Boolean> addFuture = addAsync(requestQueue, request, result);
                addFuture.addListener(new FutureListener<Boolean>() {

                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        if (optionsCopy.isAckExpected()) {
                            final RBlockingQueue<RemoteServiceAck> responseQueue = redisson.getBlockingQueue(responseName, getCodec());
                            RFuture<RemoteServiceAck> ackFuture = responseQueue.pollAsync(optionsCopy.getAckTimeoutInMillis(), TimeUnit.MILLISECONDS);
                            ackFuture.addListener(new FutureListener<RemoteServiceAck>() {
                                @Override
                                public void operationComplete(Future<RemoteServiceAck> future) throws Exception {
                                    if (!future.isSuccess()) {
                                        result.tryFailure(future.cause());
                                        return;
                                    }

                                    RemoteServiceAck ack = future.getNow();
                                    if (ack == null) {
                                        RFuture<RemoteServiceAck> ackFutureAttempt = 
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

                                                awaitResultAsync(optionsCopy, result, request, responseName, ackName);
                                            }
                                        });
                                    } else {
                                        awaitResultAsync(optionsCopy, result, request, responseName);
                                    }
                                }

                            });
                        } else {
                            awaitResultAsync(optionsCopy, result, request, responseName);
                        }
                    }
                });

                return result;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    private void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, final String responseName, final String ackName) {
        RFuture<Boolean> deleteFuture = redisson.getBucket(ackName).deleteAsync();
        deleteFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                awaitResultAsync(optionsCopy, result, request, responseName);
            }
        });
    }

    protected void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, final String responseName) {
        // poll for the response only if expected
        if (!optionsCopy.isResultExpected()) {
            return;
        }
        
        RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseName, getCodec());
        RFuture<RRemoteServiceResponse> responseFuture = responseQueue
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
        RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
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

    private RFuture<RemoteServiceAck> tryPollAckAgainAsync(RemoteInvocationOptions optionsCopy,
            final RBlockingQueue<RemoteServiceAck> responseQueue, String ackName)
            throws InterruptedException {
        final RPromise<RemoteServiceAck> promise = commandExecutor.getConnectionManager().newPromise();
        RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
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
                    promise.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    RFuture<RemoteServiceAck> pollFuture = responseQueue.pollAsync();
                    pollFuture.addListener(new FutureListener<RemoteServiceAck>() {
                        @Override
                        public void operationComplete(Future<RemoteServiceAck> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }

                            promise.trySuccess(future.getNow());
                        }
                    });
                } else {
                    promise.trySuccess(null);
                }
            }
        });
        return promise;
    }

    protected String generateRequestId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    protected RFuture<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request,
            RemotePromise<Object> result) {
        RFuture<Boolean> future = requestQueue.addAsync(request);
        result.setAddFuture(future);
        return future;
    }

    protected boolean remove(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request) {
        return requestQueue.remove(request);
    }

    private void cancelExecution(RemoteInvocationOptions optionsCopy, String responseName,
            RemoteServiceRequest request, boolean mayInterruptIfRunning, String canceRequestName, RemotePromise<Object> remotePromise) {
        RBlockingQueue<RemoteServiceCancelRequest> cancelRequestQueue = redisson.getBlockingQueue(canceRequestName, getCodec());
        cancelRequestQueue.putAsync(new RemoteServiceCancelRequest(mayInterruptIfRunning));
        cancelRequestQueue.expireAsync(60, TimeUnit.SECONDS);
        
        // subscribe for async result if it's not expected before
        if (!optionsCopy.isResultExpected()) {
            RemoteInvocationOptions options = new RemoteInvocationOptions(optionsCopy);
            options.expectResultWithin(60, TimeUnit.SECONDS);
            awaitResultAsync(options, remotePromise, request, responseName);
        }
    }

}
