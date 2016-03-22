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
package org.redisson.core;

import java.util.concurrent.TimeUnit;

public interface RRemoteService {

    /**
     * Register remote service with single executor
     * 
     * @param remoteInterface
     * @param object
     */
    <T> void register(Class<T> remoteInterface, T object);
    
    /**
     * Register remote service with custom executors amount
     * 
     * @param remoteInterface
     * @param object
     * @param executorsAmount
     */
    <T> void register(Class<T> remoteInterface, T object, int executorsAmount);
    
    /**
     * Get remote service object for remote invocations
     * 
     * @param remoteInterface
     * @return
     */
    <T> T get(Class<T> remoteInterface);
    
    /**
     * Get remote service object for remote invocations 
     * with specified timeout invocation
     * 
     * @param remoteInterface
     * @param timeout - timeout invocation
     * @param timeUnit
     * @return
     */
    <T> T get(Class<T> remoteInterface, int timeout, TimeUnit timeUnit);
    
}
