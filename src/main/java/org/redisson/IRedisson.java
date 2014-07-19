package org.redisson;

import org.redisson.core.*;

public interface IRedisson {
    <V> RBucket<V> getBucket(String name);

    <V> RHyperLogLog<V> getHyperLogLog(String name);

    <V> RList<V> getList(String name);

    <K, V> RMap<K, V> getMap(String name);

    RLock getLock(String name);

    <V> RSet<V> getSet(String name);

    <V> RSortedSet<V> getSortedSet(String name);

    <M> RTopic<M> getTopic(String name);

    <V> RQueue<V> getQueue(String name);

    <V> RDeque<V> getDeque(String name);

    RAtomicLong getAtomicLong(String name);

    RCountDownLatch getCountDownLatch(String name);
}
