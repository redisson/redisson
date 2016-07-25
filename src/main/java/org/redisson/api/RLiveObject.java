/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

//import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface RLiveObject {
//
//    /**
//     * @return the liveObjectLiveMap
//     */
//    public RMap getLiveObjectLiveMap();

    /**
     * Returns the value of the field that has the RId annotation.
     * @return liveObjectId
     */
    Object getLiveObjectId();

    /**
     * Change the value of the field that has the RId annotation. Since the 
     * liveObjectId is encoded as a part of the name of the underlying RMap,
     * this action will result in renaming the underlying RMap based on the
     * naming scheme specified in the REntity annotation of the instance class.
     * 
     * @param liveObjectId the liveObjectId to set
     * @see org.redisson.core.RMap
     */
    void setLiveObjectId(Object liveObjectId);

    /**
     * Returns true if this object holds no other values apart from the field
     * annotated with RId. This involves in invoking the isExist() method on the
     * underlying RMap. Since the field with RId annotation is encoded in the
     * name of the underlying RMap, so to ensure the map exist in redis, set a 
     * non null value to any of the other fields.
     * 
     * @return 
     * @see org.redisson.core.RMap
     */
    boolean isExists();
    
    /**
     * Deletes the underlying RMap.
     * @return 
     */
    boolean delete();
    
}
