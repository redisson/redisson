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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.Node.InfoSection;
import org.redisson.client.protocol.Time;

/**
 * Redis node interface
 *
 * @author Nikita Koksharov
 *
 */
public interface NodeAsync {

    RFuture<Map<String, String>> infoAsync(InfoSection section);
    
    RFuture<Time> timeAsync();
    
    RFuture<Boolean> pingAsync();

    /**
     * Ping Redis node with specified timeout.
     *
     * @param timeout - ping timeout
     * @param timeUnit - timeout unit
     * @return <code>true</code> if "PONG" reply received, <code>false</code> otherwise
     */
    RFuture<Boolean> pingAsync(long timeout, TimeUnit timeUnit);
    
    RFuture<Map<String, String>> clusterInfoAsync();
    
}
