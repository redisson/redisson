/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ResponseEntry {

    public static class Result {

        private final CompletableFuture<? extends RRemoteServiceResponse> promise;
        private final ScheduledFuture<?> responseTimeoutFuture;
        
        public Result(CompletableFuture<? extends RRemoteServiceResponse> promise, ScheduledFuture<?> responseTimeoutFuture) {
            super();
            this.promise = promise;
            this.responseTimeoutFuture = responseTimeoutFuture;
        }
        
        public <T extends RRemoteServiceResponse> CompletableFuture<T> getPromise() {
            return (CompletableFuture<T>) promise;
        }
        
        public ScheduledFuture<?> getResponseTimeoutFuture() {
            return responseTimeoutFuture;
        }
        
    }
    
    private final Map<String, List<Result>> responses = new HashMap<String, List<Result>>();
    private final AtomicBoolean started = new AtomicBoolean(); 
    
    public Map<String, List<Result>> getResponses() {
        return responses;
    }
    
    public AtomicBoolean getStarted() {
        return started;
    }
    
}
