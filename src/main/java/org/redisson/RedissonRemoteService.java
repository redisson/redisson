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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.core.MessageListener;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RRemoteService;
import org.redisson.core.RTopic;
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

public class RedissonRemoteService implements RRemoteService {

    private static final Logger log = LoggerFactory.getLogger(RedissonRemoteService.class); 
    
    private final Map<RemoteServiceKey, RemoteServiceMethod> beans = PlatformDependent.newConcurrentHashMap();
    
    private final Redisson redisson;
    
    public RedissonRemoteService(Redisson redisson) {
        this.redisson = redisson;
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
            String requestQueueName = "redisson_remote_service:{" + remoteInterface.getName() + "}";
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
                    subscribe(remoteInterface, requestQueue);
                    return;
                }
                subscribe(remoteInterface, requestQueue);
                
                final RemoteServiceRequest request = future.getNow();
                if (System.currentTimeMillis() - request.getDate() > request.getAckTimeout()) {
                    log.debug("request: {} has been skipped due to ackTimeout");
                    return;
                }
                
                final RemoteServiceMethod method = beans.get(new RemoteServiceKey(remoteInterface, request.getMethodName()));
                String responseName = "redisson_remote_service:{" + remoteInterface.getName() + "}:" + request.getRequestId();
                final RTopic<RRemoteServiceResponse> topic = redisson.getTopic(responseName);
                Future<Long> ackClientsFuture = topic.publishAsync(new RemoteServiceAck());
                ackClientsFuture.addListener(new FutureListener<Long>() {
                    @Override
                    public void operationComplete(Future<Long> future) throws Exception {
                        if (!future.isSuccess()) {
                            log.error("Can't send ack for request: " + request, future.cause());
                            return;
                        }
                        if (future.getNow() == 0) {
                            log.error("Client has not received ack for request: {}", request);
                            return;
                        }
                        
                        invokeMethod(request, method, topic);
                    }
                });
            }

        });
    }

    private void invokeMethod(final RemoteServiceRequest request, RemoteServiceMethod method,
            RTopic<RRemoteServiceResponse> topic) {
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
        
        Future<Long> clientsFuture = topic.publishAsync(responseHolder.get());
        clientsFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow() == 0) {
                    log.error("None of clients has not received a response: {} for request: {}", responseHolder.get(), request);
                }
            }
        });
    }
    
    @Override
    public <T> T get(Class<T> remoteInterface) {
        return get(remoteInterface, -1, null);
    }

    @Override
    public <T> T get(final Class<T> remoteInterface, final long executionTimeout, final TimeUnit executionTimeUnit) {
        return get(remoteInterface, executionTimeout, executionTimeUnit, 1, TimeUnit.SECONDS);
    }
    
    public <T> T get(final Class<T> remoteInterface, final long executionTimeout, final TimeUnit executionTimeUnit, 
                        final long ackTimeout, final TimeUnit ackTimeUnit) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String requestId = generateRequestId();
                
                String requestQueueName = "redisson_remote_service:{" + remoteInterface.getName() + "}";
                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName);
                RemoteServiceRequest request = new RemoteServiceRequest(requestId, method.getName(), args, ackTimeUnit.toMillis(ackTimeout), System.currentTimeMillis());
                requestQueue.add(request);
                
                String responseName = "redisson_remote_service:{" + remoteInterface.getName() + "}:" + requestId;
                final CountDownLatch ackLatch = new CountDownLatch(1);
                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<RRemoteServiceResponse> response = new AtomicReference<RRemoteServiceResponse>();
                final RTopic<RRemoteServiceResponse> topic = redisson.getTopic(responseName);
                int listenerId = topic.addListener(new MessageListener<RRemoteServiceResponse>() {
                    @Override
                    public void onMessage(String channel, RRemoteServiceResponse msg) {
                        if (msg instanceof RemoteServiceResponse) {
                            response.set(msg);
                            latch.countDown();
                        } else {
                            ackLatch.countDown();
                        }
                    }
                });
                
                if (!ackLatch.await(ackTimeout, ackTimeUnit)) {
                    topic.removeListener(listenerId);
                    throw new RemoteServiceAckTimeoutException("No ACK response after " + ackTimeUnit.toMillis(ackTimeout) + "ms for request: " + request);
                }
                
                if (executionTimeout == -1) {
                    latch.await();
                } else {
                    if (!latch.await(executionTimeout, executionTimeUnit)) {
                        topic.removeListener(listenerId);
                        throw new RemoteServiceTimeoutException("No response after " + executionTimeUnit.toMillis(executionTimeout) + "ms for request: " + request);
                    }
                }
                topic.removeListener(listenerId);
                RemoteServiceResponse msg = (RemoteServiceResponse) response.get();
                if (msg.getError() != null) {
                    throw msg.getError();
                }
                return msg.getResult();
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
    
}
