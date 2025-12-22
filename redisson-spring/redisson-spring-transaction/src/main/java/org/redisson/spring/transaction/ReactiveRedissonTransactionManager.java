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
package org.redisson.spring.transaction;

import org.redisson.api.RTransactionReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.TransactionOptions;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager;
import org.springframework.transaction.reactive.GenericReactiveTransaction;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ReactiveRedissonTransactionManager extends AbstractReactiveTransactionManager {

    private final RedissonReactiveClient redissonClient;

    public ReactiveRedissonTransactionManager(RedissonReactiveClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public Mono<RTransactionReactive> getCurrentTransaction() {
        return TransactionSynchronizationManager.forCurrentTransaction().map(manager -> {
            ReactiveRedissonResourceHolder holder = (ReactiveRedissonResourceHolder) manager.getResource(redissonClient);
            if (holder == null) {
                throw new NoTransactionException("No transaction is available for the current thread");
            } else {
                return holder.getTransaction();
            }
        });
    }


    @Override
    protected Object doGetTransaction(TransactionSynchronizationManager synchronizationManager) throws TransactionException {
        ReactiveRedissonTransactionObject transactionObject = new ReactiveRedissonTransactionObject();

        ReactiveRedissonResourceHolder holder = (ReactiveRedissonResourceHolder) synchronizationManager.getResource(redissonClient);
        transactionObject.setResourceHolder(holder);
        return transactionObject;
    }

    @Override
    protected Mono<Void> doBegin(TransactionSynchronizationManager synchronizationManager, Object transaction, TransactionDefinition definition) throws TransactionException {
        ReactiveRedissonTransactionObject tObject = (ReactiveRedissonTransactionObject) transaction;

        TransactionOptions options = TransactionOptions.defaults();
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
            options.timeout(definition.getTimeout(), TimeUnit.SECONDS);
        }

        RTransactionReactive trans = redissonClient.createTransaction(options);
        ReactiveRedissonResourceHolder holder = new ReactiveRedissonResourceHolder();
        holder.setTransaction(trans);
        tObject.setResourceHolder(holder);
        synchronizationManager.bindResource(redissonClient, holder);

        return Mono.empty();
    }

    @Override
    protected Mono<Void> doCommit(TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) throws TransactionException {
        ReactiveRedissonTransactionObject to = (ReactiveRedissonTransactionObject) status.getTransaction();
        return to.getResourceHolder().getTransaction().commit().onErrorMap(ex -> {
            return new TransactionSystemException("Unable to commit transaction " + to.getResourceHolder().getTransaction(), ex);
        });
    }

    @Override
    protected Mono<Void> doRollback(TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) throws TransactionException {
        ReactiveRedissonTransactionObject to = (ReactiveRedissonTransactionObject) status.getTransaction();
        return to.getResourceHolder().getTransaction().rollback().onErrorMap(ex -> {
            return new TransactionSystemException("Unable to rollback transaction", ex);
        });
    }

    @Override
    protected Mono<Object> doSuspend(TransactionSynchronizationManager synchronizationManager, Object transaction) throws TransactionException {
        return Mono.fromSupplier(() -> {
            ReactiveRedissonTransactionObject to = (ReactiveRedissonTransactionObject) transaction;
            to.setResourceHolder(null);
            return synchronizationManager.unbindResource(redissonClient);
        });
    }

    @Override
    protected Mono<Void> doResume(TransactionSynchronizationManager synchronizationManager, Object transaction, Object suspendedResources) throws TransactionException {
        return Mono.fromRunnable(() -> {
            synchronizationManager.bindResource(redissonClient, suspendedResources);
        });
    }

    @Override
    protected Mono<Void> doSetRollbackOnly(TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) throws TransactionException {
        return Mono.fromRunnable(() -> {
            ReactiveRedissonTransactionObject to = (ReactiveRedissonTransactionObject) status.getTransaction();
            to.getResourceHolder().setRollbackOnly();
        });
    }

    @Override
    protected Mono<Void> doCleanupAfterCompletion(TransactionSynchronizationManager synchronizationManager, Object transaction) {
        return Mono.fromRunnable(() -> {
            synchronizationManager.unbindResource(redissonClient);
            ReactiveRedissonTransactionObject to = (ReactiveRedissonTransactionObject) transaction;
            to.getResourceHolder().setTransaction(null);
        });
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        ReactiveRedissonTransactionObject transactionObject = (ReactiveRedissonTransactionObject) transaction;
        return transactionObject.getResourceHolder() != null;
    }
}
