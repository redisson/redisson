package org.redisson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.ChannelName;

import io.netty.util.concurrent.Future;

public class TimeoutTest extends BaseTest {

//    @Test
    public void testBrokenSlave() throws InterruptedException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        for (int i = 0; i < 1000; i++) {
            map.put(i, i * 1000);
            Thread.sleep(1000);
            map.get(i);
            System.out.println(i);
        }
    }

//    @Test
    public void testPubSub() throws InterruptedException, ExecutionException {
        RTopic topic = redisson.getTopic("simple");
        topic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                System.out.println("msg: " + msg);
            }
        });
        for (int i = 0; i < 100; i++) {
            Thread.sleep(1000);
            topic.publish("test" + i);
        }
    }


//    @Test
    public void testReplaceTimeout() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        for (int i = 0; i < 1000; i++) {
            map.put(i, i * 1000);
            map.replace(i, i * 1000 + 1);
            Thread.sleep(1000);
            System.out.println(i);
        }

        for (int i = 0; i < 1000; i++) {
            Integer r = map.get(i);
            System.out.println(r);
        }
    }

//    @Test
    public void testPutAsyncTimeout() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        List<RFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            RFuture<Integer> future = map.putAsync(i, i*1000);
            Thread.sleep(1000);
            futures.add(future);
            System.out.println(i);
        }

        for (RFuture<Integer> future : futures) {
            future.get();
        }

        for (int i = 0; i < 10; i++) {
            Integer r = map.get(i);
            System.out.println(r);
        }
    }

//    @Test
    public void testGetAsyncTimeout() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        List<RFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            map.put(i, i*1000);
        }

        for (int i = 0; i < 10; i++) {
            RFuture<Integer> future = map.getAsync(i);
            Thread.sleep(1000);
            System.out.println(i);
            futures.add(future);
        }

        for (RFuture<Integer> future : futures) {
            Integer res = future.get();
            System.out.println(res);
        }

    }

}
