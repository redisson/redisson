/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.core.RBatch;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RBlockingQueueAsync;
import org.redisson.core.RRemoteService;
import org.redisson.remote.RRemoteServiceResponse;
import org.redisson.remote.RemoteServiceAck;
import org.redisson.remote.RemoteServiceAckTimeoutException;
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
    
    private final Redisson redisson;
    private final String name;
    
    public RedissonRemoteService(Redisson redisson) {
        this(redisson, "redisson_remote_service");
    }

    public RedissonRemoteService(Redisson redisson, String name) {
        this.redisson = redisson;
        this.name = name;
    }

    @Override
    public <T> void register(Class<T> remoteInterface, T object) {
        register(remoteInterface, object, 1);
    }
    
    @Override
    public <T> void register(Class<T> remoteInterface, T object, int executorsAmount) {
        if (executorsAmount < 1) {
            throw new IllegalArgumentException("executorsAmount can't be lower than 1");
        }
        for (Method method : remoteInterface.getMethods()) {
            RemoteServiceMethod value = new RemoteServiceMethod(method, object);
            RemoteServiceKey key = new RemoteServiceKey(remoteInterface, method.getName());
            if (beans.put(key, value) != null) {
                return;
            }
        }
        
        for (int i = 0; i < executorsAmount; i++) {
            String requestQueueName = name + ":{" + remoteInterface.getName() + "}";
            RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName);
            subscribe(remoteInterface, requestQueue);
        }
    }

    private <T> void subscribe(final Class<T> remoteInterface, final RBlockingQueue<RemoteServiceRequest> requestQueue) {
        Future<RemoteServiceRequest> take = requestQueue.takeAsync();
        take.addListener(new FutureListener<RemoteServiceRequest>() {
            @Override
            public void operationComplete(Future<RemoteServiceRequest> future) throws Exception {
                if (!future.isSuccess()) {
                    if (future.cause() instanceof RedissonShutdownException) {
                        return;
                    }
                    // re-subscribe after a failed takeAsync
                    subscribe(remoteInterface, requestQueue);
                    return;
                }

                // do not subscribe now, see https://github.com/mrniko/redisson/issues/493
                // subscribe(remoteInterface, requestQueue);

                final RemoteServiceRequest request = future.getNow();
                // negative ackTimeout means unacknowledged call, do not check the ack
                if (request.getAckTimeout() >= 0 && System.currentTimeMillis() - request.getDate() > request.getAckTimeout()) {
                    log.debug("request: {} has been skipped due to ackTimeout");
                    // re-subscribe after a skipped ackTimeout
                    subscribe(remoteInterface, requestQueue);
                    return;
                }
                
                final RemoteServiceMethod method = beans.get(new RemoteServiceKey(remoteInterface, request.getMethodName()));
                final String responseName = name + ":{" + remoteInterface.getName() + "}:" + request.getRequestId();

                // negative ackTimeout means unacknowledged call, do not send the ack
                if (request.getAckTimeout() >= 0) {
                    Future<List<?>> ackClientsFuture = send(request.getAckTimeout(), responseName, new RemoteServiceAck());
                    ackClientsFuture.addListener(new FutureListener<List<?>>() {
                        @Override
                        public void operationComplete(Future<List<?>> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error("Can't send ack for request: " + request, future.cause());
                                if (future.cause() instanceof RedissonShutdownException) {
                                    return;
                                }
                                // re-subscribe after a failed send (ack)
                                subscribe(remoteInterface, requestQueue);
                                return;
                            }

                            invokeMethod(remoteInterface, requestQueue, request, method, responseName);
                        }
                    });
                } else {
                    invokeMethod(remoteInterface, requestQueue, request, method, responseName);
                }
            }

        });
    }

    private <T> void invokeMethod(final Class<T> remoteInterface, final RBlockingQueue<RemoteServiceRequest> requestQueue, final RemoteServiceRequest request, RemoteServiceMethod method, String responseName) {
        final AtomicReference<RemoteServiceResponse> responseHolder = new AtomicReference<RemoteServiceResponse>();
        try {
            Object result = method.getMethod().invoke(method.getBean(), request.getArgs());
            RemoteServiceResponse response = new RemoteServiceResponse(result);
            responseHolder.set(response);
        } catch (Exception e) {
            RemoteServiceResponse response = new RemoteServiceResponse(e.getCause());
            responseHolder.set(response);
            log.error("Can't execute: " + request, e);
        }

        // negative responseTimeout means fire-and-forget call, do not send the response
        if (request.getResponseTimeout() >= 0) {
            Future<List<?>> clientsFuture = send(request.getResponseTimeout(), responseName, responseHolder.get());
            clientsFuture.addListener(new FutureListener<List<?>>() {
                @Override
                public void operationComplete(Future<List<?>> future) throws Exception {
                    if (!future.isSuccess()) {
                        log.error("Can't send response: " + responseHolder.get() + " for request: " + request, future.cause());
                        if (future.cause() instanceof RedissonShutdownException) {
                            return;
                        }
                    }
                    // re-subscribe anyways (fail or success) after the send (response)
                    subscribe(remoteInterface, requestQueue);
                }
            });
        } else {
            // re-subscribe anyways after the method invocation
            subscribe(remoteInterface, requestQueue);
        }
    }
    
    @Override
    public <T> T get(Class<T> remoteInterface) {
        return get(remoteInterface, 30, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(final Class<T> remoteInterface, final long executionTimeout, final TimeUnit executionTimeUnit) {
        return get(remoteInterface, executionTimeout, executionTimeUnit, 1, TimeUnit.SECONDS);
    }
    
    public <T> T get(final Class<T> remoteInterface, final long executionTimeout, final TimeUnit executionTimeUnit, 
                        final long ackTimeout, final TimeUnit ackTimeUnit) {
        final String toString = getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-" + generateRequestId();
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

                String requestId = generateRequestId();
                
                String requestQueueName = name + ":{" + remoteInterface.getName() + "}";
                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName);
                RemoteServiceRequest request = new RemoteServiceRequest(requestId, method.getName(), args, 
                                                ackTimeUnit.toMillis(ackTimeout), executionTimeUnit.toMillis(executionTimeout), System.currentTimeMillis());
                requestQueue.add(request);
                
                String responseName = name + ":{" + remoteInterface.getName() + "}:" + requestId;
                RBlockingQueue<RRemoteServiceResponse> responseQueue = redisson.getBlockingQueue(responseName);

                // negative ackTimeout means unacknowledged call, do not poll for the ack
                if (ackTimeout >= 0) {
                    RemoteServiceAck ack = (RemoteServiceAck) responseQueue.poll(ackTimeout, ackTimeUnit);
                    if (ack == null) {
                        throw new RemoteServiceAckTimeoutException("No ACK response after " + ackTimeUnit.toMillis(ackTimeout) + "ms for request: " + request);
                    }
                }

                // negative executionTimeout means fire-and-forget call, do not poll for the response
                if (executionTimeout >= 0) {
                    RemoteServiceResponse response = (RemoteServiceResponse) responseQueue.poll(executionTimeout, executionTimeUnit);
                    if (response == null) {
                        throw new RemoteServiceTimeoutException("No response after " + executionTimeUnit.toMillis(executionTimeout) + "ms for request: " + request);
                    }
                    if (response.getError() != null) {
                        throw response.getError();
                    }
                    return response.getResult();
                }

                return getDefaultValue(method.getReturnType());
            }
        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] {remoteInterface}, handler);
    }

    private String generateRequestId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    private <T extends RRemoteServiceResponse> Future<List<?>> send(long timeout, String responseName, T response) {
        RBatch batch = redisson.createBatch();
        RBlockingQueueAsync<T> queue = batch.getBlockingQueue(responseName);
        queue.putAsync(response);
        queue.expireAsync(timeout, TimeUnit.MILLISECONDS);
        return batch.executeAsync();
    }

    /**
     * Horrible hack to get the default value for a Class
     *
     * @param type the Class
     * @return the default value as
     */
    private static Object getDefaultValue(Class type) {
        if (!type.isPrimitive() || type.equals(void.class)) {
            return null;
        } else if (type.equals(boolean.class)) {
            return false;
        } else if (type.equals(byte.class)) {
            return (byte) 0;
        } else if (type.equals(char.class)) {
            return (char) 0;
        } else if (type.equals(short.class)) {
            return (short) 0;
        } else if (type.equals(int.class)) {
            return (int) 0;
        } else if (type.equals(long.class)) {
            return (long) 0;
        } else if (type.equals(float.class)) {
            return (float) 0;
        } else if (type.equals(double.class)) {
            return (double) 0;
        }
        throw new IllegalArgumentException("Class " + type + " not supported");
    }
}
