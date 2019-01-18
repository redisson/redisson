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
package org.redisson.transaction;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BaseTransactionalObject {

    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("expire method is not supported in transaction");
    }
    
    public RFuture<Boolean> expireAtAsync(Date timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    public RFuture<Boolean> clearExpireAsync() {
        throw new UnsupportedOperationException("clearExpire method is not supported in transaction");
    }
    
    public RFuture<Boolean> moveAsync(int database) {
        throw new UnsupportedOperationException("move method is not supported in transaction");
    }
    
    public RFuture<Void> migrateAsync(String host, int port, int database) {
        throw new UnsupportedOperationException("migrate method is not supported in transaction");
    }

    
}
