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
package org.redisson.executor;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemotePromise<T> extends CompletableFuture<T> {

    private final String requestId;
    private CompletableFuture<Boolean> addFuture;
    
    public RemotePromise(String requestId) {
        super();
        this.requestId = requestId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setAddFuture(CompletableFuture<Boolean> addFuture) {
        this.addFuture = addFuture;
    }
    public CompletableFuture<Boolean> getAddFuture() {
        return addFuture;
    }
    
    public void doCancel(boolean mayInterruptIfRunning) {
        super.cancel(mayInterruptIfRunning);
    }

    public CompletableFuture<Boolean> cancelAsync(boolean mayInterruptIfRunning) {
        return CompletableFuture.completedFuture(false);
    }

}
