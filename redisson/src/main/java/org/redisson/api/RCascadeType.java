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
 * Live Object cascade type.
 * 
 * @author Nikita Koksharov
 *
 */
public enum RCascadeType {

    /**
     * Includes all cascade types.
     */
    ALL,
    
    /**
     * Cascade persist operation during {@link RLiveObjectService#persist} method invocation. 
     */
    PERSIST,
    
    /**
     * Cascade detach operation during {@link RLiveObjectService#detach} method invocation. 
     */
    DETACH,
    
    /**
     * Cascade merge operation during {@link RLiveObjectService#merge} method invocation. 
     */
    MERGE,
    
    /**
     * Cascade delete operation during {@link RLiveObjectService#delete} method invocation. 
     */
    DELETE
    
    
}
