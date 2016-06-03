package org.redisson.liveobject.resolver;

import org.redisson.liveobject.annotation.RId;

/**
 *
 * @author ruigu
 */
public class FieldValueAsIdGenerator implements Resolver<Object, RId, String>{

    public static final FieldValueAsIdGenerator INSTANCE = new FieldValueAsIdGenerator();
    
    @Override
    public String resolve(Object value, RId index) {
        return value.toString();
    }
    
}
