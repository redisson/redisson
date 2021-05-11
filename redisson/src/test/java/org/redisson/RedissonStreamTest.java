package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.stream.*;
import org.redisson.client.RedisException;
import org.redisson.client.WriteRedisConnectionException;

public class RedissonStreamTest extends BaseTest {

    @Test
    public void testAutoClaim() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));

        stream.createGroup("testGroup");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);

        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "33"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "44"));

        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);

        AutoClaimResult<String, String> res = stream.autoClaim("testGroup", "consumer1", 1, TimeUnit.MILLISECONDS, id3, 2);
        assertThat(res.getMessages().size()).isEqualTo(2);
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : res.getMessages().entrySet()) {
            assertThat(entry.getValue().keySet()).containsAnyOf("3", "4");
            assertThat(entry.getValue().values()).containsAnyOf("33", "44");
        }
    }

    @Test
    public void testPendingIdle() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));

        stream.createGroup("testGroup");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);

        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "3"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "4"));

        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);

        List<PendingEntry> list = stream.listPending("testGroup", StreamMessageId.MIN, StreamMessageId.MAX, 1, TimeUnit.MILLISECONDS, 10);
        assertThat(list.size()).isEqualTo(4);
        for (PendingEntry pendingEntry : list) {
            assertThat(pendingEntry.getId()).isIn(id1, id2, id3, id4);
            assertThat(pendingEntry.getConsumerName()).isIn("consumer1", "consumer2");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }

        List<PendingEntry> list2 = stream.listPending("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 1, TimeUnit.MILLISECONDS,10);
        assertThat(list2.size()).isEqualTo(2);
        for (PendingEntry pendingEntry : list2) {
            assertThat(pendingEntry.getId()).isIn(id1, id2);
            assertThat(pendingEntry.getConsumerName()).isEqualTo("consumer1");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }
    }

    @Test
    public void testTrim() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));

        assertThat(stream.trim(2)).isEqualTo(1);
    }

    @Test
    public void testPendingEmpty() {
        RStream<Object, Object> stream = redisson.getStream("test");
        stream.createGroup("testGroup");
        PendingResult result = stream.getPendingInfo("testGroup");
        assertThat(result.getTotal()).isZero();
        assertThat(result.getHighestId()).isNull();
        assertThat(result.getLowestId()).isNull();
        assertThat(result.getConsumerNames()).isEmpty();
    }
    
    @Test
    public void testUpdateGroupMessageId() {
        RStream<String, String> stream = redisson.getStream("test");

        StreamMessageId id = stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        System.out.println("id1 " + id1);
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        System.out.println("id2 " + id2);

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        stream.updateGroupMessageId("testGroup", id);
        
        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);
    }
    
    @Test
    public void testRemoveConsumer() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        assertThat(stream.removeConsumer("testGroup", "consumer1")).isEqualTo(2);
        assertThat(stream.removeConsumer("testGroup", "consumer2")).isZero();
    }
    
    @Test
    public void testRemoveGroup() {
        Assertions.assertThrows(RedisException.class, () -> {
            RStream<String, String> stream = redisson.getStream("test");

            stream.add(StreamAddArgs.entry("0", "0"));

            stream.createGroup("testGroup");

            StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
            StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

            stream.removeGroup("testGroup");

            stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        });
    }
    
    @Test
    public void testRemoveMessages() {
        RStream<String, String> stream = redisson.getStream("test");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("0", "0"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("1", "1"));
        assertThat(stream.size()).isEqualTo(2);
        
        assertThat(stream.remove(id1, id2)).isEqualTo(2);
        assertThat(stream.size()).isZero();
    }

    @Test
    public void testClaimRemove() throws InterruptedException {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));

        stream.createGroup("testGroup");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);

        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "33"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "44"));

        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);

        stream.remove(id3);

        Thread.sleep(2);

        Map<StreamMessageId, Map<String, String>> res = stream.claim("testGroup", "consumer1", 1, TimeUnit.MILLISECONDS, id3, id4);
        assertThat(res.size()).isEqualTo(1);
        assertThat(res.keySet()).containsExactly(id4);
    }

    @Test
    public void testClaim() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "33"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "44"));
        
        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);
        
        Map<StreamMessageId, Map<String, String>> res = stream.claim("testGroup", "consumer1", 1, TimeUnit.MILLISECONDS, id3, id4);
        assertThat(res.size()).isEqualTo(2);
        assertThat(res.keySet()).containsExactly(id3, id4);
        for (Map<String, String> map : res.values()) {
            assertThat(map.keySet()).containsAnyOf("3", "4");
            assertThat(map.values()).containsAnyOf("33", "44");
        }
    }

    @Test
    public void testAutoClaimIds() throws InterruptedException {
        RStream<String, String> stream = redisson.getStream("test3");

        stream.add(StreamAddArgs.entry("0", "0"));

        stream.createGroup("testGroup3");

        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup3", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);

        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "33"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "44"));

        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup3", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);

        FastAutoClaimResult res = stream.fastAutoClaim("testGroup3", "consumer1", 1, TimeUnit.MILLISECONDS, id3, 10);
        assertThat(res.getNextId()).isEqualTo(new StreamMessageId(0, 0));
        assertThat(res.getIds()).containsExactly(id3, id4);
    }

    @Test
    public void testClaimIds() throws InterruptedException {
        RStream<String, String> stream = redisson.getStream("test3");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup3");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup3", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "33"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "44"));
        
        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup3", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);
        
        List<StreamMessageId> res = stream.fastClaim("testGroup3", "consumer1", 1, TimeUnit.MILLISECONDS, id3, id4);
        assertThat(res.size()).isEqualTo(2);
        assertThat(res).containsExactly(id3, id4);
    }
    
    @Test
    public void testPending() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        StreamMessageId id3 = stream.add(StreamAddArgs.entry("3", "3"));
        StreamMessageId id4 = stream.add(StreamAddArgs.entry("4", "4"));
        
        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(s2.size()).isEqualTo(2);
        
        PendingResult pi = stream.getPendingInfo("testGroup");
        assertThat(pi.getLowestId()).isEqualTo(id1);
        assertThat(pi.getHighestId()).isEqualTo(id4);
        assertThat(pi.getTotal()).isEqualTo(4);
        assertThat(pi.getConsumerNames().keySet()).containsExactly("consumer1", "consumer2");
        
        List<PendingEntry> list = stream.listPending("testGroup", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        assertThat(list.size()).isEqualTo(4);
        for (PendingEntry pendingEntry : list) {
            assertThat(pendingEntry.getId()).isIn(id1, id2, id3, id4);
            assertThat(pendingEntry.getConsumerName()).isIn("consumer1", "consumer2");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }
        
        List<PendingEntry> list2 = stream.listPending("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        assertThat(list2.size()).isEqualTo(2);
        for (PendingEntry pendingEntry : list2) {
            assertThat(pendingEntry.getId()).isIn(id1, id2);
            assertThat(pendingEntry.getConsumerName()).isEqualTo("consumer1");
            assertThat(pendingEntry.getLastTimeDelivered()).isOne();
        }
    }

    @Test
    public void testPendingRange() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("11", "12"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("21", "22"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);
        
        Map<StreamMessageId, Map<String, String>> pres = stream.pendingRange("testGroup", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        assertThat(pres.keySet()).containsExactly(id1, id2);
        assertThat(pres.get(id1)).isEqualTo(Collections.singletonMap("11", "12"));
        assertThat(pres.get(id2)).isEqualTo(Collections.singletonMap("21", "22"));
        
        Map<StreamMessageId, Map<String, String>> pres2 = stream.pendingRange("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        assertThat(pres2.keySet()).containsExactly(id1, id2);
        assertThat(pres2.get(id1)).isEqualTo(Collections.singletonMap("11", "12"));
        assertThat(pres2.get(id2)).isEqualTo(Collections.singletonMap("21", "22"));
        
        Map<StreamMessageId, Map<String, String>> pres3 = stream.pendingRange("testGroup", "consumer2", StreamMessageId.MIN, StreamMessageId.MAX, 10);
        assertThat(pres3).isEmpty();
    }

    @Test
    public void testAck() {
        RStream<String, String> stream = redisson.getStream("test");

        stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup");
        
        StreamMessageId id1 = stream.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.size()).isEqualTo(2);

        assertThat(stream.ack("testGroup", id1, id2)).isEqualTo(2);
    }
    
    @Test
    public void testReadGroupMulti() {
        RStream<String, String> stream1 = redisson.getStream("test1");
        RStream<String, String> stream2 = redisson.getStream("test2");

        StreamMessageId id01 = stream1.add(StreamAddArgs.entry("0", "0"));
        StreamMessageId id02 = stream2.add(StreamAddArgs.entry("0", "0"));
        
        stream1.createGroup("testGroup", id01);
        stream2.createGroup("testGroup", id02);
        
        StreamMessageId id11 = stream1.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id12 = stream1.add(StreamAddArgs.entry("2", "2"));
        StreamMessageId id13 = stream1.add(StreamAddArgs.entry("3", "3"));
        StreamMessageId id21 = stream2.add(StreamAddArgs.entry("1", "1"));
        StreamMessageId id22 = stream2.add(StreamAddArgs.entry("2", "2"));
        StreamMessageId id23 = stream2.add(StreamAddArgs.entry("3", "3"));
        
        Map<String, Map<StreamMessageId, Map<String, String>>> s2 = stream1.readGroup("testGroup", "consumer1", StreamMultiReadGroupArgs.greaterThan(id11, Collections.singletonMap("test2", id21)));
        assertThat(s2).isEmpty();
    }

    @Test
    public void testReadGroupBlocking() {
        RStream<String, String> stream = redisson.getStream("test");

        StreamMessageId id0 = stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup", id0);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));

        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered().count(3).timeout(Duration.ofSeconds(5)));
        assertThat(s.values().iterator().next().keySet()).containsAnyOf("1", "2", "3");
        assertThat(s.size()).isEqualTo(3);

        stream.removeGroup("testGroup");
        
        stream.createGroup("testGroup", id0);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));

        RStream<String, String> stream2 = redisson.getStream("test2");
        
        StreamMessageId id1 = stream2.add(StreamAddArgs.entry("0", "0"));
        
        stream2.createGroup("testGroup", id1);
        
