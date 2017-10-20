/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.executor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RemoteExecutorService {

    Object executeCallable(String className, byte[] classBody, byte[] state, String requestId);
 
    void executeRunnable(String className, byte[] classBody, byte[] state, String requestId);
    
    Object scheduleCallable(String className, byte[] classBody, byte[] state, long startTime, String requestId);
    
    void scheduleRunnable(String className, byte[] classBody, byte[] state, long startTime, String requestId);
    
    void scheduleAtFixedRate(String className, byte[] classBody, byte[] state, long startTime, long period, String executorId, String requestId);
    
    void scheduleWithFixedDelay(String className, byte[] classBody, byte[] state, long startTime, long delay, String executorId, String requestId);
    
    void schedule(String className, byte[] classBody, byte[] state, long startTime, String cronExpression, String executorId, String requestId);
    
}
