package org.redisson.remote;

/**
 * Rises when remote method executor has not answered 
 * within Ack timeout.
 * <p/>
 * Method invocation has not been started in this case. 
 * So a new invocation attempt can be made. 
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceAckTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1820133675653636587L;

    public RemoteServiceAckTimeoutException(String message) {
        super(message);
    }
    
}
