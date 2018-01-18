package org.redisson.client.protocol.convertor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class DoubleNullSafeReplayConvertor extends DoubleReplayConvertor {

    @Override
    public Double convert(Object obj) {
        Double r = super.convert(obj);
        if (r == null) {
            return 0.0;
        }
        return r;
    }
    
}
