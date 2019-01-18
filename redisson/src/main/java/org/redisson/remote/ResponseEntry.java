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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ResponseEntry {

    public static class Result {
        
        private final RPromise<? extends RRemoteServiceResponse> promise;
        private final ScheduledFuture<?> scheduledFuture;
        
        public Result(RPromise<? extends RRemoteServiceResponse> promise, ScheduledFuture<?> scheduledFuture) {
            super();
            this.promise = promise;
            this.scheduledFuture = scheduledFuture;
        }
        
        public <T extends RRemoteServiceResponse> RPromise<T> getPromise() {
            return (RPromise<T>) promise;
        }
        
        public ScheduledFuture<?> getScheduledFuture() {
            return scheduledFuture;
        }
        
    }
    
    private final Map<RequestId, List<Result>> responses = new HashMap<RequestId, List<Result>>();
    private final AtomicBoolean started = new AtomicBoolean(); 
    
    public Map<RequestId, List<Result>> getResponses() {
        return responses;
    }
    
    public AtomicBoolean getStarted() {
        return started;
    }
    
}
