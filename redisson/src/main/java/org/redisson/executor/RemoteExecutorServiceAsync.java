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
package org.redisson.executor;

import org.redisson.api.RFuture;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.executor.params.ScheduledAtFixedRateParameters;
import org.redisson.executor.params.ScheduledCronExpressionParameters;
import org.redisson.executor.params.ScheduledParameters;
import org.redisson.executor.params.ScheduledWithFixedDelayParameters;
import org.redisson.executor.params.TaskParameters;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@RRemoteAsync(RemoteExecutorService.class)
public interface RemoteExecutorServiceAsync {

    <T> RFuture<T> executeCallable(TaskParameters params);
    
    RFuture<Void> executeRunnable(TaskParameters params);
    
    <T> RFuture<T> scheduleCallable(ScheduledParameters params);
    
    RFuture<Void> scheduleRunnable(ScheduledParameters params);
    
    RFuture<Void> scheduleAtFixedRate(ScheduledAtFixedRateParameters params);
    
    RFuture<Void> scheduleWithFixedDelay(ScheduledWithFixedDelayParameters params);

    RFuture<Void> schedule(ScheduledCronExpressionParameters params);
    
}
