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
package org.redisson;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.RRemoteServiceResponse;
import org.redisson.remote.RemoteServiceAck;
import org.redisson.remote.RemoteServiceAckTimeoutException;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RemoteServiceResponse;
import org.redisson.remote.RemoteServiceTimeoutException;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseRemoteService {

    private static final Logger log = LoggerFactory.getLogger(BaseRemoteService.class);

    private final Map<Class<?>, String> requestQueueNameCache = PlatformDependent.newConcurrentHashMap();
    private final ConcurrentMap<Method, List<String>> methodSignaturesCache = PlatformDependent.newConcurrentHashMap();

    protected final Codec codec;
    protected final RedissonClient redisson;
    protected final String name;
    protected final CommandAsyncExecutor commandExecutor;
    protected final String executorId;

    protected final String cancelRequestMapName;
    protected final String cancelResponseMapName;
    protected final String responseQueueName;
    private final ConcurrentMap<String, ResponseEntry> responses;

    public BaseRemoteService(Codec codec, RedissonClient redisson, String name, CommandAsyncExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        this.codec = codec;
        this.redisson = redisson;
        this.name = name;
        this.commandExecutor = commandExecutor;
        this.executorId = executorId;
        this.responses = responses;
        this.cancelRequestMapName = "{" + name + ":remote" + "}:cancel-request";
        this.cancelResponseMapName = "{" + name + ":remote" + "}:cancel-response";
        this.responseQueueName = getResponseQueueName(executorId);
    }

    public String getResponseQueueName(String executorId) {
        return "{remote_response}:" + executorId;
    }
    
    protected String getAckName(RequestId requestId) {
        return "{" + name + ":remote" + "}:" + requestId + ":ack";
    }
    
    protected String getAckName(String requestId) {
        return "{" + name + ":remote" + "}:" + requestId + ":ack";
    }


    public String getRequestQueueName(Class<?> remoteInterface) {
        String str = requestQueueNameCache.get(remoteInterface);
        if (str == null) {
            str = "{" + name + ":" + remoteInterface.getName() + "}";
            requestQueueNameCache.put(remoteInterface, str);
        }
        return str;
    }

    protected ByteBuf encode(Object obj) {
        try {
            return codec.getValueEncoder().encode(obj);
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
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                final RequestId requestId = generateRequestId();

                if (method.getName().equals("toString")) {
                    return getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-" + requestId;
                } else if (method.getName().equals("equals")) {
                    return proxy == args[0];
                } else if (method.getName().equals("hashCode")) {
                    return (getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-" + requestId).hashCode();
                }

                if (!optionsCopy.isResultExpected() && !(method.getReturnType().equals(Void.class)
                        || method.getReturnType().equals(Void.TYPE) || method.getReturnType().equals(RFuture.class))) {
                    throw new IllegalArgumentException("The noResult option only supports void return value");
                }

                final String requestQueueName = getRequestQueueName(syncInterface);

                final Long ackTimeout = optionsCopy.getAckTimeoutInMillis();
                

                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId.toString(), method.getName(), getMethodSignatures(method), args,
                        optionsCopy, System.currentTimeMillis());

                final RFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = pollResponse(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }
                
                final RPromise<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    responseFuture = pollResultResponse(optionsCopy.getExecutionTimeoutInMillis(), requestId, request);
                } else {
                    responseFuture = null;
                }

                final RemotePromise<Object> result = createResultPromise(optionsCopy, requestId, requestQueueName,
                        ackTimeout);
                RFuture<Boolean> addFuture = addAsync(requestQueueName, request, result);
                addFuture.addListener(new FutureListener<Boolean>() {

                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            if (responseFuture != null) {
                                responseFuture.cancel(false);
                            }
                            if (ackFuture != null) {
                                ackFuture.cancel(false);
                            }
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        if (!future.get()) {
                            result.tryFailure(new RedisException("Task hasn't been added"));
                            if (responseFuture != null) {
                                responseFuture.cancel(false);
                            }
                            if (ackFuture != null) {
                                ackFuture.cancel(false);
                            }
                            return;
                        }

                                if (optionsCopy.isAckExpected()) {
                                    ackFuture.addListener(new FutureListener<RemoteServiceAck>() {
                                        @Override
                                        public void operationComplete(Future<RemoteServiceAck> future) throws Exception {
                                            if (!future.isSuccess()) {
                                                if (responseFuture != null) {
                                                    responseFuture.cancel(false);
                                                }

                                                result.tryFailure(future.cause());
                                                return;
                                            }

                                            RemoteServiceAck ack = future.getNow();
                                            if (ack == null) {
                                                final String ackName = getAckName(requestId);
                                                RFuture<RemoteServiceAck> ackFutureAttempt = 
                                                                            tryPollAckAgainAsync(optionsCopy, ackName, requestId);
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
                                                                            + "ms for request: " + requestId);
                                                            result.tryFailure(ex);
                                                            return;
                                                        }

                                                        awaitResultAsync(optionsCopy, result, ackName, responseFuture);
                                                    }
                                                });
                                            } else {
                                                awaitResultAsync(optionsCopy, result, responseFuture);
                                            }
                                        }

                                    });
                                } else {
                                    awaitResultAsync(optionsCopy, result, responseFuture);
                                }
                            }
                        });

                return result;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    private void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final String ackName, final RFuture<RRemoteServiceResponse> responseFuture) {
        RFuture<Boolean> deleteFuture = redisson.getBucket(ackName).deleteAsync();
        deleteFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                awaitResultAsync(optionsCopy, result, responseFuture);
            }
        });
    }
    
    protected void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            RFuture<RRemoteServiceResponse> responseFuture) {
        // poll for the response only if expected
        if (!optionsCopy.isResultExpected()) {
            return;
        }
        
        responseFuture.addListener(new FutureListener<RRemoteServiceResponse>() {
            
            @Override
            public void operationComplete(Future<RRemoteServiceResponse> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow() == null) {
                    RemoteServiceTimeoutException e = new RemoteServiceTimeoutException("No response after "
                            + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + result.getRequestId());
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

    protected <T extends RRemoteServiceResponse> RPromise<T> pollResultResponse(long timeout,
            RequestId requestId, RemoteServiceRequest request) {
        return pollResponse(timeout, requestId, false);
    }
    
    private <T extends RRemoteServiceResponse> RPromise<T> pollResponse(final long timeout,
            final RequestId requestId, boolean insertFirst) {
        final RPromise<T> responseFuture = new RedissonPromise<T>();

        ResponseEntry entry;
        synchronized (responses) {
            entry = responses.get(responseQueueName);
            if (entry == null) {
                entry = new ResponseEntry();
                ResponseEntry oldEntry = responses.putIfAbsent(responseQueueName, entry);
                if (oldEntry != null) {
                    entry = oldEntry;
                }
            }
            
            responseFuture.addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    if (future.isCancelled()) {
                        synchronized (responses) {
                            ResponseEntry entry = responses.get(responseQueueName);
                            List<Result> list = entry.getResponses().get(requestId);
                            if (list == null) {
                                return;
                            }
                            
                            for (Iterator<Result> iterator = list.iterator(); iterator.hasNext();) {
                                Result result = iterator.next();
                                if (result.getPromise() == responseFuture) {
                                    result.getScheduledFuture().cancel(true);
                                    iterator.remove();
                                }
                            }
                            if (list.isEmpty()) {
                                entry.getResponses().remove(requestId);
                            }

                            if (entry.getResponses().isEmpty()) {
                                responses.remove(responseQueueName, entry);
                            }
                        }
                    }
                    
                }
            });
            
            ScheduledFuture<?> future = commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (responses) {
                        ResponseEntry entry = responses.get(responseQueueName);
                        if (entry == null) {
                            return;
                        }
                        
                        RemoteServiceTimeoutException ex = new RemoteServiceTimeoutException("No response after " + timeout + "ms");
                        if (responseFuture.tryFailure(ex)) {
                            List<Result> list = entry.getResponses().get(requestId);
                            list.remove(0);
                            if (list.isEmpty()) {
                                entry.getResponses().remove(requestId);
                            }
                            if (entry.getResponses().isEmpty()) {
                                responses.remove(responseQueueName, entry);
                            }
                        }
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

            final Map<RequestId, List<Result>> entryResponses = entry.getResponses();
            List<Result> list = entryResponses.get(requestId);
            if (list == null) {
                list = new ArrayList<Result>(3);
                entryResponses.put(requestId, list);
            }
            
            Result res = new Result(responseFuture, future);
            if (insertFirst) {
                list.add(0, res);
            } else {
                list.add(res);
            }
        }

        
        pollResponse(entry);
        return responseFuture;
    }

    private void pollResponse(final ResponseEntry entry) {
        if (!entry.getStarted().compareAndSet(false, true)) {
            return;
        }
        
        RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseQueueName, codec);
        RFuture<RRemoteServiceResponse> future = responseQueue.takeAsync();
        future.addListener(new FutureListener<RRemoteServiceResponse>() {
            
            @Override
            public void operationComplete(Future<RRemoteServiceResponse> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't get response from " + responseQueueName, future.cause());
                    return;
                }
                
                RRemoteServiceResponse response = future.getNow();
                RPromise<RRemoteServiceResponse> promise;
                synchronized (responses) {
                    ResponseEntry entry = responses.get(responseQueueName);
                    if (entry == null) {
                        return;
                    }
                    
                    RequestId key = new RequestId(response.getId());
                    List<Result> list = entry.getResponses().get(key);
                    if (list == null) {
                        RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseQueueName, codec);
                        responseQueue.takeAsync().addListener(this);
                        return;
                    }
                    
                    Result res = list.remove(0);
                    if (list.isEmpty()) {
                        entry.getResponses().remove(key);
                    }

                    promise = res.getPromise();
                    res.getScheduledFuture().cancel(true);
                    
                    if (entry.getResponses().isEmpty()) {
                        responses.remove(responseQueueName, entry);
                    } else {
                        RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseQueueName, codec);
                        responseQueue.takeAsync().addListener(this);
                    }
                }

                if (promise != null) {
                    promise.trySuccess(response);
                }
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

                RequestId requestId = generateRequestId();

                String requestQueueName = getRequestQueueName(remoteInterface);
                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId.toString(), method.getName(), getMethodSignatures(method), args, optionsCopy,
                        System.currentTimeMillis());
                
                final RFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = pollResponse(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }
                
                final RPromise<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    responseFuture = pollResultResponse(optionsCopy.getExecutionTimeoutInMillis(), requestId, request);
                } else {
                    responseFuture = null;
                }
                
                RemotePromise<Object> addPromise = new RemotePromise<Object>(requestId);
                RFuture<Boolean> futureAdd = addAsync(requestQueueName, request, addPromise);
                futureAdd.await();
                if (!futureAdd.isSuccess()) {
                    if (responseFuture != null) {
                        responseFuture.cancel(false);
                    }
                    if (ackFuture != null) {
                        ackFuture.cancel(false);
                    }
                    throw futureAdd.cause();
                }
                
                if (!futureAdd.get()) {
                    if (responseFuture != null) {
                        responseFuture.cancel(false);
                    }
                    if (ackFuture != null) {
                        ackFuture.cancel(false);
                    }
                    throw new RedisException("Task hasn't been added");
                }
                
                // poll for the ack only if expected
                if (ackFuture != null) {
                    String ackName = getAckName(requestId);
                    ackFuture.await();
                    RemoteServiceAck ack = ackFuture.getNow();
                    if (ack == null) {
                        RFuture<RemoteServiceAck> ackFutureAttempt = 
                                tryPollAckAgainAsync(optionsCopy, ackName, requestId);
                        ackFutureAttempt.await();
                        ack = ackFutureAttempt.getNow();
                        if (ack == null) {
                            throw new RemoteServiceAckTimeoutException("No ACK response after "
                                    + optionsCopy.getAckTimeoutInMillis() + "ms for request: " + request);
                        }
                    }
                    redisson.getBucket(ackName).delete();
                }

                // poll for the response only if expected
                if (responseFuture != null) {
                    responseFuture.awaitUninterruptibly();
                    RemoteServiceResponse response = (RemoteServiceResponse) responseFuture.getNow();
                    if (response == null) {
                        throw new RemoteServiceTimeoutException("No response after "
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

    private RFuture<RemoteServiceAck> tryPollAckAgainAsync(final RemoteInvocationOptions optionsCopy,
            String ackName, final RequestId requestId) {
        final RPromise<RemoteServiceAck> promise = new RedissonPromise<RemoteServiceAck>();
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
                    RPromise<RemoteServiceAck> ackFuture = pollResponse(commandExecutor.getConnectionManager().getConfig().getTimeout(), requestId, true);
                    ackFuture.addListener(new FutureListener<RemoteServiceAck>() {
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

    protected <T> void scheduleCheck(final String mapName, final RequestId requestId, final RPromise<T> cancelRequest) {
        commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (cancelRequest.isDone()) {
                    return;
                }

                RMap<String, T> canceledRequests = redisson.getMap(mapName, new CompositeCodec(StringCodec.INSTANCE, codec, codec));
                RFuture<T> future = canceledRequests.removeAsync(requestId.toString());
                future.addListener(new FutureListener<T>() {
                    @Override
                    public void operationComplete(Future<T> future) throws Exception {
                        if (cancelRequest.isDone()) {
                            return;
                        }
                        if (!future.isSuccess()) {
                            scheduleCheck(mapName, requestId, cancelRequest);
                            return;
                        }
                        
                        T request = future.getNow();
                        if (request == null) {
                            scheduleCheck(mapName, requestId, cancelRequest);
                        } else {
                            cancelRequest.trySuccess(request);
                        }
                    }
                });
            }
        }, 3000, TimeUnit.MILLISECONDS);
    }

    protected RequestId generateRequestId() {
        byte[] id = new byte[17];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        PlatformDependent.threadLocalRandom().nextBytes(id);
        id[0] = 00;
        return new RequestId(id);
    }

    protected abstract RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request,
            RemotePromise<Object> result);

    protected abstract RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId);

    private void cancelExecution(RemoteInvocationOptions optionsCopy,
            boolean mayInterruptIfRunning, RemotePromise<Object> remotePromise) {
        RMap<String, RemoteServiceCancelRequest> canceledRequests = redisson.getMap(cancelRequestMapName, new CompositeCodec(StringCodec.INSTANCE, codec, codec));
        canceledRequests.fastPutAsync(remotePromise.getRequestId().toString(), new RemoteServiceCancelRequest(mayInterruptIfRunning, false));
        canceledRequests.expireAsync(60, TimeUnit.SECONDS);
        
        // subscribe for async result if it's not expected before
        if (!optionsCopy.isResultExpected()) {
            RemoteInvocationOptions options = new RemoteInvocationOptions(optionsCopy);
            options.expectResultWithin(60, TimeUnit.SECONDS);
            RFuture<RRemoteServiceResponse> responseFuture = pollResponse(options.getExecutionTimeoutInMillis(), remotePromise.getRequestId(), false);
            awaitResultAsync(options, remotePromise, responseFuture);
        }
    }

    protected List<String> getMethodSignatures(Method method) {
        List<String> result = methodSignaturesCache.get(method);
        if (result == null) {
            result = new ArrayList<String>(method.getParameterTypes().length);
            for (Class<?> t : method.getParameterTypes()) {
                result.add(t.getName());
            }
            List<String> oldList = methodSignaturesCache.putIfAbsent(method, result);
            if (oldList != null) {
                result = oldList;
            }
        }
        
        return result;
    }

    private RemotePromise<Object> createResultPromise(final RemoteInvocationOptions optionsCopy,
            final RequestId requestId, final String requestQueueName, final Long ackTimeout) {
        final RemotePromise<Object> result = new RemotePromise<Object>(requestId) {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (isCancelled()) {
                    return true;
                }
                
                if (isDone()) {
                    return false;
                }
                
                
                if (optionsCopy.isAckExpected()) {
                    String ackName = getAckName(requestId);
                    RFuture<Boolean> future = commandExecutor.evalWriteAsync(responseQueueName, LongCodec.INSTANCE,
                            RedisCommands.EVAL_BOOLEAN,
                            "if redis.call('setnx', KEYS[1], 1) == 1 then "
                                + "redis.call('pexpire', KEYS[1], ARGV[1]);"
//                                        + "redis.call('lrem', KEYS[3], 1, ARGV[1]);"
//                                        + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                + "return 1;" 
                            + "end;"
                            + "return 0;",
                            Arrays.<Object> asList(ackName),
//                                    Arrays.<Object> asList(ackName, responseQueueName, requestQueueName), 
                            ackTimeout);
                    
                    boolean ackNotSent = commandExecutor.get(future);
                    if (ackNotSent) {
                        RList<Object> list = redisson.getList(requestQueueName, LongCodec.INSTANCE);
                        list.remove(requestId.toString());
                        super.cancel(mayInterruptIfRunning);
                        return true;
                    }

                    return doCancel(mayInterruptIfRunning);
                }

                boolean removed = commandExecutor.get(removeAsync(requestQueueName, requestId));
                if (removed) {
                    super.cancel(mayInterruptIfRunning);
                    return true;
                }

                return doCancel(mayInterruptIfRunning);
            }

            private boolean doCancel(boolean mayInterruptIfRunning) {
                if (isCancelled()) {
                    return true;
                }
                
                if (isDone()) {
                    return false;
                }

                cancelExecution(optionsCopy, mayInterruptIfRunning, this);

                try {
                    awaitUninterruptibly(60, TimeUnit.SECONDS);
                } catch (CancellationException e) {
                    // skip
                }
                return isCancelled();
            }
        };
        return result;
    }
}
