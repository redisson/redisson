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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * RRemoteService invocation options.
 *
 * Used to tune how RRemoteService will behave
 * in regard to the remote invocations acknowledgement
 * and execution timeout.
 * <p>
 * Examples:
 * <pre>
 *     // 1 second ack timeout and 30 seconds execution timeout
 *     RemoteInvocationOptions options =
 *          RemoteInvocationOptions.defaults();
 *
 *     // no ack but 30 seconds execution timeout
 *     RemoteInvocationOptions options =
 *          RemoteInvocationOptions.defaults()
 *              .noAck();
 *
 *     // 1 second ack timeout then forget the result
 *     RemoteInvocationOptions options =
 *          RemoteInvocationOptions.defaults()
 *              .noResult();
 *
 *     // 1 minute ack timeout then forget about the result
 *     RemoteInvocationOptions options =
 *          RemoteInvocationOptions.defaults()
 *              .expectAckWithin(1, TimeUnit.MINUTES)
 *              .noResult();
 *
 *     // no ack and forget about the result (fire and forget)
 *     RemoteInvocationOptions options =
 *          RemoteInvocationOptions.defaults()
 *              .noAck()
 *              .noResult();
 * </pre>
 *
 * @see RRemoteService#get(Class, RemoteInvocationOptions)
 */
public class RemoteInvocationOptions implements Serializable {

    private static final long serialVersionUID = -7715968073286484802L;
    
    private Long ackTimeoutInMillis;
    private Long executionTimeoutInMillis;

    private RemoteInvocationOptions() {
    }

    public RemoteInvocationOptions(RemoteInvocationOptions copy) {
        this.ackTimeoutInMillis = copy.ackTimeoutInMillis;
        this.executionTimeoutInMillis = copy.executionTimeoutInMillis;
    }

    /**
     * Creates a new instance of RemoteInvocationOptions with opinionated defaults.
     * <p>
     * This is equivalent to:
     * <pre>
     *     new RemoteInvocationOptions()
     *      .expectAckWithin(1, TimeUnit.SECONDS)
     *      .expectResultWithin(30, TimeUnit.SECONDS)
     * </pre>
     * 
     * @return RemoteInvocationOptions object
     */
    public static RemoteInvocationOptions defaults() {
        return new RemoteInvocationOptions()
                .expectAckWithin(1, TimeUnit.SECONDS)
                .expectResultWithin(30, TimeUnit.SECONDS);
    }

    public Long getAckTimeoutInMillis() {
        return ackTimeoutInMillis;
    }

    public Long getExecutionTimeoutInMillis() {
        return executionTimeoutInMillis;
    }

    public boolean isAckExpected() {
        return ackTimeoutInMillis != null;
    }

    public boolean isResultExpected() {
        return executionTimeoutInMillis != null;
    }

    /**
     * Defines ACK timeout
     * 
     * @param ackTimeoutInMillis - timeout in milliseconds
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions expectAckWithin(long ackTimeoutInMillis) {
        this.ackTimeoutInMillis = ackTimeoutInMillis;
        return this;
    }

    /**
     * Defines ACK timeout
     * 
     * @param ackTimeout - timeout
     * @param timeUnit - timeout unit
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions expectAckWithin(long ackTimeout, TimeUnit timeUnit) {
        this.ackTimeoutInMillis = timeUnit.toMillis(ackTimeout);
        return this;
    }

    /**
     * Specifies to not wait for ACK reply
     * 
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions noAck() {
        ackTimeoutInMillis = null;
        return this;
    }

    /**
     * Defines execution timeout
     * 
     * @param executionTimeoutInMillis - timeout in milliseconds
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions expectResultWithin(long executionTimeoutInMillis) {
        this.executionTimeoutInMillis = executionTimeoutInMillis;
        return this;
    }

    /**
     * Defines execution timeout
     * 
     * @param executionTimeout - timeout
     * @param timeUnit - timeout unit
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions expectResultWithin(long executionTimeout, TimeUnit timeUnit) {
        this.executionTimeoutInMillis = timeUnit.toMillis(executionTimeout);
        return this;
    }

    /**
     * Specifies to not wait for result
     * 
     * @return RemoteInvocationOptions object
     */
    public RemoteInvocationOptions noResult() {
        executionTimeoutInMillis = null;
        return this;
    }

    @Override
    public String toString() {
        return "RemoteInvocationOptions[" +
                "ackTimeoutInMillis=" + ackTimeoutInMillis +
                ", executionTimeoutInMillis=" + executionTimeoutInMillis +
                ']';
    }
}
