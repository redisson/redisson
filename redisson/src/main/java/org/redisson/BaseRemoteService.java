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
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
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
import org.redisson.remote.ResponseEntry;
import org.redisson.remote.ResponseEntry.Key;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseRemoteService {

    private static final Logger log = LoggerFactory.getLogger(BaseRemoteService.class);

    private final Map<Class<?>, String> requestQueueNameCache = PlatformDependent.newConcurrentHashMap();
    private final Map<Method, List<String>> methodSignaturesCache = PlatformDependent.newConcurrentHashMap();

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

    protected String getResponseQueueName(String executorId) {
        return "{remote_response}:" + executorId;
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
                final String requestId = generateRequestId();

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

                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName, codec);
                final RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId, method.getName(), getMethodSignatures(method), args,
                        optionsCopy, System.currentTimeMillis());

                final RFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = poll(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }
                
                final RPromise<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    responseFuture = poll(optionsCopy.getExecutionTimeoutInMillis(), requestId, false);
                } else {
                    responseFuture = null;
                }
                
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
                                        + "redis.call('pexpire', KEYS[1], ARGV[2]);"
                                        + "redis.call('lrem', KEYS[3], 1, ARGV[1]);"
//                                        + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                        + "return 1;" 
                                    + "end;"
                                    + "return 0;",
                                    Arrays.<Object> asList(ackName, responseQueueName, requestQueueName), 
                                    encode(request), request.getOptions().getAckTimeoutInMillis());

                            boolean ackNotSent = commandExecutor.get(future);
                            if (ackNotSent) {
                                super.cancel(mayInterruptIfRunning);
                                return true;
                            }

                            return cancel(requestId, request, mayInterruptIfRunning);
                        }

                        RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName, codec);
                        boolean removed = commandExecutor.get(removeAsync(requestQueue, request));
                        if (removed) {
                            super.cancel(mayInterruptIfRunning);
                            return true;
                        }

                        return cancel(requestId, request, mayInterruptIfRunning);
                    }

                    private boolean cancel(String requestId, RemoteServiceRequest request,
                            boolean mayInterruptIfRunning) {
                        if (isCancelled()) {
                            return true;
                        }
                        
                        if (isDone()) {
                            return false;
                        }

                        cancelExecution(optionsCopy, request, mayInterruptIfRunning, this, responseFuture);

                        try {
                            awaitUninterruptibly(60, TimeUnit.SECONDS);
                        } catch (CancellationException e) {
                            // skip
                        }
                        return isCancelled();
                    }
                };

                RFuture<Boolean> addFuture = addAsync(requestQueue, request, result);
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
                                                                            tryPollAckAgainAsync(optionsCopy, ackName, request.getId());
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

                                                        awaitResultAsync(optionsCopy, result, request, ackName, responseFuture);
                                                    }
                                                });
                                            } else {
                                                awaitResultAsync(optionsCopy, result, request, responseFuture);
                                            }
                                        }

                                    });
                                } else {
                                    awaitResultAsync(optionsCopy, result, request, responseFuture);
                                }
                            }
                        });

                return result;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    private void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, final String ackName, final RFuture<RRemoteServiceResponse> responseFuture) {
        RFuture<Boolean> deleteFuture = redisson.getBucket(ackName).deleteAsync();
        deleteFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                awaitResultAsync(optionsCopy, result, request, responseFuture);
            }
        });
    }
    
    protected void awaitResultAsync(final RemoteInvocationOptions optionsCopy, final RemotePromise<Object> result,
            final RemoteServiceRequest request, RFuture<RRemoteServiceResponse> responseFuture) {
        // poll for the response only if expected
        if (!optionsCopy.isResultExpected()) {
            return;
        }
        
        final String requestId = request.getId();
        responseFuture.addListener(new FutureListener<RRemoteServiceResponse>() {
            
            @Override
            public void operationComplete(Future<RRemoteServiceResponse> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow() == null) {
                    RemoteServiceTimeoutException e = new RemoteServiceTimeoutException("No response after "
                            + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + requestId);
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

    private <T extends RRemoteServiceResponse> RPromise<T> poll(final long timeout,
            String requestId, boolean insertFirst) {
        final Key key = new Key(requestId);
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
                            List<Result> list = entry.getResponses().get(key);
                            for (Iterator<Result> iterator = list.iterator(); iterator.hasNext();) {
                                Result result = iterator.next();
                                if (result.getPromise() == responseFuture) {
                                    result.getScheduledFuture().cancel(true);
                                    iterator.remove();
                                }
                            }
                            if (list.isEmpty()) {
                                entry.getResponses().remove(key);
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
                            List<Result> list = entry.getResponses().get(key);
                            list.remove(0);
                            if (list.isEmpty()) {
                                responses.remove(responseQueueName, entry);
                            }
                        }
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

            final Map<Key, List<Result>> entryResponses = entry.getResponses();
            List<Result> list = entryResponses.get(key);
            if (list == null) {
                list = new ArrayList<Result>();
                entryResponses.put(key, list);
            }
            
            Result res = new Result(responseFuture, future);
            if (insertFirst) {
                list.add(0, res);
            } else {
                list.add(res);
            }
        }

        
        pollTasks(entry);
        return responseFuture;
    }

    private void pollTasks(final ResponseEntry entry) {
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
                        log.error("Can't find entry " + responseQueueName);
                        return;
                    }
                    
                    Key key = new Key(response.getId());
                    List<Result> list = entry.getResponses().get(key);
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

                String requestId = generateRequestId();

                String requestQueueName = getRequestQueueName(remoteInterface);
                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName, codec);
                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId, method.getName(), getMethodSignatures(method), args, optionsCopy,
                        System.currentTimeMillis());
                requestQueue.add(request);

                RBlockingQueue<RRemoteServiceResponse> responseQueue = null;
                if (optionsCopy.isAckExpected() || optionsCopy.isResultExpected()) {
                    responseQueue = redisson.getBlockingQueue(responseQueueName, codec);
                }

                // poll for the ack only if expected
                if (optionsCopy.isAckExpected()) {
                    String ackName = getAckName(requestId);
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

    private RFuture<RemoteServiceAck> tryPollAckAgainAsync(final RemoteInvocationOptions optionsCopy,
            String ackName, final String requestId) {
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
                    RPromise<RemoteServiceAck> ackFuture = poll(commandExecutor.getConnectionManager().getConfig().getTimeout(), requestId, true);
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

    protected <T> void scheduleCheck(final String mapName, final String requestId, final RPromise<T> cancelRequest) {
        commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (cancelRequest.isDone()) {
                    return;
                }

                RMap<String, T> canceledRequests = redisson.getMap(mapName, codec);
                RFuture<T> future = canceledRequests.getAsync(requestId);
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

    protected RFuture<Boolean> removeAsync(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request) {
        return requestQueue.removeAsync(request);
    }

    private void cancelExecution(RemoteInvocationOptions optionsCopy,
            RemoteServiceRequest request, boolean mayInterruptIfRunning, RemotePromise<Object> remotePromise, RFuture<RRemoteServiceResponse> responseFuture) {
        RMap<String, RemoteServiceCancelRequest> canceledRequests = redisson.getMap(cancelRequestMapName, codec);
        canceledRequests.putAsync(request.getId(), new RemoteServiceCancelRequest(mayInterruptIfRunning, false));
        canceledRequests.expireAsync(60, TimeUnit.SECONDS);
        
        // subscribe for async result if it's not expected before
        if (!optionsCopy.isResultExpected()) {
            RemoteInvocationOptions options = new RemoteInvocationOptions(optionsCopy);
            options.expectResultWithin(60, TimeUnit.SECONDS);
            awaitResultAsync(options, remotePromise, request, responseFuture);
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
}
