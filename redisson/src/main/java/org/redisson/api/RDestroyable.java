package org.redisson.api;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RDestroyable {

    /**
     * Allows to destroy object then it's not necessary anymore.
     */
    void destroy();
    
}
