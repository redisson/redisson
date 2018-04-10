/**
 * Copyright 2018 Nikita Koksharov
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

import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RedissonTransactionManager extends AbstractPlatformTransactionManager implements ResourceTransactionManager {

    private RedissonClient redisson;
    
    @Override
    protected Object doGetTransaction() throws TransactionException {
        RedissonTransactionObject tObject = new RedissonTransactionObject();
        
        TransactionSynchronizationManager.getResource(redisson);
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        // TODO Auto-generated method stub
        return super.isExistingTransaction(transaction);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        RedissonTransactionObject tObject = (RedissonTransactionObject) transaction;
        
        if (tObject.getBatch() == null) {
            RBatch batch = redisson.createBatch();
            batch.atomic();
            tObject.setBatch(batch);
        }
        
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Object getResourceFactory() {
        return redisson;
    }

}
