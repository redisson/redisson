package org.redisson.executor;

import org.redisson.RedissonAtomicLong;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RMap;
import org.redisson.api.RObject;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.codec.FstCodec;
import org.redisson.codec.JsonJacksonCodec;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class InjectablesInTask implements Runnable, Serializable {

    @RInject
    private RedissonClient redisson;

    @RInject(name = "myCounter")
    private RedissonAtomicLong atomicLong;

    @RInject(name = "myBucket")
    private RBucketAsync bucket;

    @RInject(name = "mySet", codec = FstCodec.class)
    private RSet set;

    @RInject(name = "RMap('myMap')", codec = JsonJacksonCodec.class)
    private Map map;

    public InjectablesInTask() {
    }

    @Override
    public void run() {
        atomicLong.incrementAndGet();
        if (redisson != null) {
            atomicLong.incrementAndGet();
        }
        bucket.setAsync("ABC");
        set.add(1);
        if (FstCodec.class.equals(set.getCodec().getClass())) {
            atomicLong.incrementAndGet();
        }
        if (map instanceof RMap) {
            atomicLong.incrementAndGet();

            RMap m = (RMap) map;
            if ("myMap".equals(m.getName())) {
                atomicLong.incrementAndGet();
                m.put("1", "999");
            }
            if (JsonJacksonCodec.class.isAssignableFrom(m.getCodec().getClass())) {
                atomicLong.incrementAndGet();
            }
        }
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

}
