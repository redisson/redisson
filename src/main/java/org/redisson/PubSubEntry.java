package org.redisson;

import io.netty.util.concurrent.Promise;

public interface PubSubEntry<E> {

    void aquire();

    int release();

    Promise<E> getPromise();

}
