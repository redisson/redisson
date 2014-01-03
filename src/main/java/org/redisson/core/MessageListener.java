package org.redisson.core;

import java.util.EventListener;

public interface MessageListener<M> extends EventListener {

    void onMessage(M msg);

}
