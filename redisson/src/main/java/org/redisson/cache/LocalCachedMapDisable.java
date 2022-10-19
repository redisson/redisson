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
package org.redisson.cache;

import java.io.Serializable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LocalCachedMapDisable implements Serializable {

    private byte[][] keyHashes;
    private long timeout;
    private String requestId;
    
    public LocalCachedMapDisable() {
    }

    public LocalCachedMapDisable(String requestId, byte[][] keyHashes, long timeout) {
        super();
        this.requestId = requestId;
        this.keyHashes = keyHashes;
        this.timeout = timeout;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    public byte[][] getKeyHashes() {
        return keyHashes;
    }
    
}
