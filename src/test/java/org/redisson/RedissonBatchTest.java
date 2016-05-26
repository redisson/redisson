package org.redisson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.core.RBatch;
import org.redisson.core.RListAsync;
import org.redisson.core.RScript;
import org.redisson.core.RScript.Mode;

import io.netty.util.concurrent.Future;

public class RedissonBatchTest extends BaseTest {

//    @Test
    public void testBatchRedirect() {
        RBatch batch = redisson.createBatch();
        for (int i = 0; i < 5; i++) {
            batch.getMap("" + i).fastPutAsync("" + i, i);
        }
        batch.execute();

        batch = redisson.createBatch();
        for (int i = 0; i < 1; i++) {
            batch.getMap("" + i).sizeAsync();
            batch.getMap("" + i).containsValueAsync("" + i);
            batch.getMap("" + i).containsValueAsync(i);
        }
        List<?> t = batch.execute();
        System.out.println(t);
    }
    
    @Test
    public void testBatchNPE() {
        RBatch batch = redisson.createBatch();
        batch.getBucket("A1").setAsync("001");
        batch.getBucket("A2").setAsync("001");
        batch.getBucket("A3").setAsync("001");
        batch.getKeys().deleteAsync("A1");
        batch.getKeys().deleteAsync("A2");
        List result = batch.execute();
    }

    @Test
    public void testDifferentCodecs() {
        RBatch b = redisson.createBatch();
        b.getMap("test1").putAsync("1", "2");
        b.getMap("test2", StringCodec.INSTANCE).putAsync("21", "3");
        Future<Object> val1 = b.getMap("test1").getAsync("1");
        Future<Object> val2 = b.getMap("test2", StringCodec.INSTANCE).getAsync("21");
        b.execute();

        Assert.assertEquals("2", val1.getNow());
        Assert.assertEquals("3", val2.getNow());
    }

    @Test
    public void testBatchList() {
        RBatch b = redisson.createBatch();
        RListAsync<Integer> listAsync = b.getList("list");
        for (int i = 1; i < 540; i++) {
            listAsync.addAsync(i);
        }
        List<?> res = b.execute();
        Assert.assertEquals(539, res.size());
    }

    @Test
    public void testBatchBigRequest() {
        RBatch batch = redisson.createBatch();
        for (int i = 0; i < 210; i++) {
            batch.getMap("test").fastPutAsync("1", "2");
            batch.getMap("test").fastPutAsync("2", "3");
            batch.getMap("test").putAsync("2", "5");
            batch.getAtomicLong("counter").incrementAndGetAsync();
            batch.getAtomicLong("counter").incrementAndGetAsync();
        }
        List<?> res = batch.execute();
        Assert.assertEquals(210*5, res.size());
    }

    @Test(expected=RedisException.class)
    public void testExceptionHandling() {
        RBatch batch = redisson.createBatch();
        batch.getMap("test").putAsync("1", "2");
        batch.getScript().evalAsync(Mode.READ_WRITE, "wrong_code", RScript.ReturnType.VALUE);
        batch.execute();
    }

    @Test(expected=IllegalStateException.class)
    public void testTwice() {
        RBatch batch = redisson.createBatch();
        batch.getMap("test").putAsync("1", "2");
        batch.execute();
        batch.execute();
    }


    @Test
    public void testEmpty() {
        RBatch batch = redisson.createBatch();
        batch.execute();
    }
    
    @Test
    public void testOrdering() throws InterruptedException {
        ExecutorService e = Executors.newFixedThreadPool(16);
        final RBatch batch = redisson.createBatch();
        final AtomicLong index = new AtomicLong(-1);
        final List<Future<Long>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 500; i++) {
            futures.add(null);
        }
        for (int i = 0; i < 500; i++) {
            final int j = i;
            e.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (RedissonBatchTest.this) {
                        int i = (int) index.incrementAndGet();
                        int ind = j % 3;
                        Future<Long> f1 = batch.getAtomicLong("test" + ind).addAndGetAsync(j);
                        futures.set(i, f1);
                    }
                }
            });
        }
        e.shutdown();
        Assert.assertTrue(e.awaitTermination(30, TimeUnit.SECONDS));
        List<?> s = batch.execute();
        
        int i = 0;
        for (Object element : s) {
            Future<Long> a = futures.get(i);
            Assert.assertEquals(a.getNow(), element);
            i++;
        }
    }

    @Test
    public void test() {
        RBatch batch = redisson.createBatch();
        batch.getMap("test").fastPutAsync("1", "2");
        batch.getMap("test").fastPutAsync("2", "3");
        batch.getMap("test").putAsync("2", "5");
        batch.getAtomicLong("counter").incrementAndGetAsync();
        batch.getAtomicLong("counter").incrementAndGetAsync();

        List<?> res = batch.execute();
        Assert.assertEquals(5, res.size());
        Assert.assertTrue((Boolean)res.get(0));
        Assert.assertTrue((Boolean)res.get(1));
        Assert.assertEquals("3", res.get(2));
        Assert.assertEquals(1L, res.get(3));
        Assert.assertEquals(2L, res.get(4));

        Map<String, String> map = new HashMap<String, String>();
        map.put("1", "2");
        map.put("2", "5");
        Assert.assertEquals(map, redisson.getMap("test"));

        Assert.assertEquals(redisson.getAtomicLong("counter").get(), 2);
    }

}
