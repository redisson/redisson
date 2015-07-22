package org.redisson;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLongAsync;
import org.redisson.core.RBatch;
import org.redisson.core.RBlockingQueueAsync;
import org.redisson.core.RBucketAsync;
import org.redisson.core.RDequeAsync;
import org.redisson.core.RHyperLogLogAsync;
import org.redisson.core.RListAsync;
import org.redisson.core.RMapAsync;
import org.redisson.core.RQueueAsync;
import org.redisson.core.RScriptAsync;
import org.redisson.core.RSetAsync;
import org.redisson.core.RTopicAsync;

public class RedissonBatch implements RBatch {

    CommandBatchExecutorService executorService;

    public RedissonBatch(ConnectionManager connectionManager) {
        super();
        this.executorService = new CommandBatchExecutorService(connectionManager);
    }

    @Override
    public <V> RBucketAsync<V> getBucket(String name) {
        return new RedissonBucket<V>(executorService, name);
    }

    @Override
    public <V> RHyperLogLogAsync<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(executorService, name);
    }

    @Override
    public <V> RListAsync<V> getList(String name) {
        return new RedissonList<V>(executorService, name);
    }

    @Override
    public <K, V> RMapAsync<K, V> getMap(String name) {
        return new RedissonMap<K, V>(executorService, name);
    }

    @Override
    public <V> RSetAsync<V> getSet(String name) {
        return new RedissonSet<V>(executorService, name);
    }

    @Override
    public <M> RTopicAsync<M> getTopic(String name) {
        return new RedissonTopic<M>(executorService, name);
    }

    @Override
    public <M> RTopicAsync<M> getTopicPattern(String pattern) {
        return new RedissonTopicPattern<M>(executorService, pattern);
    }

    @Override
    public <V> RQueueAsync<V> getQueue(String name) {
        return new RedissonQueue<V>(executorService, name);
    }

    @Override
    public <V> RBlockingQueueAsync<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<V>(executorService, name);
    }

    @Override
    public <V> RDequeAsync<V> getDequeAsync(String name) {
        return new RedissonDeque<V>(executorService, name);
    }

    @Override
    public RAtomicLongAsync getAtomicLongAsync(String name) {
        return new RedissonAtomicLong(executorService, name);
    }

    @Override
    public RScriptAsync getScript() {
        return new RedissonScript(executorService);
    }

    @Override
    public void execute() {
        executorService.execute();
    }

}
