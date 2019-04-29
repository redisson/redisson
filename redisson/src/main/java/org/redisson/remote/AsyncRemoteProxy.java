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
package org.redisson.remote;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.RedissonBucket;
import org.redisson.RedissonList;
import org.redisson.RedissonMap;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AsyncRemoteProxy extends BaseRemoteProxy {

    protected final String cancelRequestMapName;
    
    public AsyncRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
            ConcurrentMap<String, ResponseEntry> responses, Codec codec, String executorId, String cancelRequestMapName, BaseRemoteService remoteService) {
        super(commandExecutor, name, responseQueueName, responses, codec, executorId, remoteService);
        this.cancelRequestMapName = cancelRequestMapName;
    }
    
    protected List<Class<?>> permittedClasses() {
        return Arrays.asList(RFuture.class);
    }
    
    public <T> T create(Class<T> remoteInterface, RemoteInvocationOptions options,
            Class<?> syncInterface) {
        for (Method m : remoteInterface.getMethods()) {
            try {
                syncInterface.getMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Method '" + m.getName() + "' with params '"
                        + Arrays.toString(m.getParameterTypes()) + "' isn't defined in " + syncInterface);
            } catch (SecurityException e) {
                throw new IllegalArgumentException(e);
            }
            
            boolean permitted = false;
            for (Class<?> clazz : permittedClasses()) {
                if (clazz.isAssignableFrom(m.getReturnType())) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                throw new IllegalArgumentException(
                        m.getReturnType().getClass() + " isn't allowed as return type");
            }
        }
        
        // local copy of the options, to prevent mutation
        RemoteInvocationOptions optionsCopy = new RemoteInvocationOptions(options);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                RequestId requestId = remoteService.generateRequestId();

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

                String requestQueueName = getRequestQueueName(syncInterface);

                Long ackTimeout = optionsCopy.getAckTimeoutInMillis();
                

                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId.toString(), method.getName(), 
                                                    remoteService.getMethodSignature(method), args, optionsCopy, System.currentTimeMillis());

                final RFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = pollResponse(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }
                
                final RPromise<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    long timeout = remoteService.getTimeout(optionsCopy.getExecutionTimeoutInMillis(), request);
                    responseFuture = pollResponse(timeout, requestId, false);
                } else {
                    responseFuture = null;
                }

                RemotePromise<Object> result = createResultPromise(optionsCopy, requestId, requestQueueName,
                        ackTimeout);
                RFuture<Boolean> addFuture = remoteService.addAsync(requestQueueName, request, result);
                addFuture.onComplete((res, e) -> {
                        if (e != null) {
                            if (responseFuture != null) {
                                responseFuture.cancel(false);
                            }
                            if (ackFuture != null) {
                                ackFuture.cancel(false);
                            }
                            result.tryFailure(e);
                            return;
                        }
                        
                        if (!res) {
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
                                    ackFuture.onComplete((ack, ex) -> {
                                        if (ex != null) {
                                            if (responseFuture != null) {
                                                responseFuture.cancel(false);
                                            }

                                            result.tryFailure(ex);
                                            return;
                                        }

                                        if (ack == null) {
                                            String ackName = remoteService.getAckName(requestId);
                                            RFuture<RemoteServiceAck> ackFutureAttempt = 
                                                                        tryPollAckAgainAsync(optionsCopy, ackName, requestId);
                                            ackFutureAttempt.onComplete((re, ex2) -> {
                                                if (ex2 != null) {
                                                    result.tryFailure(ex2);
                                                    return;
                                                }

                                                if (re == null) {
                                                    Exception exc = new RemoteServiceAckTimeoutException(
                                                            "No ACK response after "
                                                                    + optionsCopy.getAckTimeoutInMillis()
                                                                    + "ms for request: " + requestId);
                                                    result.tryFailure(exc);
                                                    return;
                                                }

                                                awaitResultAsync(optionsCopy, result, ackName, responseFuture);
                                            });
                                        } else {
                                            awaitResultAsync(optionsCopy, result, responseFuture);
                                        }
                                    });
                                } else {
                                    awaitResultAsync(optionsCopy, result, responseFuture);
                                }
                        });

                return convertResult(result, method.getReturnType());
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    protected Object convertResult(RemotePromise<Object> result, Class<?> returnType) {
        return result;
    }
    
    private void awaitResultAsync(RemoteInvocationOptions optionsCopy, RemotePromise<Object> result,
            String ackName, RFuture<RRemoteServiceResponse> responseFuture) {
        RFuture<Boolean> deleteFuture = new RedissonBucket<>(commandExecutor, ackName).deleteAsync();
        deleteFuture.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            awaitResultAsync(optionsCopy, result, responseFuture);
        });
    }
    
    protected void awaitResultAsync(RemoteInvocationOptions optionsCopy, RemotePromise<Object> result,
            RFuture<RRemoteServiceResponse> responseFuture) {
        // poll for the response only if expected
        if (!optionsCopy.isResultExpected()) {
            return;
        }
        
        responseFuture.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            if (res == null) {
                RemoteServiceTimeoutException ex = new RemoteServiceTimeoutException("No response after "
                        + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + result.getRequestId());
                result.tryFailure(ex);
                return;
            }
            
            if (res instanceof RemoteServiceCancelResponse) {
                result.doCancel();
                return;
            }
            
            RemoteServiceResponse response = (RemoteServiceResponse) res;
            if (response.getError() != null) {
                result.tryFailure(response.getError());
                return;
            }
            
            result.trySuccess(response.getResult());
        });
    }
    
    private RemotePromise<Object> createResultPromise(RemoteInvocationOptions optionsCopy,
            RequestId requestId, String requestQueueName, Long ackTimeout) {
        RemotePromise<Object> result = new RemotePromise<Object>(requestId) {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (isCancelled()) {
                    return true;
                }
                
                if (isDone()) {
                    return false;
                }
                
                
                if (optionsCopy.isAckExpected()) {
                    String ackName = remoteService.getAckName(requestId);
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
                        RList<Object> list = new RedissonList<>(LongCodec.INSTANCE, commandExecutor, requestQueueName, null);
                        list.remove(requestId.toString());
                        super.cancel(mayInterruptIfRunning);
                        return true;
                    }

                    return doCancel(mayInterruptIfRunning);
                }

                boolean removed = commandExecutor.get(remoteService.removeAsync(requestQueueName, requestId));
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
    
    private void cancelExecution(RemoteInvocationOptions optionsCopy,
            boolean mayInterruptIfRunning, RemotePromise<Object> remotePromise) {
        RMap<String, RemoteServiceCancelRequest> canceledRequests = new RedissonMap<>(new CompositeCodec(StringCodec.INSTANCE, codec, codec), commandExecutor, cancelRequestMapName, null, null, null);
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

}
