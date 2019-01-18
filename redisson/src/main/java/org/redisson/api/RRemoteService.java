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
package org.redisson.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Allows to execute object methods remotely between Redisson instances (Server side and Client side instances in terms of remote invocation).
 * <p>
 * <b>1. Server side instance (worker instance).</b> Register object with RRemoteService instance. 
 * <p>
 * <code>
 * RRemoteService remoteService = redisson.getRemoteService();<br>
 * <br>
 * // register remote service before any remote invocation<br>
 * remoteService.register(SomeServiceInterface.class, someServiceImpl);
 * </code>
 * <p>
 * <b>2. Client side instance.</b> Invokes method remotely.
 * <p>
 * <code>
 * RRemoteService remoteService = redisson.getRemoteService();<br>
 * SomeServiceInterface service = remoteService.get(SomeServiceInterface.class);<br>
 * <br>
 * String result = service.doSomeStuff(1L, "secondParam", new AnyParam());
 * </code>
 * <p>
 * There are two timeouts during execution:
 * <p>
 * <b>Acknowledge (Ack) timeout.</b>Client side instance waits for acknowledge message from Server side instance.
 * <p>
 * If acknowledge has not been received by Client side instance then <code>RemoteServiceAckTimeoutException</code> will be thrown. 
 * And next invocation attempt can be made.
 * <p>
 * If acknowledge has not been received Client side instance but Server side instance has received invocation message already. 
 * In this case invocation will be skipped, due to ack timeout checking by Server side instance. 
 * <p>
 * <b>Execution timeout.</b> Client side instance received acknowledge message. If it hasn't received any result or error 
 * from server side during execution timeout then <code>RemoteServiceTimeoutException</code> will be thrown.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RRemoteService {

    /**
     * Returns free workers amount available for tasks 
     * 
     * @param remoteInterface - remote service interface
     * @return workers amount
     */
    int getFreeWorkers(Class<?> remoteInterface);
    
    /**
     * Register remote service with single worker
     *
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param object - remote service object
     */
    <T> void register(Class<T> remoteInterface, T object);
    
    /**
     * Register remote service with custom workers amount
     *
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param object - remote service object
     * @param workersAmount - workers amount
     */
    <T> void register(Class<T> remoteInterface, T object, int workersAmount);

    /**
     * Register remote service with custom workers amount
     * and executor for running them
     * 
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param object - remote service object
     * @param workers - workers amount
     * @param executor - executor service
     */
    <T> void register(Class<T> remoteInterface, T object, int workers, ExecutorService executor);
    
    /**
     * Deregister all workers for remote service
     *
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     */
    <T> void deregister(Class<T> remoteInterface);
    
    /**
     * Get remote service object for remote invocations.
     * <p>
     * This method is a shortcut for
     * <pre>
     *     get(remoteInterface, RemoteInvocationOptions.defaults())
     * </pre>
     *
     * @see RemoteInvocationOptions#defaults()
     * @see #get(Class, RemoteInvocationOptions)
     *
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @return remote service instance
     */
    <T> T get(Class<T> remoteInterface);

    /**
     * Get remote service object for remote invocations 
     * with specified invocation timeout.
     * <p>
     * This method is a shortcut for
     * <pre>
     *     get(remoteInterface, RemoteInvocationOptions.defaults()
     *      .expectResultWithin(executionTimeout, executionTimeUnit))
     * </pre>
     *
     * @see RemoteInvocationOptions#defaults()
     * @see #get(Class, RemoteInvocationOptions)
     *
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param executionTimeout - invocation timeout
     * @param executionTimeUnit - time unit
     * @return remote service instance
     */
    <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit);
    
    /**
     * Get remote service object for remote invocations
     * with specified invocation and ack timeouts
     * <p>
     * This method is a shortcut for
     * <pre>
     *     get(remoteInterface, RemoteInvocationOptions.defaults()
     *      .expectAckWithin(ackTimeout, ackTimeUnit)
     *      .expectResultWithin(executionTimeout, executionTimeUnit))
     * </pre>
     *
     * @see RemoteInvocationOptions
     * @see #get(Class, RemoteInvocationOptions)
     * 
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param executionTimeout - invocation timeout
     * @param executionTimeUnit - time unit
     * @param ackTimeout - ack timeout
     * @param ackTimeUnit - time unit
     * @return remote service object
     */
    <T> T get(Class<T> remoteInterface, long executionTimeout, TimeUnit executionTimeUnit, long ackTimeout, TimeUnit ackTimeUnit);

    /**
     * Get remote service object for remote invocations
     * with the specified options
     * <p>
     * Note that when using the noResult() option,
     * it is expected that the invoked method returns void,
     * or else IllegalArgumentException will be thrown.
     *
     * @see RemoteInvocationOptions
     * 
     * @param <T> type of remote service
     * @param remoteInterface - remote service interface
     * @param options - service options
     * @return remote service object
     */
    <T> T get(Class<T> remoteInterface, RemoteInvocationOptions options);

}
