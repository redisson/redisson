package org.redisson.transaction;

import org.redisson.api.RMap;
import org.redisson.api.RTransaction;

public class RedissonTransactionalMapTest extends RedissonBaseTransactionalMapTest {

    @Override
    protected RMap<String, String> getMap() {
        return redisson.getMap("test");
    }

    @Override
    protected RMap<String, String> getTransactionalMap(RTransaction transaction) {
        return transaction.getMap("test");
    }

    
}
