package org.redisson;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReferenceReactiveTest extends BaseReactiveTest {

    @Test
    public void test() throws InterruptedException {
        RBucketReactive<Object> b1 = redisson.getBucket("b1");
        RBucketReactive<Object> b2 = redisson.getBucket("b2");
        RBucketReactive<Object> b3 = redisson.getBucket("b3");
        sync(b2.set(b3));
        sync(b1.set(redisson.getBucket("b2")));
        Assertions.assertTrue(sync(b1.get()) instanceof RBucketReactive);
        Assertions.assertEquals("b3", ((RBucketReactive) sync(((RBucketReactive) sync(b1.get())).get())).getName());
        RBucketReactive<Object> b4 = redisson.getBucket("b4");
        sync(b4.set(redisson.getMapCache("testCache")));
        Assertions.assertTrue(sync(b4.get()) instanceof RMapCacheReactive);
        sync(((RMapCacheReactive) sync(b4.get())).fastPut(b1, b2));
        Assertions.assertEquals("b2", ((RBucketReactive) sync(((RMapCacheReactive) sync(b4.get())).get(b1))).getName());
    }

    @Test
    public void testBatch() throws InterruptedException {
        RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
        RBucketReactive<Object> b1 = batch.getBucket("b1");
        RBucketReactive<Object> b2 = batch.getBucket("b2");
        RBucketReactive<Object> b3 = batch.getBucket("b3");
        b2.set(b3);
        b1.set(b2);
        b3.set(b1);
        sync(batch.execute());

        batch = redisson.createBatch(BatchOptions.defaults());
        batch.getBucket("b1").get();
        batch.getBucket("b2").get();
        batch.getBucket("b3").get();
        List<RBucketReactive> result = (List<RBucketReactive>) sync(batch.execute()).getResponses();
        Assertions.assertEquals("b2", result.get(0).getName());
        Assertions.assertEquals("b3", result.get(1).getName());
        Assertions.assertEquals("b1", result.get(2).getName());
    }

    @Test
    public void testReactiveToNormal() throws InterruptedException {
        RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
        RBucketReactive<Object> b1 = batch.getBucket("b1");
        RBucketReactive<Object> b2 = batch.getBucket("b2");
        RBucketReactive<Object> b3 = batch.getBucket("b3");
        b2.set(b3);
        b1.set(b2);
        b3.set(b1);
        sync(batch.execute());

        RedissonClient lredisson = Redisson.create(redisson.getConfig());
        RBatch b = lredisson.createBatch(BatchOptions.defaults());
        b.getBucket("b1").getAsync();
        b.getBucket("b2").getAsync();
        b.getBucket("b3").getAsync();
        List<RBucket> result = (List<RBucket>)b.execute().getResponses();
        Assertions.assertEquals("b2", result.get(0).getName());
        Assertions.assertEquals("b3", result.get(1).getName());
        Assertions.assertEquals("b1", result.get(2).getName());

        lredisson.shutdown();
    }

    @Test
    public void shouldUseDefaultCodec() {
        Kryo5Codec codec = new Kryo5Codec();

        Config config = createConfig();
        config.setCodec(codec);

        RedissonReactiveClient reactive = Redisson.create(config).reactive();
        RBucketReactive<Object> b1 = reactive.getBucket("b1");
        sync(b1.set(new MyObject()));
        RSetReactive<Object> s1 = reactive.getSet("s1");
        Assertions.assertTrue(sync(s1.add(b1)));
        Assertions.assertTrue(codec == b1.getCodec());

        RedissonReactiveClient reactive1 = Redisson.create(config).reactive();

        RSetReactive<RBucketReactive> s2 = reactive1.getSet("s1");
        RBucketReactive<MyObject> b2 = sync(s2.iterator(1));
        Assertions.assertTrue(codec == b2.getCodec());
        Assertions.assertTrue(sync(b2.get()) instanceof MyObject);
        reactive.shutdown();
        reactive1.shutdown();
    }

    public static class MyObject {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }
}
