package org.redisson.spring.transaction;

import org.redisson.api.RTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalBean2 {

    @Autowired
    private RedissonTransactionManager transactionManager;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testInNewTransaction() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("tr2").put("2", "4");
    }
    
    @Transactional
    public void testPropagationRequired() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("tr3").put("2", "4");
    }
    
    @Transactional
    public void testPropagationRequiredWithException() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("tr5").put("2", "4");
        throw new IllegalStateException();
    }
   
}
