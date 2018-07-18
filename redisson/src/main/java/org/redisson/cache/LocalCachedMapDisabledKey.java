/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.cache;

import java.io.Serializable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LocalCachedMapDisabledKey implements Serializable {

    private String requestId;
    private long timeout;
    
    public LocalCachedMapDisabledKey() {
    }
    
    public LocalCachedMapDisabledKey(String requestId, long timeout) {
        super();
        this.requestId = requestId;
        this.timeout = timeout;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
}
