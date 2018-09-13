package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.api.PendingEntry;
import org.redisson.api.PendingResult;
import org.redisson.api.RStream;
import org.redisson.api.StreamId;

public class RedissonStreamTest extends BaseTest {

    @Test
    public void testClaim() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add("0", "0");
        
        stream.createGroup("testGroup");
        
        StreamId id1 = stream.add("1", "1");
        StreamId id2 = stream.add("2", "2");
        
        Map<StreamId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1");
        assertThat(s.size()).isEqualTo(2);
        
        StreamId id3 = stream.add("3", "33");
        StreamId id4 = stream.add("4", "44");
        
        Map<StreamId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2");
        assertThat(s2.size()).isEqualTo(2);
        
        Map<StreamId, Map<String, String>> res = stream.claimPending("testGroup", "consumer1", 1, TimeUnit.MILLISECONDS, id3, id4);
        assertThat(res.size()).isEqualTo(2);
        assertThat(res.keySet()).containsExactly(id3, id4);
        for (Map<String, String> map : res.values()) {
            assertThat(map.keySet()).containsAnyOf("3", "4");
            assertThat(map.values()).containsAnyOf("33", "44");
        }
    }
    
    @Test
    public void testPending() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add("0", "0");
        
        stream.createGroup("testGroup");
        
        StreamId id1 = stream.add("1", "1");
        StreamId id2 = stream.add("2", "2");
        
        Map<StreamId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1");
        assertThat(s.size()).isEqualTo(2);
        
        StreamId id3 = stream.add("3", "3");
        StreamId id4 = stream.add("4", "4");
        
        Map<StreamId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2");
        assertThat(s2.size()).isEqualTo(2);
        
        PendingResult pi = stream.listPending("testGroup");
        assertThat(pi.getLowestId()).isEqualTo(id1);
        assertThat(pi.getHighestId()).isEqualTo(id4);
        assertThat(pi.getTotal()).isEqualTo(4);
        assertThat(pi.getConsumerNames().keySet()).containsExactly("consumer1", "consumer2");
        
        List<PendingEntry> list = stream.listPending("testGroup", StreamId.MIN, StreamId.MAX, 10);
        assertThat(list.size()).isEqualTo(4);
        for (PendingEntry pendingEntry : list) {
            assertThat(pendingEntry.getId()).isIn(id1, id2, id3, id4);
            assertThat(pendingEntry.getConsumerName()).isIn("consumer1", "consumer2");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }
        
        List<PendingEntry> list2 = stream.listPending("testGroup", StreamId.MIN, StreamId.MAX, 10, "consumer1");
        assertThat(list2.size()).isEqualTo(2);
        for (PendingEntry pendingEntry : list2) {
            assertThat(pendingEntry.getId()).isIn(id1, id2);
            assertThat(pendingEntry.getConsumerName()).isEqualTo("consumer1");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }
    }
    
    @Test
    public void testAck() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add("0", "0");
        
        stream.createGroup("testGroup");
        
        StreamId id1 = stream.add("1", "1");
        StreamId id2 = stream.add("2", "2");
        
        Map<StreamId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1");
        assertThat(s.size()).isEqualTo(2);

        assertThat(stream.ack("testGroup", id1, id2)).isEqualTo(2);
    }
    
    @Test
    public void testReadGroup() {
        RStream<String, String> stream = redisson.getStream("test");

        StreamId id0 = stream.add("0", "0");
        
        stream.createGroup("testGroup", id0);
        
        stream.add("1", "1");
        stream.add("2", "2");
        stream.add("3", "3");
        
        Map<StreamId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1");
        assertThat(s.values().iterator().next().keySet()).containsAnyOf("1", "2", "3");
        assertThat(s.size()).isEqualTo(3);

        stream.add("1", "1");
        stream.add("2", "2");
        stream.add("3", "3");
        
        Map<StreamId, Map<String, String>> s1 = stream.readGroup("testGroup", "consumer1", 1);
        assertThat(s1.size()).isEqualTo(1);
        
        StreamId id = stream.add("1", "1");
        stream.add("2", "2");
        stream.add("3", "3");
        
        Map<StreamId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer1", id);
        assertThat(s2.size()).isEqualTo(2);
    }
    
    @Test
    public void testRangeReversed() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.addAll(new StreamId(1), entries1, 1, false);
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.addAll(new StreamId(2), entries2, 1, false);

        Map<StreamId, Map<String, String>> r2 = stream.rangeReversed(10, StreamId.MAX, StreamId.MIN);
        assertThat(r2.keySet()).containsExactly(new StreamId(2), new StreamId(1));
        assertThat(r2.get(new StreamId(1))).isEqualTo(entries1);
        assertThat(r2.get(new StreamId(2))).isEqualTo(entries2);
    }
    
    @Test
    public void testRange() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.addAll(new StreamId(1), entries1, 1, false);
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.addAll(new StreamId(2), entries2, 1, false);

        Map<StreamId, Map<String, String>> r = stream.range(10, new StreamId(0), new StreamId(1));
        assertThat(r).hasSize(1);
        assertThat(r.get(new StreamId(1))).isEqualTo(entries1);
        
        Map<StreamId, Map<String, String>> r2 = stream.range(10, StreamId.MIN, StreamId.MAX);
        assertThat(r2.keySet()).containsExactly(new StreamId(1), new StreamId(2));
        assertThat(r2.get(new StreamId(1))).isEqualTo(entries1);
        assertThat(r2.get(new StreamId(2))).isEqualTo(entries2);
    }
    
    @Test
    public void testPollMultiKeys() {
        RStream<String, String> stream = redisson.getStream("test");
        
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                stream.addAll(new StreamId(1), entries1);
            }
        };
        t.start();
        
        long start = System.currentTimeMillis();
        Map<String, Map<StreamId, Map<String, String>>> s = stream.read(2, 5, TimeUnit.SECONDS, new StreamId(0), "test1", StreamId.NEWEST);
        assertThat(System.currentTimeMillis() - start).isBetween(1900L, 2200L);
        assertThat(s).hasSize(1);
        assertThat(s.get("test").get(new StreamId(1))).isEqualTo(entries1);
    }
    
    @Test
    public void testPoll() {
        RStream<String, String> stream = redisson.getStream("test");
        
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                stream.addAll(new StreamId(1), entries1);
            }
        };
        t.start();
        
        long start = System.currentTimeMillis();
        Map<StreamId, Map<String, String>> s = stream.read(2, 5, TimeUnit.SECONDS, new StreamId(0));
        assertThat(System.currentTimeMillis() - start).isBetween(1900L, 2200L);
        assertThat(s).hasSize(1);
        assertThat(s.get(new StreamId(1))).isEqualTo(entries1);
    }
    
    @Test
    public void testSize() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.addAll(new StreamId(1), entries1, 1, false);
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.addAll(new StreamId(2), entries2, 1, false);
        assertThat(stream.size()).isEqualTo(2);
    }
    
    @Test
    public void testReadMultiKeysEmpty() {
        RStream<String, String> stream = redisson.getStream("test2");
        Map<String, Map<StreamId, Map<String, String>>> s = stream.read(10, new StreamId(0), "test1", new StreamId(0));
        assertThat(s).isEmpty();
    }
    
    @Test
    public void testReadMultiKeys() {
        RStream<String, String> stream1 = redisson.getStream("test1");
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("2", "22");
        entries1.put("3", "33");
        stream1.addAll(entries1);
        RStream<String, String> stream2 = redisson.getStream("test2");
        Map<String, String> entries2 = new LinkedHashMap<>();
        entries2.put("4", "44");
        entries2.put("5", "55");
        entries2.put("6", "66");
        stream2.addAll(entries2);
        
        Map<String, Map<StreamId, Map<String, String>>> s = stream2.read(10, new StreamId(0), "test1", new StreamId(0));
        assertThat(s).hasSize(2);
        assertThat(s.get("test1").values().iterator().next()).isEqualTo(entries1);
        assertThat(s.get("test2").values().iterator().next()).isEqualTo(entries2);
    }
    
    @Test
    public void testReadMulti() {
        RStream<String, String> stream = redisson.getStream("test");

        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.addAll(new StreamId(1), entries1, 1, false);

        Map<String, String> entries2 = new LinkedHashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.addAll(new StreamId(2), entries2, 1, false);

        Map<String, String> entries3 = new LinkedHashMap<>();
        entries3.put("15", "05");
        entries3.put("17", "07");
        stream.addAll(new StreamId(3), entries3, 1, false);
        
        Map<StreamId, Map<String, String>> result = stream.read(10, new StreamId(0, 0));
        assertThat(result).hasSize(3);
        assertThat(result.get(new StreamId(4))).isNull();
        assertThat(result.get(new StreamId(1))).isEqualTo(entries1);
        assertThat(result.get(new StreamId(2))).isEqualTo(entries2);
        assertThat(result.get(new StreamId(3))).isEqualTo(entries3);
    }
    
    @Test
    public void testReadSingle() {
        RStream<String, String> stream = redisson.getStream("test");
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.addAll(new StreamId(1), entries1, 1, true);
     
        Map<StreamId, Map<String, String>> result = stream.read(10, new StreamId(0, 0));
        assertThat(result).hasSize(1);
        assertThat(result.get(new StreamId(4))).isNull();
        assertThat(result.get(new StreamId(1))).isEqualTo(entries1);
    }
    
    @Test
    public void testReadEmpty() {
        RStream<String, String> stream2 = redisson.getStream("test");
        Map<StreamId, Map<String, String>> result2 = stream2.read(10, new StreamId(0, 0));
        assertThat(result2).isEmpty();
    }
    
    @Test
    public void testAdd() {
        RStream<String, String> stream = redisson.getStream("test1");
        StreamId s = stream.add("12", "33");
        assertThat(s.getId0()).isNotNegative();
        assertThat(s.getId1()).isNotNegative();
        assertThat(stream.size()).isEqualTo(1);
    }
    
    @Test
    public void testAddAll() {
        RStream<String, String> stream = redisson.getStream("test1");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries = new HashMap<>();
        entries.put("6", "61");
        entries.put("4", "41");
        stream.addAll(new StreamId(12, 42), entries, 10, false);
        assertThat(stream.size()).isEqualTo(1);

        entries.clear();
        entries.put("1", "11");
        entries.put("3", "31");
        stream.addAll(new StreamId(Long.MAX_VALUE), entries, 1, false);
        assertThat(stream.size()).isEqualTo(2);
    }
    
}