//        Map<String, Map<StreamMessageId, Map<String, String>>> s2 = stream.readGroup("testGroup", "consumer1", 3, 5, TimeUnit.SECONDS, id0, Collections.singletonMap("test2", id1));
//        assertThat(s2.values().iterator().next().values().iterator().next().keySet()).containsAnyOf("1", "2", "3");
//        assertThat(s2.size()).isEqualTo(3);
    }
        
    @Test
    public void testCreateEmpty() {
        RStream<String, String> stream = redisson.getStream("test");
        stream.createGroup("testGroup", StreamMessageId.ALL);
        stream.add(StreamAddArgs.entry("1", "2"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s).hasSize(1);
    }
    
    @Test
    public void testReadGroup() {
        RStream<String, String> stream = redisson.getStream("test");

        StreamMessageId id0 = stream.add(StreamAddArgs.entry("0", "0"));
        
        stream.createGroup("testGroup", id0);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));
        
        Map<StreamMessageId, Map<String, String>> s = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(s.values().iterator().next().keySet()).containsAnyOf("1", "2", "3");
        assertThat(s.size()).isEqualTo(3);

        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));
        
        Map<StreamMessageId, Map<String, String>> s1 = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered().count(1));
        assertThat(s1.size()).isEqualTo(1);
        
        StreamMessageId id = stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));
        
        Map<StreamMessageId, Map<String, String>> s2 = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.greaterThan(id));
        assertThat(s2).isEmpty();
    }
    
    @Test
    public void testRangeReversed() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1).trim(TrimStrategy.MAXLEN, 1));
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.add(new StreamMessageId(2), StreamAddArgs.entries(entries2).trim(TrimStrategy.MAXLEN, 1));

        Map<StreamMessageId, Map<String, String>> r2 = stream.rangeReversed(10, StreamMessageId.MAX, StreamMessageId.MIN);
        assertThat(r2.keySet()).containsExactly(new StreamMessageId(2), new StreamMessageId(1));
        assertThat(r2.get(new StreamMessageId(1))).isEqualTo(entries1);
        assertThat(r2.get(new StreamMessageId(2))).isEqualTo(entries2);
    }
    
    @Test
    public void testRange() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1).trim(TrimStrategy.MAXLEN, 1));
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.add(new StreamMessageId(2), StreamAddArgs.entries(entries2).trim(TrimStrategy.MAXLEN, 1));

        Map<StreamMessageId, Map<String, String>> r = stream.range(10, new StreamMessageId(0), new StreamMessageId(1));
        assertThat(r).hasSize(1);
        assertThat(r.get(new StreamMessageId(1))).isEqualTo(entries1);
        
        Map<StreamMessageId, Map<String, String>> r2 = stream.range(10, StreamMessageId.MIN, StreamMessageId.MAX);
        assertThat(r2.keySet()).containsExactly(new StreamMessageId(1), new StreamMessageId(2));
        assertThat(r2.get(new StreamMessageId(1))).isEqualTo(entries1);
        assertThat(r2.get(new StreamMessageId(2))).isEqualTo(entries2);
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
                
                stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1));
            }
        };
        t.start();

        Awaitility.await().between(Duration.ofMillis(1900), Duration.ofMillis(2200)).untilAsserted(() -> {
            Map<String, Map<StreamMessageId, Map<String, String>>> s = stream.read(StreamMultiReadArgs.greaterThan(new StreamMessageId(0), "test1", StreamMessageId.NEWEST)
                    .timeout(Duration.ofSeconds(5))
                    .count(2));
            assertThat(s).hasSize(1);
            assertThat(s.get("test").get(new StreamMessageId(1))).isEqualTo(entries1);
        });
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
                
                stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1));
            }
        };
        t.start();

        Awaitility.await().between(Duration.ofMillis(1900), Duration.ofMillis(2200)).untilAsserted(() -> {
            Map<StreamMessageId, Map<String, String>> s = stream.read(StreamReadArgs.greaterThan(new StreamMessageId(0)).count(2).timeout(Duration.ofSeconds(4)));
            assertThat(s).hasSize(1);
            assertThat(s.get(new StreamMessageId(1))).isEqualTo(entries1);
        });

        StreamMessageId id0 = stream.add(StreamAddArgs.entry("11", "11"));
        stream.add(StreamAddArgs.entry("22", "22"));

        RStream<String, String> stream2 = redisson.getStream("test2");
        
        StreamMessageId id1 = stream2.add(StreamAddArgs.entry("33", "33"));
        stream2.add(StreamAddArgs.entry("44", "44"));

        Map<String, Map<StreamMessageId, Map<String, String>>> s2 = stream.read(StreamMultiReadArgs.greaterThan(id0, "test2", id1)
                                                                            .timeout(Duration.ofSeconds(5)));
        assertThat(s2.values().iterator().next().values().iterator().next().keySet()).containsAnyOf("11", "22", "33", "44");
        assertThat(s2.keySet()).containsExactlyInAnyOrder("test", "test2");
    }
    
    @Test
    public void testSize() {
        RStream<String, String> stream = redisson.getStream("test");
        assertThat(stream.size()).isEqualTo(0);

        Map<String, String> entries1 = new HashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1).trim(TrimStrategy.MAXLEN, 1));
        assertThat(stream.size()).isEqualTo(1);
        
        Map<String, String> entries2 = new HashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.add(new StreamMessageId(2), StreamAddArgs.entries(entries2).trim(TrimStrategy.MAXLEN, 1));
        assertThat(stream.size()).isEqualTo(2);
    }
    
    @Test
    public void testReadMultiKeysEmpty() {
        RStream<String, String> stream = redisson.getStream("test2");
        Map<String, Map<StreamMessageId, Map<String, String>>> s = stream.read(StreamMultiReadArgs.greaterThan(new StreamMessageId(0), "test1", new StreamMessageId(0))
                                                                            .count(10));
        assertThat(s).isEmpty();
    }
    
    @Test
    public void testReadMultiKeys() {
        RStream<String, String> stream1 = redisson.getStream("test1");
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("2", "22");
        entries1.put("3", "33");
        stream1.add(StreamAddArgs.entries(entries1));
        RStream<String, String> stream2 = redisson.getStream("test2");
        Map<String, String> entries2 = new LinkedHashMap<>();
        entries2.put("4", "44");
        entries2.put("5", "55");
        entries2.put("6", "66");
        stream2.add(StreamAddArgs.entries(entries2));

        Map<String, Map<StreamMessageId, Map<String, String>>> s = stream2.read(StreamMultiReadArgs.greaterThan(new StreamMessageId(0), "test1", new StreamMessageId(0))
                                                                            .count(10));
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
        stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1).trim(TrimStrategy.MAXLEN, 1));

        Map<String, String> entries2 = new LinkedHashMap<>();
        entries2.put("5", "55");
        entries2.put("7", "77");
        stream.add(new StreamMessageId(2), StreamAddArgs.entries(entries2).trim(TrimStrategy.MAXLEN, 1));

        Map<String, String> entries3 = new LinkedHashMap<>();
        entries3.put("15", "05");
        entries3.put("17", "07");
        stream.add(new StreamMessageId(3), StreamAddArgs.entries(entries3).trim(TrimStrategy.MAXLEN, 1));
        
        Map<StreamMessageId, Map<String, String>> result = stream.read(StreamReadArgs.greaterThan(new StreamMessageId(0, 0)).count(10));
        assertThat(result).hasSize(3);
        assertThat(result.get(new StreamMessageId(4))).isNull();
        assertThat(result.get(new StreamMessageId(1))).isEqualTo(entries1);
        assertThat(result.get(new StreamMessageId(2))).isEqualTo(entries2);
        assertThat(result.get(new StreamMessageId(3))).isEqualTo(entries3);
    }
    
    @Test
    public void testReadSingle() {
        RStream<String, String> stream = redisson.getStream("test");
        Map<String, String> entries1 = new LinkedHashMap<>();
        entries1.put("1", "11");
        entries1.put("3", "31");
        stream.add(new StreamMessageId(1), StreamAddArgs.entries(entries1).trim(TrimStrategy.MAXLEN, 1));
     
        Map<StreamMessageId, Map<String, String>> result = stream.read(StreamReadArgs.greaterThan(new StreamMessageId(0, 0)).count(10));
        assertThat(result).hasSize(1);
        assertThat(result.get(new StreamMessageId(4))).isNull();
        assertThat(result.get(new StreamMessageId(1))).isEqualTo(entries1);
    }
    
    @Test
    public void testReadEmpty() {
        RStream<String, String> stream2 = redisson.getStream("test");
        Map<StreamMessageId, Map<String, String>> result2 = stream2.read(StreamReadArgs.greaterThan(new StreamMessageId(0, 0)).count(10));
        assertThat(result2).isEmpty();
    }
    
    @Test
    public void testAdd() {
        RStream<String, String> stream = redisson.getStream("test1");
        StreamMessageId s = stream.add(StreamAddArgs.entry("12", "33"));
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
        StreamMessageId id = new StreamMessageId(12, 42);
        stream.add(id, StreamAddArgs.entries(entries).trim(TrimStrategy.MAXLEN, 10));
        assertThat(stream.size()).isEqualTo(1);

        Map<StreamMessageId, Map<String, String>> res = stream.read(StreamReadArgs.greaterThan(new StreamMessageId(10, 42)));
        assertThat(res.get(id).size()).isEqualTo(2);
        
        entries.clear();
        entries.put("1", "11");
        entries.put("3", "31");
        stream.add(new StreamMessageId(Long.MAX_VALUE), StreamAddArgs.entries(entries).trim(TrimStrategy.MAXLEN, 1));
        assertThat(stream.size()).isEqualTo(2);
    }

    @Test
    public void testStreamConsumers() {
        RStream<String, String> stream = redisson.getStream("test1");
        
        StreamMessageId id1 = new StreamMessageId(12, 44);
        stream.createGroup("testGroup", id1);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));

        StreamMessageId id2 = new StreamMessageId(12, 44);
        stream.createGroup("testGroup2", id2);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));
        
        Map<StreamMessageId, Map<String, String>> map = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());
        assertThat(map.size()).isEqualTo(6);
        
        List<StreamConsumer> s1 = stream.listConsumers("testGroup");
        assertThat(s1).hasSize(1);
        assertThat(s1.get(0).getName()).isEqualTo("consumer1");
        assertThat(s1.get(0).getPending()).isEqualTo(6);
        assertThat(s1.get(0).getIdleTime()).isLessThan(100L);

        Map<StreamMessageId, Map<String, String>> map2 = stream.readGroup("testGroup2", "consumer2", StreamReadGroupArgs.neverDelivered());
        assertThat(map2.size()).isEqualTo(6);
        
        List<StreamConsumer> s2 = stream.listConsumers("testGroup2");
        assertThat(s2).hasSize(1);
        assertThat(s2.get(0).getName()).isEqualTo("consumer2");
        assertThat(s2.get(0).getPending()).isEqualTo(6);
        assertThat(s2.get(0).getIdleTime()).isLessThan(100L);
    
    }
    
    @Test
    public void testStreamGroupsInfo() {
        RStream<String, String> stream = redisson.getStream("test1");
        
        Map<String, String> entries = new HashMap<>();
        entries.put("6", "61");
        entries.put("4", "41");
        StreamMessageId id = new StreamMessageId(12, 42);
        stream.add(id, StreamAddArgs.entries(entries).trim(TrimStrategy.MAXLEN, 10));

        List<StreamGroup> s = stream.listGroups();
        assertThat(s).isEmpty();
        
        StreamMessageId id1 = new StreamMessageId(12, 44);
        stream.createGroup("testGroup", id1);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));

        StreamMessageId id2 = new StreamMessageId(12, 44);
        stream.createGroup("testGroup2", id2);
        
        stream.add(StreamAddArgs.entry("1", "1"));
        stream.add(StreamAddArgs.entry("2", "2"));
        stream.add(StreamAddArgs.entry("3", "3"));
        
        List<StreamGroup> s2 = stream.listGroups();
        assertThat(s2).hasSize(2);
        assertThat(s2.get(0).getName()).isEqualTo("testGroup");
        assertThat(s2.get(0).getConsumers()).isEqualTo(0);
        assertThat(s2.get(0).getPending()).isEqualTo(0);
        assertThat(s2.get(0).getLastDeliveredId()).isEqualTo(id1);
        assertThat(s2.get(1).getName()).isEqualTo("testGroup2");
        assertThat(s2.get(1).getConsumers()).isEqualTo(0);
        assertThat(s2.get(1).getPending()).isEqualTo(0);
        assertThat(s2.get(1).getLastDeliveredId()).isEqualTo(id2);
    }

    @Test
    public void testStreamInfoEmpty() {
        RStream<String, String> stream = redisson.getStream("test1");
        StreamMessageId id1 = new StreamMessageId(12, 44);
        stream.createGroup("testGroup", id1);
        
        StreamInfo<String, String> s = stream.getInfo();
    }
    
    @Test
    public void testStreamInfo() {
        RStream<String, String> stream = redisson.getStream("test1");
        
        Map<String, String> entries = new HashMap<>();
        entries.put("6", "61");
        entries.put("4", "41");
        StreamMessageId id = new StreamMessageId(12, 42);
        stream.add(id, StreamAddArgs.entries(entries).trim(TrimStrategy.MAXLEN, 10));

        Map<String, String> lastEntries = new HashMap<>();
        lastEntries.put("10", "52");
        lastEntries.put("44", "89");
        StreamMessageId lastId = new StreamMessageId(12, 43);
        stream.add(lastId, StreamAddArgs.entries(lastEntries).trim(TrimStrategy.MAXLEN, 10));

        StreamInfo<String, String> info = stream.getInfo();
        assertThat(info.getLength()).isEqualTo(2);
        assertThat(info.getRadixTreeKeys()).isEqualTo(1);
        assertThat(info.getRadixTreeNodes()).isEqualTo(2);
        assertThat(info.getLastGeneratedId()).isEqualTo(lastId);
        
        assertThat(info.getFirstEntry().getId()).isEqualTo(id);
        assertThat(info.getFirstEntry().getData()).isEqualTo(entries);
        assertThat(info.getLastEntry().getId()).isEqualTo(lastId);
        assertThat(info.getLastEntry().getData()).isEqualTo(lastEntries);
    }
    
}
