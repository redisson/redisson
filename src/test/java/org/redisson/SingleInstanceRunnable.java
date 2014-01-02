package org.redisson;

public interface SingleInstanceRunnable {

    void run(Redisson redisson);

}
