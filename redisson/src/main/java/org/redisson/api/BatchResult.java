/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BatchResult<E> {

    private final List<E> responses;
    private final int syncedSlaves;
    
    public BatchResult(List<E> responses, int syncedSlaves) {
        super();
        this.responses = responses;
        this.syncedSlaves = syncedSlaves;
    }
    
    /**
     * Returns list of result objects for each command
     * 
     * @return list of objects
     */
    public List<E> getResponses() {
        return responses;
    }

    /**
     * Returns amount of successfully synchronized slaves during batch execution
     * 
     * @return slaves amount
     */
    public int getSyncedSlaves() {
        return syncedSlaves;
    }

}
