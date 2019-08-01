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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RRemoteService;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.BaseRemoteService;
import org.redisson.remote.RRemoteServiceResponse;
import org.redisson.remote.RemoteServiceAck;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceKey;
import org.redisson.remote.RemoteServiceMethod;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RemoteServiceResponse;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRemoteService extends BaseRemoteService implements RRemoteService {

    public static class Entry {
        
        RFuture<String> future;
        final AtomicInteger counter;
        
        public Entry(int workers) {
            counter = new AtomicInteger(workers);
        }
        
        public void setFuture(RFuture<String> future) {
            this.future = future;
        }
        
        public RFuture<String> getFuture() {
            return future;
        }
        
        public AtomicInteger getCounter() {
            return counter;
        }
        
    }
    
    private static final Logger log = LoggerFactory.getLogger(RedissonRemoteService.class);

    private final Map<RemoteServiceKey, RemoteServiceMethod> beans = new ConcurrentHashMap<>();
    private final Map<Class<?>, Entry> remoteMap = new ConcurrentHashMap<>();

    public RedissonRemoteService(Codec codec, String name, CommandAsyncExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, name, commandExecutor, executorId, responses);
    }
    
    @Override
    protected RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request,
            RemotePromise<Object> result) {
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "redis.call('hset', KEYS[2], ARGV[1], ARGV[2]);"
                + "redis.call('rpush', KEYS[1], ARGV[1]); "
                + "return 1;",
                Arrays.<Object>asList(requestQueueName, requestQueueName + ":tasks"),
                request.getId(), encode(request));

        result.setAddFuture(future);
        return future;
    }

    @Override
    protected RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('lrem', KEYS[1], 1, ARGV[1]); "
              + "redis.call('hdel', KEYS[2], ARGV[1]);"
              + "return 1;",
              Arrays.<Object>asList(requestQueueName, requestQueueName + ":tasks"),
              taskId.toString());
    }
        
    @Override
    public <T> void register(Class<T> remoteInterface, T object) {
        register(remoteInterface, object, 1);
    }

    @Override
    public <T> void deregister(Class<T> remoteInterface) {
        for (Method method : remoteInterface.getMethods()) {
            RemoteServiceKey key = new RemoteServiceKey(remoteInterface, method.getName(), getMethodSignature(method));
            beans.remove(key);
        }
        
        Entry entry = remoteMap.remove(remoteInterface);
        if (entry != null && entry.getFuture() != null) {
            entry.getFuture().cancel(false);
        }
    }
    
    @Override
    public int getPendingInvocations(Class<?> remoteInterface) {
        String requestQueueName = getRequestQueueName(remoteInterface);
        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);
        return requestQueue.size();
    }
    
    @Override
    public int getFreeWorkers(Class<?> remoteInterface) {
        Entry entry = remoteMap.get(remoteInterface);
        if (entry == null) {
            return 0;
        }
        return entry.getCounter().get();
    }
    
    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workers) {
        register(remoteInterface, object, workers, commandExecutor.getConnectionManager().getExecutor());
    }

    private <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
    }
    
    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workers, ExecutorService executor) {
        if (workers < 1) {
            throw new IllegalArgumentException("executorsAmount can't be lower than 1");
        }
        for (Method method : remoteInterface.getMethods()) {
            RemoteServiceMethod value = new RemoteServiceMethod(method, object);
            RemoteServiceKey key = new RemoteServiceKey(remoteInterface, method.getName(), getMethodSignature(method));
            if (beans.put(key, value) != null) {
                return;
            }
        }

        remoteMap.put(remoteInterface, new Entry(workers));
        
        String requestQueueName = getRequestQueueName(remoteInterface);
        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);
        subscribe(remoteInterface, requestQueue, executor);
    }
    
    private <T> void subscribe(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor) {
        Entry entry = remoteMap.get(remoteInterface);
        if (entry == null) {
            return;
        }
        RFuture<String> take = requestQueue.takeAsync();
        entry.setFuture(take);
        take.onComplete((requestId, e) -> {
                Entry entr = remoteMap.get(remoteInterface);
                if (entr == null) {
                    return;
                }
                
                if (e != null) {
                    if (e instanceof RedissonShutdownException) {
                        return;
                    }
                    log.error("Can't process the remote service request.", e);
                    // re-subscribe after a failed takeAsync
                    subscribe(remoteInterface, requestQueue, executor);
                    return;
                }

                // do not subscribe now, see
                // https://github.com/mrniko/redisson/issues/493
                // subscribe(remoteInterface, requestQueue);
                
                if (entry.getCounter().get() == 0) {
                    return;
                }
                
                if (entry.getCounter().decrementAndGet() > 0) {
                    subscribe(remoteInterface, requestQueue, executor);
                }

                RMap<String, RemoteServiceRequest> tasks = getMap(requestQueue.getName() + ":tasks");
                RFuture<RemoteServiceRequest> taskFuture = getTask(requestId, tasks);
                taskFuture.onComplete((request, exc) -> {
                    if (exc != null) {
                        if (exc instanceof RedissonShutdownException) {
                            return;
                        }
                        log.error("Can't process the remote service request with id " + requestId, exc);
                            
                        // re-subscribe after a failed takeAsync
                        resubscribe(remoteInterface, requestQueue, executor);
                        return;
                    }
                    
                    if (request == null) {
                        log.debug("Task can't be found for request: {}", requestId);
                        
                        // re-subscribe after a skipped ackTimeout
                        resubscribe(remoteInterface, requestQueue, executor);
                        return;
                    }
                    
                    long elapsedTime = System.currentTimeMillis() - request.getDate();
                    // check the ack only if expected
                    if (request.getOptions().isAckExpected() && elapsedTime > request
                            .getOptions().getAckTimeoutInMillis()) {
                        log.debug("request: {} has been skipped due to ackTimeout. Elapsed time: {}ms", request.getId(), elapsedTime);
                        
                        // re-subscribe after a skipped ackTimeout
                        resubscribe(remoteInterface, requestQueue, executor);
                        return;
                    }


                    // send the ack only if expected
                    if (request.getOptions().isAckExpected()) {
                        String responseName = getResponseQueueName(request.getExecutorId());
                        String ackName = getAckName(request.getId());
                                RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(responseName,
                                        LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                                            "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                                                + "redis.call('pexpire', KEYS[1], ARGV[1]);"
//                                                    + "redis.call('rpush', KEYS[2], ARGV[1]);"
//                                                    + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                                + "return 1;" 
                                            + "end;" 
                                            + "return 0;",
                                        Arrays.<Object>asList(ackName),
                                        request.getOptions().getAckTimeoutInMillis());
//                                            Arrays.<Object>asList(ackName, responseName),
//                                            encode(new RemoteServiceAck(request.getId())), request.getOptions().getAckTimeoutInMillis());

                                ackClientsFuture.onComplete((r, ex) -> {
                                    if (ex != null) {
                                        if (ex instanceof RedissonShutdownException) {
                                            return;
                                        }
                                        log.error("Can't send ack for request: " + request, ex);

                                        // re-subscribe after a failed send (ack)
                                        resubscribe(remoteInterface, requestQueue, executor);
                                        return;
                                    }

                                    if (!r) {
                                        resubscribe(remoteInterface, requestQueue, executor);
                                        return;
                                    }
                                    

                                    RList<Object> list = new RedissonList<>(codec, commandExecutor, responseName, null);
                                    RFuture<Boolean> addFuture = list.addAsync(new RemoteServiceAck(request.getId()));
                                    addFuture.onComplete((res, exce) -> {
                                        if (exce != null) {
                                            if (exce instanceof RedissonShutdownException) {
                                                return;
                                            }
                                            log.error("Can't send ack for request: " + request, exce);

                                            // re-subscribe after a failed send (ack)
                                            resubscribe(remoteInterface, requestQueue, executor);
                                            return;
                                        }

                                        if (!res) {
                                            resubscribe(remoteInterface, requestQueue, executor);
                                            return;
                                        }
                                        
                                        executeMethod(remoteInterface, requestQueue, executor, request);
                                    });
                                });
                    } else {
                        executeMethod(remoteInterface, requestQueue, executor, request);
                    }
                });
        });
    }
    
    private <T> void executeMethod(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor, RemoteServiceRequest request) {
        RemoteServiceMethod method = beans.get(new RemoteServiceKey(remoteInterface, request.getMethodName(), request.getSignature()));
        String responseName = getResponseQueueName(request.getExecutorId());
        

        AtomicReference<RRemoteServiceResponse> responseHolder = new AtomicReference<RRemoteServiceResponse>();
        
        RPromise<RemoteServiceCancelRequest> cancelRequestFuture = new RedissonPromise<RemoteServiceCancelRequest>();
        scheduleCheck(cancelRequestMapName, new RequestId(request.getId()), cancelRequestFuture);
        
        java.util.concurrent.Future<?> submitFuture = executor.submit(() -> {
            invokeMethod(remoteInterface, requestQueue, request, method, responseName, executor,
                    cancelRequestFuture, responseHolder);
        });
        
        cancelRequestFuture.onComplete((r, e) -> {
            if (e != null) {
                return;
            }

            boolean res = submitFuture.cancel(r.isMayInterruptIfRunning());
            if (res) {
                RemoteServiceCancelResponse response = new RemoteServiceCancelResponse(request.getId(), true);
                if (!responseHolder.compareAndSet(null, response)) {
                    response = new RemoteServiceCancelResponse(request.getId(), false);
                }
                
                // could be removed not from future object
                if (r.isSendResponse()) {
                    RMap<String, RemoteServiceCancelResponse> map = getMap(cancelResponseMapName);
                    map.fastPutAsync(request.getId(), response);
                    map.expireAsync(60, TimeUnit.SECONDS);
                }
            }
        });
    }

    private <T> void invokeMethod(Class<T> remoteInterface,
            RBlockingQueue<String> requestQueue, RemoteServiceRequest request,
            RemoteServiceMethod method, String responseName, ExecutorService executor,
            RFuture<RemoteServiceCancelRequest> cancelRequestFuture, AtomicReference<RRemoteServiceResponse> responseHolder) {
        try {
            Object result = method.getMethod().invoke(method.getBean(), request.getArgs());

            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), result);
            responseHolder.compareAndSet(null, response);
        } catch (Exception e) {
            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), e.getCause());
            responseHolder.compareAndSet(null, response);
            log.error("Can't execute: " + request, e);
        }

        if (cancelRequestFuture != null) {
            cancelRequestFuture.cancel(false);
        }
        
        // send the response only if expected or task was canceled
        if (request.getOptions().isResultExpected()
                || responseHolder.get() instanceof RemoteServiceCancelResponse) {
            long timeout = 60 * 1000;
            if (request.getOptions().getExecutionTimeoutInMillis() != null) {
                timeout = request.getOptions().getExecutionTimeoutInMillis();
            }

            RBlockingQueueAsync<RRemoteServiceResponse> queue = getBlockingQueue(responseName, codec);
            RFuture<Void> clientsFuture = queue.putAsync(responseHolder.get());
            queue.expireAsync(timeout, TimeUnit.MILLISECONDS);

            clientsFuture.onComplete((res, e) -> {
                // interface has been deregistered 
                if (!remoteMap.containsKey(remoteInterface)) {
                    return;
                }
                
                if (e != null) {
                    if (e instanceof RedissonShutdownException) {
                        return;
                    }
                    log.error("Can't send response: " + responseHolder.get() + " for request: " + request,
                            e);
                }
                
                resubscribe(remoteInterface, requestQueue, executor);
            });
        } else {
            resubscribe(remoteInterface, requestQueue, executor);
        }
    }

    private <T> void resubscribe(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor) {
        if (remoteMap.get(remoteInterface).getCounter().getAndIncrement() == 0) {
            // re-subscribe anyways after the method invocation
            subscribe(remoteInterface, requestQueue, executor);
        }
    }

    protected RFuture<RemoteServiceRequest> getTask(String requestId, RMap<String, RemoteServiceRequest> tasks) {
        return tasks.removeAsync(requestId);
    }

}
