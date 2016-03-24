package org.redisson.remote;

/**
 * Rises when invocation timeout has been occurred
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceTimeoutException extends RuntimeException {

    private static final long serialVersionUID = -1749266931994840256L;

    public RemoteServiceTimeoutException(String message) {
        super(message);
    }
    
}
