package org.redisson.spring.transaction;

import org.junit.Assert;
import org.redisson.api.RMapReactive;
import org.redisson.api.RTransactionReactive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveTransactionalBean {

    @Autowired
    private ReactiveRedissonTransactionManager transactionManager;
    
    @Autowired
    private ReactiveTransactionalBean2 transactionalBean2;
    
    @Transactional
    public Mono<Void> testTransactionIsNotNull() {
        Mono<RTransactionReactive> reactiveTransaction = transactionManager.getCurrentTransaction();
        return reactiveTransaction.map(t -> {
           if (t == null) {
               throw new RuntimeException("transaction can't be null");
           }
           return t;
        }).then();
    }

    public Mono<Void> testNoTransaction() {
        Mono<RTransactionReactive> reactiveTransaction = transactionManager.getCurrentTransaction();
        return reactiveTransaction.onErrorResume(e -> {
            if (e instanceof NoTransactionException) {
                return Mono.empty();
            }
            return Mono.error(e);
        }).then();
    }

    @Transactional
    public Mono<Void> testCommit() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("test1");
            return map.put("1", "2");
        }).then();
    }
    
    @Transactional
    public Mono<Void> testRollback() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("test2");
            return map.put("1", "2");
        }).doOnSuccess(v -> {
            throw new IllegalStateException();
        }).then();
    }

    @Transactional
    public Mono<Void> testCommitAfterRollback() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("test2");
            return map.put("1", "2");
        }).then();
    }

    @Transactional
    public Mono<Void> testNestedNewTransaction() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("tr1");
            return map.put("1", "0");
        }).flatMap(v -> {
            return transactionalBean2.testInNewTransaction();
        });
    }

    @Transactional
    public Mono<Void> testPropagationRequired() {
        return transactionalBean2.testPropagationRequired();
    }

    @Transactional
    public Mono<Void> testPropagationRequiredWithException() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("tr4");
            return map.put("1", "0");
        }).then(transactionalBean2.testPropagationRequiredWithException());
    }


}
