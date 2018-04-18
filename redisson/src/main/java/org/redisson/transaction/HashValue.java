package org.redisson.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class HashValue {

    private final AtomicInteger counter = new AtomicInteger();
    private final List<byte[]> keyIds = new ArrayList<byte[]>();
    
    public HashValue() {
    }
    
    public AtomicInteger getCounter() {
        return counter;
    }
    
    public List<byte[]> getKeyIds() {
        return keyIds;
    }
    
}
