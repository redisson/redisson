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
package org.redisson.spring.transaction;

import org.springframework.transaction.support.SmartTransactionObject;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransactionObject implements SmartTransactionObject {

    private boolean isRollbackOnly;
    private RedissonTransactionHolder transactionHolder;

    public RedissonTransactionHolder getTransactionHolder() {
        return transactionHolder;
    }

    public void setTransactionHolder(RedissonTransactionHolder transaction) {
        this.transactionHolder = transaction;
    }

    public void setRollbackOnly(boolean isRollbackOnly) {
        this.isRollbackOnly = isRollbackOnly;
    }
    
    @Override
    public boolean isRollbackOnly() {
        return isRollbackOnly;
    }

    @Override
    public void flush() {
        // skip
    }

}
