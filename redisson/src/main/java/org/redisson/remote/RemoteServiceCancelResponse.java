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
package org.redisson.remote;

import java.io.Serializable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceCancelResponse implements RRemoteServiceResponse, Serializable {

    private static final long serialVersionUID = -4356901222132702182L;

    private String id;
    private boolean canceled;

    public RemoteServiceCancelResponse() {
    }
    
    public RemoteServiceCancelResponse(String id, boolean canceled) {
        this.canceled = canceled;
        this.id = id;
    }
    
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public String getId() {
        return id;
    }
    
}
