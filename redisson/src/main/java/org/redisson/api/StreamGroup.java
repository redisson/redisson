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
 * Object containing details about Stream Group
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamGroup {

    private final String name;
    private final int consumers;
    private final int pending;
    private final StreamMessageId lastDeliveredId;
    
    public StreamGroup(String name, int consumers, int pending, StreamMessageId lastDeliveredId) {
        this.name = name;
        this.consumers = consumers;
        this.pending = pending;
        this.lastDeliveredId = lastDeliveredId;
    }
    
    /**
     * Returns last delivered StreamMessageId for this group
     * 
     * @return StreamMessageId object
     */
    public StreamMessageId getLastDeliveredId() {
        return lastDeliveredId;
    }
    
    /**
     * Returns current customers amount for this group
     * 
     * @return customers amount
     */
    public int getConsumers() {
        return consumers;
    }
    
    /**
     * Returns name of this group
     * 
     * @return name of group
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns amount of pending messages for this group
     * 
     * @return amount of pending messages 
     */
    public int getPending() {
        return pending;
    }
    
}
