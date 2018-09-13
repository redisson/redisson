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
package org.redisson.api;

import java.io.Serializable;
import java.util.Map;

/**
 * Result object for pending messages request.
 * 
 * @author Nikita Koksharov
 *
 */
public class PendingResult implements Serializable {

    private static final long serialVersionUID = -5525031552305408248L;
    
    private long total;
    private StreamId lowestId;
    private StreamId highestId;
    private Map<String, Long> consumerNames;
    
    public PendingResult() {
    }
    
    public PendingResult(long total, StreamId lowestId, StreamId highestId, Map<String, Long> consumerNames) {
        super();
        this.total = total;
        this.lowestId = lowestId;
        this.highestId = highestId;
        this.consumerNames = consumerNames;
    }

    /**
     * Total amount of pending messages
     * 
     * @return number
     */
    public long getTotal() {
        return total;
    }

    /**
     * Lowest stream id of pending messages
     * 
     * @return number
     */
    public StreamId getLowestId() {
        return lowestId;
    }

    /**
     * Highest stream id of pending messages
     * 
     * @return number
     */
    public StreamId getHighestId() {
        return highestId;
    }

    /**
     * Pending messages amount mapped by consumer name
     * 
     * @return map
     */
    public Map<String, Long> getConsumerNames() {
        return consumerNames;
    }
    
}
