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

/**
 * Entry object for pending messages request.
 * 
 * @author Nikita Koksharov
 *
 */
public class PendingEntry {
    
    private StreamMessageId id;
    private String consumerName;
    private long idleTime;
    private long lastTimeDelivered;
    
    public PendingEntry(StreamMessageId id, String consumerName, long idleTime, long lastTimeDelivered) {
        super();
        this.id = id;
        this.consumerName = consumerName;
        this.idleTime = idleTime;
        this.lastTimeDelivered = lastTimeDelivered;
    }
    
    /**
     * Returns stream id of message
     * 
     * @return id
     */
    public StreamMessageId getId() {
        return id;
    }

    /**
     * Returns name of consumer
     * 
     * @return id
     */
    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Returns milliseconds amount have passed since the last time 
     * the message was delivered to some consumer
     * 
     * @return number
     */
    public long getIdleTime() {
        return idleTime;
    }

    /**
     * Returns number of times that a given message was delivered
     * 
     * @return number
     */
    public long getLastTimeDelivered() {
        return lastTimeDelivered;
    }
    
}
