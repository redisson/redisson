package org.redisson.spring.transaction;

import org.redisson.api.RTransaction;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransactionHolder {

    private RTransaction transaction;

    public RTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(RTransaction transaction) {
        this.transaction = transaction;
    }
    
}
