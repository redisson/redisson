package org.redisson.core;


public interface RTopic<M> {

    /**
     * Publish the message to all subscribers of this topic
     *
     * @param message
     */
    void publish(M message);

    int addListener(MessageListener<M> listener);

    void removeListener(int listenerId);


}
