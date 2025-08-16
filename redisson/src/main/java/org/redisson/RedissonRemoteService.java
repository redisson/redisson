/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRemoteService extends BaseRemoteService implements RRemoteService {

    public static class Entry {
        
        RFuture<String> future;
        final AtomicInteger freeWorkers;
        
        public Entry(int workers) {
            freeWorkers = new AtomicInteger(workers);
        }
        
        public void setFuture(RFuture<String> future) {
            this.future = future;
        }
        
        public RFuture<String> getFuture() {
            return future;
        }
        
        public AtomicInteger getFreeWorkers() {
            return freeWorkers;
        }
        
    }
    
    private static final Logger log = LoggerFactory.getLogger(RedissonRemoteService.class);

    private final Map<Class<?>, Entry> remoteMap = new ConcurrentHashMap<>();

    public RedissonRemoteService(Codec codec, String name, CommandAsyncExecutor commandExecutor, String executorId) {
        super(codec, name, commandExecutor, executorId);
    }

    public String getRequestTasksMapName(Class<?> remoteInterface) {
        String queue = getRequestQueueName(remoteInterface);
        return queue + ":tasks";
    }

    @Override
    protected CompletableFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request,
                                                  RemotePromise<Object> result) {
        RFuture<Boolean> future = commandExecutor.evalWriteNoRetryAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "redis.call('hset', KEYS[2], ARGV[1], ARGV[2]);"
                + "redis.call('rpush', KEYS[1], ARGV[1]); "
                + "return 1;",
                Arrays.asList(requestQueueName, requestQueueName + ":tasks"),
                request.getId(), encode(request));

        result.setAddFuture(future.toCompletableFuture());
        return future.toCompletableFuture();
    }

    @Override
    protected CompletableFuture<Boolean> removeAsync(String requestQueueName, String taskId) {
        RFuture<Boolean> f = commandExecutor.evalWriteNoRetryAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('lrem', KEYS[1], 1, ARGV[1]) > 0 then "
                        + "redis.call('hdel', KEYS[2], ARGV[1]);" +
                           "return 1;" +
                       "end;"
                      + "return 0;",
              Arrays.asList(requestQueueName, requestQueueName + ":tasks"),
              taskId);
        return f.toCompletableFuture();
    }
        
    @Override
    public <T> void register(Class<T> remoteInterface, T object) {
        register(remoteInterface, object, 1);
    }

    @Override
    public <T> void deregister(Class<T> remoteInterface) {
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
    public RFuture<Integer> getPendingInvocationsAsync(Class<?> remoteInterface) {
        String requestQueueName = getRequestQueueName(remoteInterface);
        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);
        return requestQueue.sizeAsync();
    }

    @Override
    public int getFreeWorkers(Class<?> remoteInterface) {
        Entry entry = remoteMap.get(remoteInterface);
        if (entry == null) {
            return 0;
        }
        return entry.getFreeWorkers().get();
    }
    
    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workers) {
        register(remoteInterface, object, workers, commandExecutor.getServiceManager().getExecutor());
    }

    @Override
    public <T> void register(Class<T> remoteInterface, T object, int workers, ExecutorService executor) {
        if (workers < 1) {
            throw new IllegalArgumentException("executorsAmount can't be lower than 1");
        }

        if (remoteMap.putIfAbsent(remoteInterface, new Entry(workers)) != null) {
            return;
        }
        
        String requestQueueName = getRequestQueueName(remoteInterface);
        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);
        subscribe(remoteInterface, requestQueue, executor, object);
    }

    @Override
    public <T> boolean tryExecute(Class<T> remoteInterface, T object, long timeout, TimeUnit timeUnit) throws InterruptedException {
        return tryExecute(remoteInterface, object, commandExecutor.getServiceManager().getExecutor(), timeout, timeUnit);
    }

    @Override
    public <T> boolean tryExecute(Class<T> remoteInterface, T object, ExecutorService executorService, long timeout, TimeUnit timeUnit) throws InterruptedException {
        String requestQueueName = getRequestQueueName(remoteInterface);
        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);

        String requestId = requestQueue.poll(timeout, timeUnit);
        if (requestId == null) {
            return false;
        }

        RMap<String, RemoteServiceRequest> tasks = getMap(((RedissonObject) requestQueue).getRawName() + ":tasks");
        RFuture<RemoteServiceRequest> taskFuture = getTask(requestId, tasks);
        RemoteServiceRequest request = commandExecutor.getInterrupted(taskFuture);
        if (request == null) {
            throw new IllegalStateException("Task can't be found for request: " + requestId);
        }

        RFuture<RRemoteServiceResponse> r = executeMethod(remoteInterface, requestQueue, executorService, request, object);
        commandExecutor.getInterrupted(r);
        return true;
    }

    @Override
    public <T> RFuture<Boolean> tryExecuteAsync(Class<T> remoteInterface, T object, long timeout, TimeUnit timeUnit) {
        return tryExecuteAsync(remoteInterface, object, commandExecutor.getServiceManager().getExecutor(), timeout, timeUnit);
    }

    @Override
    public <T> RFuture<Boolean> tryExecuteAsync(Class<T> remoteInterface, T object, ExecutorService executor, long timeout, TimeUnit timeUnit) {
        String requestQueueName = getRequestQueueName(remoteInterface);

        RBlockingQueue<String> requestQueue = getBlockingQueue(requestQueueName, StringCodec.INSTANCE);
        RFuture<String> pollFuture;
        if (timeout == -1) {
            pollFuture = requestQueue.pollAsync();
        } else {
            pollFuture = requestQueue.pollAsync(timeout, timeUnit);
        }
        CompletionStage<Boolean> f = pollFuture.thenCompose(requestId -> {
            if (requestId == null) {
                return CompletableFuture.completedFuture(false);
            }

            RMap<String, RemoteServiceRequest> tasks = getMap(((RedissonObject) requestQueue).getRawName() + ":tasks");
            RFuture<RemoteServiceRequest> taskFuture = getTask(requestId, tasks);
            return taskFuture.thenCompose(request -> {
                if (request == null) {
                    throw new CompletionException(new IllegalStateException("Task can't be found for request: " + requestId));
                }

                RFuture<RRemoteServiceResponse> future = executeMethod(remoteInterface, requestQueue, executor, request, object);
                return future.thenApply(r -> true);
            });
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public <T> RFuture<Boolean> tryExecuteAsync(Class<T> remoteInterface, T object) {
        return tryExecuteAsync(remoteInterface, object, -1, null);
    }
    
    @SuppressWarnings("MethodLength")
    private <T> void subscribe(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor, Object bean) {
        Entry entry = remoteMap.get(remoteInterface);
        if (entry == null) {
            return;
        }

        log.debug("subscribe: {}, free workers: {}", remoteInterface, entry.getFreeWorkers());

        RFuture<String> take = requestQueue.pollAsync(60, TimeUnit.SECONDS);
        entry.setFuture(take);
        take.whenComplete((requestId, e) -> {
                Entry entr = remoteMap.get(remoteInterface);
                if (entr == null) {
                    return;
                }
                
                if (e != null) {
                    if (commandExecutor.getServiceManager().isShuttingDown(e)) {
                        return;
                    }
                    log.error("Can't process the remote service request. A new attempt has been made.", e);
                    // re-subscribe after a failed takeAsync
                    MasterSlaveServersConfig config = commandExecutor.getServiceManager().getConfig();
                    commandExecutor.getServiceManager().newTimeout(task -> {
                        subscribe(remoteInterface, requestQueue, executor, bean);
                    }, config.getRetryDelay().calcDelay(config.getRetryAttempts()).toMillis(), TimeUnit.MILLISECONDS);

                    return;
                }

                if (entry.getFreeWorkers().get() == 0) {
                    return;
                }

                int freeWorkers = entry.getFreeWorkers().decrementAndGet();
                if (freeWorkers > 0) {
                    subscribe(remoteInterface, requestQueue, executor, bean);
                }

                // poll method may return null value
                if (requestId == null) {
                    // Because the previous code is already -1, it must be +1 before returning, otherwise the counter will become 0 soon
                    resubscribe(remoteInterface, requestQueue, executor, bean);
                    return;
                }

                RMap<String, RemoteServiceRequest> tasks = getMap(((RedissonObject) requestQueue).getRawName() + ":tasks");
                RFuture<RemoteServiceRequest> taskFuture = getTask(requestId, tasks);
                taskFuture.whenComplete((request, exc) -> {
                    if (exc != null) {
                        if (commandExecutor.getServiceManager().isShuttingDown(exc)) {
                            return;
                        }
                        log.error("Can't process the remote service request with id {}. Try to increase 'retryDelay' and/or 'retryAttempts' settings", requestId, exc);
                            
                        // re-subscribe after a failed takeAsync
                        resubscribe(remoteInterface, requestQueue, executor, bean);
                        return;
                    }
                    
                    if (request == null) {
                        log.debug("Task can't be found for request: {}", requestId);
                        
                        // re-subscribe after a skipped ackTimeout
                        resubscribe(remoteInterface, requestQueue, executor, bean);
                        return;
                    }
                    
                    long elapsedTime = System.currentTimeMillis() - request.getDate();
                    // check the ack only if expected
                    if (request.getOptions().isAckExpected() && elapsedTime > request
                            .getOptions().getAckTimeoutInMillis()) {
                        log.debug("request: {} has been skipped due to ackTimeout. Elapsed time: {}ms", request.getId(), elapsedTime);
                        
                        // re-subscribe after a skipped ackTimeout
                        resubscribe(remoteInterface, requestQueue, executor, bean);
                        return;
                    }


                    // send the ack only if expected
                    if (request.getOptions().isAckExpected()) {
                        String responseName = getResponseQueueName(request.getExecutorId());
                        String ackName = getAckName(request.getId());
                                RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteNoRetryAsync(responseName,
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

                                ackClientsFuture.whenComplete((r, ex) -> {
                                    if (ex != null) {
                                        if (commandExecutor.getServiceManager().isShuttingDown(ex)) {
                                            return;
                                        }
                                        log.error("Can't send ack for request: {}. Try to increase 'retryDelay' and/or 'retryAttempts' settings", request, ex);

                                        // re-subscribe after a failed send (ack)
                                        resubscribe(remoteInterface, requestQueue, executor, bean);
                                        return;
                                    }

                                    if (!r) {
                                        resubscribe(remoteInterface, requestQueue, executor, bean);
                                        return;
                                    }
                                    

                                    RList<Object> list = new RedissonList<>(codec, commandExecutor, responseName, null);
                                    RFuture<Boolean> addFuture = list.addAsync(new RemoteServiceAck(request.getId()));
                                    addFuture.whenComplete((res, exce) -> {
                                        if (exce != null) {
                                            if (commandExecutor.getServiceManager().isShuttingDown(exce)) {
                                                return;
                                            }
                                            log.error("Can't send ack for request: {}. Try to increase 'retryDelay' and/or 'retryAttempts' settings", request, exce);

                                            // re-subscribe after a failed send (ack)
                                            resubscribe(remoteInterface, requestQueue, executor, bean);
                                            return;
                                        }

                                        if (!res) {
                                            resubscribe(remoteInterface, requestQueue, executor, bean);
                                            return;
                                        }
                                        
                                        executeMethod(remoteInterface, requestQueue, executor, request, bean);
                                    })
                                    .exceptionally(exack -> {
                                        if (commandExecutor.getServiceManager().isShuttingDown(exack)) {
                                            return null;
                                        }
                                        log.error("Can't send ack for request: {}", request, exack);
                                        return null;
                                    });
                                });
                    } else {
                        executeMethod(remoteInterface, requestQueue, executor, request, bean);
                    }
                })
                .exceptionally(exc -> {
                    if (commandExecutor.getServiceManager().isShuttingDown(exc)) {
                        return null;
                    }
                    log.error("Can't process the remote service request with id {}", requestId, exc);
                    return null;
                });
        }).exceptionally(exc -> {
            if (commandExecutor.getServiceManager().isShuttingDown(exc)) {
                return null;
            }

            if (exc instanceof CancellationException || exc.getCause() instanceof CancellationException) {
                return null;
            }

            log.error("Can't process the remote service request", exc);
            return null;
        });
    }

    private final Map<RemoteServiceKey, Method> methodsCache = new ConcurrentHashMap<>();

    private <T> RFuture<RRemoteServiceResponse> executeMethod(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor, RemoteServiceRequest request, Object bean) {

        RemoteServiceKey key = new RemoteServiceKey(remoteInterface, request.getMethodName(), request.getSignature());
        Method rm = methodsCache.computeIfAbsent(key, k -> Arrays.stream(k.getServiceInterface().getMethods())
                                                          .filter(m -> m.getName().equals(k.getMethodName())
                                                                         && Arrays.equals(getMethodSignature(m), k.getSignature()))
                                                          .findFirst().get());

        RemoteServiceMethod method = new RemoteServiceMethod(rm, bean);
        String responseName = getResponseQueueName(request.getExecutorId());

        CompletableFuture<RRemoteServiceResponse> responsePromise = new CompletableFuture<>();
        CompletableFuture<RemoteServiceCancelRequest> cancelRequestFuture = new CompletableFuture<>();
        scheduleCheck(cancelRequestMapName, request.getId(), cancelRequestFuture);

        responsePromise.whenComplete((result, e) -> {
            if (request.getOptions().isResultExpected()
                || result instanceof RemoteServiceCancelResponse) {

                long timeout = 60 * 1000;
                if (request.getOptions().getExecutionTimeoutInMillis() != null) {
                    timeout = request.getOptions().getExecutionTimeoutInMillis();
                }
                long tt = timeout;

                RBlockingQueueAsync<RRemoteServiceResponse> queue = getBlockingQueue(responseName, codec);
                try {
                    RRemoteServiceResponse response;
                    if (result instanceof RemoteServiceResponse
                            && ((RemoteServiceResponse) result).getResult() instanceof Optional) {
                        Optional<?> o = (Optional<?>) ((RemoteServiceResponse) result).getResult();
                        response = new RemoteServiceResponse(result.getId(), o.orElse(null));
                    } else {
                        response = result;
                    }

                    CompletionStage<?> clientsFuture = queue.putAsync(response)
                            .thenCompose(s -> queue.expireAsync(Duration.ofMillis(tt)));

                    clientsFuture.whenComplete((res, exc) -> {
                        if (exc != null) {
                            if (commandExecutor.getServiceManager().isShuttingDown(exc)) {
                                return;
                            }
                            log.error("Can't send response: {} for request: {}. Try to increase 'retryDelay' and/or 'retryAttempts' settings", response, request, exc);
                        }

                        resubscribe(remoteInterface, requestQueue, executor, method.getBean());
                    });
                } catch (Exception ex) {
                    log.error("Can't send response: {} for request: {}", result, request, ex);
                }
            } else {
                resubscribe(remoteInterface, requestQueue, executor, method.getBean());
            }
        });

        java.util.concurrent.Future<?> submitFuture = executor.submit(() -> {
            if (commandExecutor.getServiceManager().isShuttingDown()) {
                return;
            }

            log.debug("start execution. requestId: {}, method: {}", request.getId(), method);

            invokeMethod(request, method, cancelRequestFuture, responsePromise);

            log.debug("end execution. requestId: {}, method: {}", request.getId(), method);
        });

        log.debug("task submitted. requestId: {}, method: {}", request.getId(), method);

        cancelRequestFuture.thenAccept(r -> {
            boolean res = submitFuture.cancel(r.isMayInterruptIfRunning());
            if (res) {
                RemoteServiceCancelResponse response = new RemoteServiceCancelResponse(request.getId(), true);
                if (!responsePromise.complete(response)) {
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

        return new CompletableFutureWrapper<>(responsePromise);
    }

    protected <T> void invokeMethod(RemoteServiceRequest request, RemoteServiceMethod method,
                                    CompletableFuture<RemoteServiceCancelRequest> cancelRequestFuture,
                                    CompletableFuture<RRemoteServiceResponse> responsePromise) {
        try {
            Object result = method.getMethod().invoke(method.getBean(), request.getArgs());

            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), result);
            responsePromise.complete(response);
        } catch (Exception e) {
            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), e.getCause());
            responsePromise.complete(response);
            log.error("Can't execute: {}", request, e);
        }

        if (cancelRequestFuture != null) {
            cancelRequestFuture.cancel(false);
        }
    }

    private <T> void resubscribe(Class<T> remoteInterface, RBlockingQueue<String> requestQueue,
            ExecutorService executor, Object bean) {
        Entry entry = remoteMap.get(remoteInterface);

        log.debug("resubscribe: {}, queue: {}", remoteInterface, requestQueue.getName());

        if (entry != null && entry.getFreeWorkers().getAndIncrement() == 0) {
            // re-subscribe anyways after the method invocation
            subscribe(remoteInterface, requestQueue, executor, bean);
        }
    }

    protected RFuture<RemoteServiceRequest> getTask(String requestId, RMap<String, RemoteServiceRequest> tasks) {
        return tasks.removeAsync(requestId);
    }

}
