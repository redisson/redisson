package org.redisson.spring.transaction;

import org.redisson.api.RTransactionReactive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

public class ReactiveTransactionalBean2 {

    @Autowired
    private ReactiveRedissonTransactionManager transactionManager;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<Void> testInNewTransaction() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            return t.getMap("tr2").put("2", "4").then();
        });
    }
    
    @Transactional
    public Mono<Void> testPropagationRequired() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            return t.getMap("tr3").put("2", "4").then();
        });
    }

    @Transactional
    public Mono<Void> testPropagationRequiredWithException() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            return t.getMap("tr5").put("2", "4");
        }).doOnSuccess(v -> {
            throw new IllegalStateException();
        }).then();
    }


}
