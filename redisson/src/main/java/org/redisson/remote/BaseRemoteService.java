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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.RedissonMap;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.api.annotation.RRemoteReactive;
import org.redisson.api.annotation.RRemoteRx;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.Hash;
import org.redisson.misc.RPromise;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseRemoteService {

    private final Map<Class<?>, String> requestQueueNameCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Method, long[]> methodSignaturesCache = new ConcurrentHashMap<>();

    protected final Codec codec;
    protected final String name;
    protected final CommandAsyncExecutor commandExecutor;
    protected final String executorId;

    protected final String cancelRequestMapName;
    protected final String cancelResponseMapName;
    protected final String responseQueueName;
    private final ConcurrentMap<String, ResponseEntry> responses;

    public BaseRemoteService(Codec codec, String name, CommandAsyncExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        this.codec = codec;
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
                AsyncRemoteProxy proxy = new AsyncRemoteProxy(commandExecutor, name, responseQueueName, responses, codec, executorId, cancelRequestMapName, this);
                return proxy.create(remoteInterface, options, syncInterface);
            }

            if (annotation.annotationType() == RRemoteReactive.class) {
                Class<T> syncInterface = (Class<T>) ((RRemoteReactive) annotation).value();
                ReactiveRemoteProxy proxy = new ReactiveRemoteProxy(commandExecutor, name, responseQueueName, responses, codec, executorId, cancelRequestMapName, this);
                return proxy.create(remoteInterface, options, syncInterface);
            }

            if (annotation.annotationType() == RRemoteRx.class) {
                Class<T> syncInterface = (Class<T>) ((RRemoteRx) annotation).value();
                RxRemoteProxy proxy = new RxRemoteProxy(commandExecutor, name, responseQueueName, responses, codec, executorId, cancelRequestMapName, this);
                return proxy.create(remoteInterface, options, syncInterface);
            }
        }

        SyncRemoteProxy proxy = new SyncRemoteProxy(commandExecutor, name, responseQueueName, responses, codec, executorId, this);
        return proxy.create(remoteInterface, options);
    }

    protected long getTimeout(Long executionTimeoutInMillis, RemoteServiceRequest request) {
        return executionTimeoutInMillis;
    }

    protected <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<>(new CompositeCodec(StringCodec.INSTANCE, codec, codec), commandExecutor, name, null, null, null);
    }
    
    protected <T> void scheduleCheck(String mapName, RequestId requestId, RPromise<T> cancelRequest) {
        commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (cancelRequest.isDone()) {
                    return;
                }

                RMap<String, T> canceledRequests = getMap(mapName);
                RFuture<T> future = canceledRequests.removeAsync(requestId.toString());
                future.onComplete((request, ex) -> {
                    if (cancelRequest.isDone()) {
                        return;
                    }
                    if (ex != null) {
                        scheduleCheck(mapName, requestId, cancelRequest);
                        return;
                    }
                    
                    if (request == null) {
                        scheduleCheck(mapName, requestId, cancelRequest);
                    } else {
                        cancelRequest.trySuccess(request);
                    }
                });
            }
        }, 3000, TimeUnit.MILLISECONDS);
    }

    protected RequestId generateRequestId() {
        byte[] id = new byte[17];
        ThreadLocalRandom.current().nextBytes(id);
        id[0] = 00;
        return new RequestId(id);
    }

    protected abstract RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request,
            RemotePromise<Object> result);

    protected abstract RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId);

    protected long[] getMethodSignature(Method method) {
        long[] result = methodSignaturesCache.get(method);
        if (result == null) {
            String str = Arrays.stream(method.getParameterTypes())
                                .map(c -> c.getName())
                                .collect(Collectors.joining());
            ByteBuf buf = Unpooled.copiedBuffer(str, CharsetUtil.UTF_8);
            result = Hash.hash128(buf);
            buf.release();
            long[] oldResult = methodSignaturesCache.putIfAbsent(method, result);
            if (oldResult != null) {
                return oldResult;
            }
        }
        
        return result;
    }

}
