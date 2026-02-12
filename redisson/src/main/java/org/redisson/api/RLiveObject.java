/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface RLiveObject extends RExpirable {

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
     * @see org.redisson.api.RMap
     */
    void setLiveObjectId(Object liveObjectId);

    RMap getLiveObjectLiveMap();

}
