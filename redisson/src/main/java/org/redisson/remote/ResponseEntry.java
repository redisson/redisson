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
package org.redisson.remote;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.misc.RPromise;

import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ResponseEntry {

    private final ConcurrentMap<String, RPromise<? extends RRemoteServiceResponse>> responses = PlatformDependent.newConcurrentHashMap();
    private final ConcurrentMap<String, ScheduledFuture<?>> timeouts = PlatformDependent.newConcurrentHashMap();
    private final AtomicBoolean started = new AtomicBoolean(); 
    
    
    public ConcurrentMap<String, ScheduledFuture<?>> getTimeouts() {
        return timeouts;
    }
    
    public ConcurrentMap<String, RPromise<? extends RRemoteServiceResponse>> getResponses() {
        return responses;
    }
    
    public AtomicBoolean getStarted() {
        return started;
    }
    
}
