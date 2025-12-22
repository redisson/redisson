package transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.redisson.api.RTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalBean {

    @Autowired
    private RedissonTransactionManager transactionManager;
    
    @Autowired
    private TransactionalBean2 transactionalBean2;
    
    @Transactional
    public void testTransactionIsNotNull() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        assertThat(transaction).isNotNull();
    }

    public void testNoTransaction() {
        try {
            RTransaction transaction = transactionManager.getCurrentTransaction();
            Assertions.fail();
        } catch (NoTransactionException e) {
            // skip
        }
    }
    
    @Transactional
    public void testCommit() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("test1").put("1", "2");
    }
    
    @Transactional
    public void testRollback() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("test2").put("1", "2");
        throw new IllegalStateException();
    }

    @Transactional
    public void testCommitAfterRollback() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("test2").put("1", "2");
    }

    @Transactional
    public void testNestedNewTransaction() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("tr1").put("1", "0");
        
        transactionalBean2.testInNewTransaction();
    }
    
    @Transactional
    public void testPropagationRequired() {
        transactionalBean2.testPropagationRequired();
    }
    
    @Transactional
    public void testPropagationRequiredWithException() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        transaction.getMap("tr4").put("1", "0");

        transactionalBean2.testPropagationRequiredWithException();
    }
    
}
