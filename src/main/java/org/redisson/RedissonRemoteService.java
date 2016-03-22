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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.core.MessageListener;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RRemoteService;
import org.redisson.core.RTopic;
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
    private final Queue<Future<RemoteServiceRequest>> futures = new ConcurrentLinkedQueue<Future<RemoteServiceRequest>>();
    
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
        futures.add(take);
        take.addListener(new FutureListener<RemoteServiceRequest>() {
            @Override
            public void operationComplete(Future<RemoteServiceRequest> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                RemoteServiceRequest request = future.getNow();
                RemoteServiceMethod method = beans.get(new RemoteServiceKey(remoteInterface, request.getMethodName()));
                String responseName = "redisson_remote_service:{" + remoteInterface.getName() + "}:" + request.getRequestId();
                RTopic<RemoteServiceResponse> topic = redisson.getTopic(responseName);
                RemoteServiceResponse response;
                try {
                    Object result = method.getMethod().invoke(method.getBean(), request.getArgs());
                    response = new RemoteServiceResponse(result);
                } catch (Exception e) {
                    response = new RemoteServiceResponse(e.getCause());
                    log.error("Can't execute: " + request, e);
                }
                
                long clients = topic.publish(response);
                if (clients == 0) {
                    log.error("None of clients has not received a response for: {}", request);
                }
                
                futures.remove(future);
                subscribe(remoteInterface, requestQueue);
            }
        });
    }

    @Override
    public <T> T get(final Class<T> remoteInterface) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String requestId = generateRequestId();

                String requestQueueName = "redisson_remote_service:{" + remoteInterface.getName() + "}";
                RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName);
                requestQueue.add(new RemoteServiceRequest(requestId, method.getName(), args));
                
                String responseName = "redisson_remote_service:{" + remoteInterface.getName() + "}:" + requestId;
                final RTopic<RemoteServiceResponse> topic = redisson.getTopic(responseName);
                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<RemoteServiceResponse> response = new AtomicReference<RemoteServiceResponse>();
                int listenerId = topic.addListener(new MessageListener<RemoteServiceResponse>() {
                    @Override
                    public void onMessage(String channel, RemoteServiceResponse msg) {
                        response.set(msg);
                        latch.countDown();
                    }
                });
                
                latch.await();
                topic.removeListener(listenerId);
                RemoteServiceResponse msg = response.get();
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
    
    public void shutdown() {
        for (Future<RemoteServiceRequest> future : futures) {
            future.cancel(true);
        }
    }
    
}
