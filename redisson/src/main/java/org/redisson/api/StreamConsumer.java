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
 * Object containing details about Stream Consumer
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamConsumer {

    private final String name;
    private final int pending;
    private final long idleTime;
    
    public StreamConsumer(String name, int pending, long idleTime) {
        this.name = name;
        this.pending = pending;
        this.idleTime = idleTime;
    }

    /**
     * Returns amount of pending messages for this consumer
     * 
     * @return amount of pending messages 
     */
    public int getPending() {
        return pending;
    }
    
    /**
     * Returns name of this consumer
     * 
     * @return name of consumer
     */
    public String getName() {
        return name;
    }

    /**
     * Returns idle time in milliseconds since which this consumer hasn't consumed messages 
     * 
     * @return idle time in milliseconds
     */
    public long getIdleTime() {
        return idleTime;
    }
    
}
