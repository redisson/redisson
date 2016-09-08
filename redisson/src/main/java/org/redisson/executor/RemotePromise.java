/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.executor;

import org.redisson.api.RFuture;
import org.redisson.misc.PromiseDelegator;
import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemotePromise<T> extends PromiseDelegator<T> {

    private String requestId;
    private RFuture<Boolean> addFuture;
    
    public RemotePromise(RPromise<T> promise) {
        super(promise);
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    public String getRequestId() {
        return requestId;
    }
    
    public void setAddFuture(RFuture<Boolean> addFuture) {
        this.addFuture = addFuture;
    }
    public RFuture<Boolean> getAddFuture() {
        return addFuture;
    }
    
    public void doCancel() {
        super.cancel(true);
    }

}
