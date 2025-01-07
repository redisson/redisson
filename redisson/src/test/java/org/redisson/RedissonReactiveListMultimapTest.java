package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RListMultimapReactive;
import org.redisson.api.RListReactive;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.redisson.api.listener.MapPutListener;
import org.redisson.api.listener.MapRemoveListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveListMultimapTest extends BaseReactiveTest {

    public static class SimpleKey implements Serializable {

        private String key;

        public SimpleKey() {
        }

        public SimpleKey(String field) {
            this.key = field;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "key: " + key;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimpleKey other = (SimpleKey) obj;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            return true;
        }

    }

    public static class SimpleValue implements Serializable {

        private String value;

        public SimpleValue() {
        }

        public SimpleValue(String field) {
            this.value = field;
        }

        public void setValue(String field) {
            this.value = field;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "value: " + value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimpleValue other = (SimpleValue) obj;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

    }

    @Test
    public void testSizeInMemory() {
        RListMultimapReactive<Object, Object> list = redisson.getListMultimap("test");
        sync(list.put("1", "2"));
        assertThat(sync(list.sizeInMemory())).isEqualTo(88);

        sync(list.put("1", "3"));
        assertThat(sync(list.sizeInMemory())).isEqualTo(96);
    }

    @Test
    public void testDelete() {
        RListMultimapReactive<Object, Object> testList = redisson.getListMultimap("test");
        sync(testList.put("1", "01"));
        sync(testList.put("1", "02"));
        sync(testList.put("1", "03"));
        RListReactive<Object> list = testList.get("1");

        sync(list.delete());
        assertThat(sync(testList.size())).isZero();
        assertThat(sync(testList.get("1").size())).isZero();
    }

    @Test
    public void testReadAllKeySet() {
        RListMultimapReactive<Object, Object> map = redisson.getListMultimap("test1");
        sync(map.put("1", "4"));
        sync(map.put("2", "5"));
        sync(map.put("3", "6"));

        assertThat(sync(map.readAllKeySet())).containsExactly("1", "2", "3");
    }

    @Test
    public void testSize() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("1"), new SimpleValue("4")));

        assertThat(sync(map.size())).isEqualTo(3);

        assertThat(sync(map.fastRemove(new SimpleKey("0")))).isEqualTo(1);

        RListReactive<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(sync(s.size())).isZero();
        assertThat(sync(map.size())).isOne();
    }

    @Test
    public void testKeySize() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("1"), new SimpleValue("4")));

        assertThat(sync(map.keySize())).isEqualTo(2);

        assertThat(sync(map.fastRemove(new SimpleKey("0")))).isOne();

        RListReactive<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(sync(s.size())).isZero();
        assertThat(sync(map.size())).isOne();
    }

    @Test
    public void testPut() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("{multi.map}.some.key");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("3")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("3")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));

        assertThat(sync(map.size())).isEqualTo(5);

        RListReactive<SimpleValue> s1 = map.get(new SimpleKey("0"));
        assertThat(sync(s1)).containsExactly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"));

        Mono<List<SimpleValue>> allValues = map.getAll(new SimpleKey("0"));
        assertThat(sync(allValues)).containsExactly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"),
                new SimpleValue("3"));

        RListReactive<SimpleValue> s2 = map.get(new SimpleKey("3"));
        assertThat(sync(s2)).containsExactly(new SimpleValue("4"));
    }

    @Test
    public void testRemoveAllFromCollection() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("3")));

        Collection<SimpleValue> values = Arrays.asList(new SimpleValue("1"), new SimpleValue("2"));
        assertThat(sync(map.get(new SimpleKey("0")).removeAll(values))).isTrue();
        assertThat(sync(map.get(new SimpleKey("0")).size())).isOne();
        assertThat(sync(map.get(new SimpleKey("0")).removeAll(Arrays.asList(new SimpleValue("3"))))).isTrue();
        assertThat(sync(map.get(new SimpleKey("0")).size())).isZero();
        assertThat(sync(map.get(new SimpleKey("0")).removeAll(Arrays.asList(new SimpleValue("3"))))).isFalse();
    }

    @Test
    public void testRemoveAll() {
        RListMultimapReactive<String, String> map = redisson.getListMultimap("test1");
        sync(map.put("0", "1"));
        sync(map.put("0", "1"));
        sync(map.put("0", "2"));
        sync(map.put("0", "3"));

        RListReactive<String> set = map.get("0");
        sync(set.removeAll(Arrays.asList("4", "5")));
        assertThat(sync(map.size())).isEqualTo(4);

        sync(set.removeAll(Arrays.asList("3")));
        assertThat(sync(map.size())).isEqualTo(3);

        List<String> values = sync(map.removeAll("0"));
        assertThat(values).containsExactly("1", "1", "2");
        assertThat(sync(map.size())).isZero();

        List<String> values2 = sync(map.removeAll("0"));
        assertThat(values2).isEmpty();
    }

    @Test
    public void testFastRemove() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        assertThat(sync(map.put(new SimpleKey("0"), new SimpleValue("1")))).isTrue();
        assertThat(sync(map.put(new SimpleKey("0"), new SimpleValue("2")))).isTrue();
        assertThat(sync(map.put(new SimpleKey("0"), new SimpleValue("2")))).isTrue();
        assertThat(sync(map.put(new SimpleKey("0"), new SimpleValue("3")))).isTrue();

        long removed = sync(map.fastRemove(new SimpleKey("0"), new SimpleKey("1")));
        assertThat(removed).isOne();
        assertThat(sync(map.size())).isZero();
    }

    @Test
    public void testContainsKey() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        assertThat(sync(map.containsKey(new SimpleKey("0")))).isTrue();
        assertThat(sync(map.containsKey(new SimpleKey("1")))).isFalse();
    }

    @Test
    public void testContainsValue() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("{1}test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));

        assertThat(sync(map.containsValue(new SimpleValue("1")))).isTrue();
        assertThat(sync(map.containsValue(new SimpleValue("0")))).isFalse();
    }

    @Test
    public void testContainsEntry() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));

        assertThat(sync(map.containsEntry(new SimpleKey("0"), new SimpleValue("1")))).isTrue();
        assertThat(sync(map.containsEntry(new SimpleKey("0"), new SimpleValue("2")))).isFalse();
    }

    @Test
    public void testRange() {
        RListMultimapReactive<Object, Object> map = redisson.getListMultimap("test1");
        sync(map.put(1, 1));
        sync(map.put(1, 2));
        sync(map.put(1, 3));
        sync(map.put(1, 4));
        sync(map.put(1, 5));

        assertThat(sync(map.get(1).range(1))).containsExactly(1, 2);
        assertThat(sync(map.get(1).range(1, 3))).containsExactly(2, 3, 4);
    }

    @Test
    public void testListener() {
        testWithParams(redisson -> {
            Queue<Integer> nfs = new ConcurrentLinkedQueue<>();
            RListMultimapReactive<Object, Object> map = redisson.reactive().getListMultimap("test1");
            sync(map.addListener((MapPutListener) name -> nfs.add(1)));
            sync(map.addListener((MapRemoveListener) name -> nfs.add(2)));
            sync(map.addListener((ListAddListener) name -> nfs.add(3)));
            sync(map.addListener((ListRemoveListener) name -> nfs.add(4)));
            sync(map.put(1, 5));
            sync(map.put(1, 8));
            sync(map.remove(1, 5));
            sync(map.remove(1, 8));

            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(nfs).containsExactlyInAnyOrder(1, 3, 3, 2, 4, 4));
        }, NOTIFY_KEYSPACE_EVENTS, "Ehl");
    }

    @Test
    public void testRemove() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("0"), new SimpleValue("3")));

        assertThat(sync(map.remove(new SimpleKey("0"), new SimpleValue("2")))).isTrue();
        assertThat(sync(map.remove(new SimpleKey("0"), new SimpleValue("5")))).isFalse();
        assertThat(sync(map.get(new SimpleKey("0")).size())).isEqualTo(2);
        assertThat(sync(map.getAll(new SimpleKey("0"))).size()).isEqualTo(2);
    }

    @Test
    public void testPutAll() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        List<SimpleValue> values =
                Arrays.asList(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"));
        assertThat(sync(map.putAll(new SimpleKey("0"), values))).isTrue();
        assertThat(sync(map.putAll(new SimpleKey("0"), Arrays.asList(new SimpleValue("1"))))).isTrue();

        List<SimpleValue> testValues =
                Arrays.asList(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"),
                        new SimpleValue("1"));
        assertThat(sync(map.get(new SimpleKey("0")))).containsExactlyElementsOf(testValues);
    }

    @Test
    public void testKeySet() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));

        assertThat(sync(map.readAllKeySet())).containsExactlyInAnyOrder(new SimpleKey("0"), new SimpleKey("3"));
    }

    @Test
    public void testReplaceValues() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));

        List<SimpleValue> values = Arrays.asList(new SimpleValue("11"), new SimpleValue("12"), new SimpleValue("12"));
        List<SimpleValue> oldValues = sync(map.replaceValues(new SimpleKey("0"), values));
        assertThat(oldValues).containsExactly(new SimpleValue("1"));

        List<SimpleValue> allValues = sync(map.getAll(new SimpleKey("0")));
        assertThat(allValues).containsExactlyElementsOf(values);

        List<SimpleValue> oldValues2 = sync(map.replaceValues(new SimpleKey("0"), Collections.emptyList()));
        assertThat(oldValues2).containsExactlyElementsOf(values);

        List<SimpleValue> vals = sync(map.getAll(new SimpleKey("0")));
        assertThat(vals).isEmpty();
    }

    @Test
    public void testFastReplaceValues() {
        RListMultimapReactive<SimpleKey, SimpleValue> map = redisson.getListMultimap("testFastReplace");

        sync(map.put(new SimpleKey("0"), new SimpleValue("1")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));

        List<SimpleValue> values = Arrays.asList(new SimpleValue("11"), new SimpleValue("12"), new SimpleValue("12"));

        sync(map.fastReplaceValues(new SimpleKey("0"), values));

        List<SimpleValue> allValues = sync(map.getAll(new SimpleKey("0")));
        assertThat(allValues).containsExactlyElementsOf(values);

        sync(map.fastReplaceValues(new SimpleKey("0"), Collections.emptyList()));

        List<SimpleValue> vals = sync(map.getAll(new SimpleKey("0")));
        assertThat(vals).isEmpty();
    }

    @Test
    void testAddAllUsingCollection() {
        RListMultimapReactive<String, String> map = redisson.getListMultimap("testAddAllUsingCollection");

        sync(map.get("1").addAll(List.of("2", "3", "4")));

        assertThat(sync(map.get("1").size())).isEqualTo(3);
        assertThat(sync(map.get("1").readAll())).containsExactly("2", "3", "4");
    }

    @Test
    void testAddAllUsingCollectionWithIndex() {
        RListMultimapReactive<String, String> map = redisson.getListMultimap("testAddAllUsingCollection");

        sync(map.get("1").addAll(List.of("2", "3", "4")));
        sync(map.get("1").addAll(2, List.of("5", "6", "7")));

        assertThat(sync(map.get("1").size())).isEqualTo(6);
        assertThat(sync(map.get("1").readAll())).containsExactly("2", "3", "5", "6", "7", "4");
    }

    @Test
    void testAddAllUsingPublisher() {
        RListMultimapReactive<String, String> map = redisson.getListMultimap("testAddAllUsingPublisher");

        sync(map.get("1").addAll(Flux.just("2", "3", "4")));

        assertThat(sync(map.get("1").size())).isEqualTo(3);
        assertThat(sync(map.get("1").readAll())).containsExactly("2", "3", "4");
    }

}
