package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.client.codec.StringCodec;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedissonSetMultimapTest extends BaseTest {

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
    public void testRemoveAll2() {
        RSetMultimap<String, Long> testMap = redisson.getSetMultimap( "test-2" );
        testMap.clear();
        testMap.put( "t1", 1L );
        testMap.put( "t1", 2L );
        testMap.put( "t1", 3L );
        RSet<Long> set = testMap.get( "t1" );
        set.removeAll( Arrays.asList( 1L, 2L ) );
        assertThat(testMap.size()).isOne();
        assertThat(testMap.get( "t1" ).size()).isEqualTo(1);
        testMap.clear();
        assertThat(testMap.size()).isZero();
        assertThat(testMap.get( "t1" ).size()).isZero();
    }

    @Test
    public void testGetAdd() {
        RSetMultimap<String, Integer> multimap1 = redisson.getSetMultimap("myMultimap1");
        Set<Integer> one = multimap1.get("1");
        Set<Integer> two = multimap1.get("2");
        Set<Integer> four = multimap1.get("4");
        one.add(1);
        one.add(2);
        one.add(3);
        two.add(5);
        two.add(6);
        four.add(7);
        
        assertThat(multimap1.keySet()).containsOnly("1", "2", "4");
        assertThat(multimap1.keySize()).isEqualTo(3);
        assertThat(multimap1.get("1")).containsOnly(1, 2, 3);
        assertThat(multimap1.get("2")).containsOnly(5, 6);
        assertThat(multimap1.get("4")).containsOnly(7);
    }

    @Test
    public void testGetAddAll() {
        RSetMultimap<String, Integer> multimap1 = redisson.getSetMultimap("myMultimap1");
        Set<Integer> one = multimap1.get("1");
        Set<Integer> two = multimap1.get("2");
        Set<Integer> four = multimap1.get("4");
        one.addAll(Arrays.asList(1, 2, 3));
        two.addAll(Arrays.asList(5, 6));
        four.addAll(Arrays.asList(7));
        
        assertThat(multimap1.keySet()).containsOnly("1", "2", "4");
        assertThat(multimap1.keySize()).isEqualTo(3);
        assertThat(multimap1.get("1")).containsOnly(1, 2, 3);
        assertThat(multimap1.get("2")).containsOnly(5, 6);
        assertThat(multimap1.get("4")).containsOnly(7);
    }

    
    @Test
    public void testGetRemove() {
        RSetMultimap<String, Integer> multimap1 = redisson.getSetMultimap("myMultimap1");
        Set<Integer> one = multimap1.get("1");
        Set<Integer> two = multimap1.get("2");
        Set<Integer> four = multimap1.get("4");
        one.add(1);
        one.add(2);
        one.add(3);
        two.add(5);
        two.add(6);
        four.add(7);
        
        assertThat(one.remove(1)).isTrue();
        assertThat(one.remove(2)).isTrue();
        assertThat(two.remove(5)).isTrue();
        assertThat(four.remove(7)).isTrue();
        
        assertThat(multimap1.keySet()).containsOnly("1", "2");
        assertThat(multimap1.keySize()).isEqualTo(2);
        assertThat(multimap1.get("1")).containsOnly(3);
        assertThat(multimap1.get("2")).containsOnly(6);
    }

    @Test
    public void testGetRemoveAll() {
        RSetMultimap<String, Integer> multimap1 = redisson.getSetMultimap("myMultimap1");
        Set<Integer> one = multimap1.get("1");
        Set<Integer> two = multimap1.get("2");
        Set<Integer> four = multimap1.get("4");
        one.add(1);
        one.add(2);
        one.add(3);
        two.add(5);
        two.add(6);
        four.add(7);
        
        assertThat(one.removeAll(Arrays.asList(1, 2, 3))).isTrue();
        assertThat(two.removeAll(Arrays.asList(5, 6))).isTrue();
        assertThat(four.removeAll(Arrays.asList(7))).isTrue();
        assertThat(four.removeAll(Arrays.asList(9))).isFalse();
        
        assertThat(multimap1.keySet()).isEmpty();
        assertThat(multimap1.keySize()).isEqualTo(0);
    }

    @Test
    public void testSizeInMemory() {
        RSetMultimap<String, String> set = redisson.getSetMultimap("test");
        set.put("1", "2");
        assertThat(set.sizeInMemory()).isEqualTo(228);

        set.put("1", "3");
        assertThat(set.sizeInMemory()).isEqualTo(257);
    }
    
    @Test
    public void testSize() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));

        assertThat(map.size()).isEqualTo(2);

        map.fastRemove(new SimpleKey("0"));

        Set<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(s).isEmpty();
        assertThat(map.size()).isEqualTo(0);
    }
    
    @Test
    public void testKeySize() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("1"), new SimpleValue("3"));

        assertThat(map.keySize()).isEqualTo(2);
        assertThat(map.keySet().size()).isEqualTo(2);

        map.fastRemove(new SimpleKey("0"));

        Set<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(s).isEmpty();
        assertThat(map.keySize()).isEqualTo(1);
    }

    @Test
    public void testPut() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.size()).isEqualTo(4);

        Set<SimpleValue> s1 = map.get(new SimpleKey("0"));
        assertThat(s1).containsOnly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"));
        Set<SimpleValue> allValues = map.getAll(new SimpleKey("0"));
        assertThat(allValues).containsOnly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"));

        Set<SimpleValue> s2 = map.get(new SimpleKey("3"));
        assertThat(s2).containsOnly(new SimpleValue("4"));
    }

    @Test
    public void testRemoveAllFromCollection() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));

        Collection<SimpleValue> values = Arrays.asList(new SimpleValue("1"), new SimpleValue("2"));
        assertThat(map.get(new SimpleKey("0")).removeAll(values)).isTrue();
        assertThat(map.get(new SimpleKey("0")).size()).isEqualTo(1);
        assertThat(map.get(new SimpleKey("0")).removeAll(Arrays.asList(new SimpleValue("3")))).isTrue();
        assertThat(map.get(new SimpleKey("0")).size()).isZero();
        assertThat(map.get(new SimpleKey("0")).removeAll(Arrays.asList(new SimpleValue("3")))).isFalse();
    }
    
    @Test
    public void testRemoveAll() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));

        Set<SimpleValue> values = map.removeAll(new SimpleKey("0"));
        assertThat(values).containsOnly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"));
        assertThat(map.size()).isZero();

        Set<SimpleValue> values2 = map.removeAll(new SimpleKey("0"));
        assertThat(values2).isEmpty();
    }

    @Test
    public void testFastRemove() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("1"))).isTrue();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("2"))).isTrue();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("2"))).isFalse();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("3"))).isTrue();

        long removed = map.fastRemove(new SimpleKey("0"), new SimpleKey("1"));
        assertThat(removed).isEqualTo(1);
        assertThat(map.size()).isZero();
    }

    @Test
    public void testContainsKey() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        assertThat(map.containsKey(new SimpleKey("0"))).isTrue();
        assertThat(map.containsKey(new SimpleKey("1"))).isFalse();
    }

    @Test
    public void testContainsValue() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));

        assertThat(map.containsValue(new SimpleValue("1"))).isTrue();
        assertThat(map.containsValue(new SimpleValue("0"))).isFalse();
    }

    @Test
    public void testContainsEntry() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));

        assertThat(map.containsEntry(new SimpleKey("0"), new SimpleValue("1"))).isTrue();
        assertThat(map.containsEntry(new SimpleKey("0"), new SimpleValue("2"))).isFalse();
    }

    @Test
    public void testRemove() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));

        assertThat(map.remove(new SimpleKey("0"), new SimpleValue("2"))).isTrue();
        assertThat(map.remove(new SimpleKey("0"), new SimpleValue("5"))).isFalse();
        assertThat(map.get(new SimpleKey("0")).size()).isEqualTo(2);
        assertThat(map.getAll(new SimpleKey("0")).size()).isEqualTo(2);
    }

    @Test
    public void testPutAll() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        List<SimpleValue> values = Arrays.asList(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"));
        assertThat(map.putAll(new SimpleKey("0"), values)).isTrue();
        assertThat(map.putAll(new SimpleKey("0"), Arrays.asList(new SimpleValue("1")))).isFalse();

        assertThat(map.get(new SimpleKey("0"))).containsOnlyElementsOf(values);
    }

    @Test
    public void testKeySet() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.keySet()).containsOnly(new SimpleKey("0"), new SimpleKey("3"));
        assertThat(map.keySet().size()).isEqualTo(2);
    }

    @Test
    public void testValues() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.values()).containsOnly(new SimpleValue("1"), new SimpleValue("4"));
    }

    @Test
    public void testEntrySet() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.entries().size()).isEqualTo(2);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<SimpleKey, SimpleValue>();
        testMap.put(new SimpleKey("0"), new SimpleValue("1"));
        testMap.put(new SimpleKey("3"), new SimpleValue("4"));
        assertThat(map.entries()).containsOnlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testExpire() throws InterruptedException {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        assertThat(map.size()).isZero();
    }

    @Test
    public void testReplaceValues() {
        RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        List<SimpleValue> values = Arrays.asList(new SimpleValue("11"), new SimpleValue("12"));
        Set<SimpleValue> oldValues = map.replaceValues(new SimpleKey("0"), values);
        assertThat(oldValues).containsOnly(new SimpleValue("1"));

        Set<SimpleValue> allValues = map.getAll(new SimpleKey("0"));
        assertThat(allValues).containsExactlyElementsOf(values);

        Set<SimpleValue> oldValues2 = map.replaceValues(new SimpleKey("0"), Collections.emptyList());
        assertThat(oldValues2).containsExactlyElementsOf(values);

        Set<SimpleValue> vals = map.getAll(new SimpleKey("0"));
        assertThat(vals).isEmpty();

    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        assertThat(map.size()).isZero();
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expireAt(System.currentTimeMillis() + 100);

        map.clearExpire();

        Thread.sleep(500);

        assertThat(map.size()).isEqualTo(2);
    }

    @Test
    public void testDelete() {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");
        assertThat(map.delete()).isTrue();
        
        RSetMultimap<String, String> map2 = redisson.getSetMultimap("simple1");
        assertThat(map2.delete()).isFalse();

        RSetMultimap<String, String> multiset = redisson.getSetMultimap( "test" );
        multiset.put("1", "01");
        multiset.put("1", "02");
        multiset.put("1", "03");
        RSet<String> set = multiset.get( "1" );

        set.delete();
        assertThat(multiset.size()).isZero();
        assertThat(multiset.get("1").size()).isZero();
    }

    @Test
    public void testRename() {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");
        map.rename("simple2");

        RSetMultimap<String, String> map2 = redisson.getSetMultimap("simple2");
        assertThat(map2.size()).isEqualTo(2);
        assertThat(map2.get("1")).containsOnly("2");
        assertThat(map2.get("2")).containsOnly("3");

        RSetMultimap<String, String> map3 = redisson.getSetMultimap("simple");
        assertThat(map3.isExists()).isFalse();
        assertThat(map3.isEmpty()).isTrue();
    }

    @Test
    public void testRenamenx() {
        RSetMultimap<String, String> map = redisson.getSetMultimap("simple");
        map.put("1", "2");
        map.put("2", "3");

        RSetMultimap<String, String> map2 = redisson.getSetMultimap("simple2");
        map2.put("4", "5");

        assertThat(map.renamenx("simple2")).isFalse();
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.get("1")).containsOnly("2");
        assertThat(map.get("2")).containsOnly("3");
        assertThat(map2.get("4")).containsOnly("5");

        assertThat(map.renamenx("simple3")).isTrue();

        RSetMultimap<String, String> map3 = redisson.getSetMultimap("simple");
        assertThat(map3.isExists()).isFalse();
        assertThat(map3.isEmpty()).isTrue();

        RSetMultimap<String, String> map4 = redisson.getSetMultimap("simple3");
        assertThat(map4.size()).isEqualTo(2);
        assertThat(map4.get("1")).containsOnly("2");
        assertThat(map4.get("2")).containsOnly("3");
    }

    @Test
    public void testDistributedIterator() {
        RSetMultimap<String, String> map = redisson.getSetMultimap("set", StringCodec.INSTANCE);

        // populate set with elements
        List<String> stringsOne = IntStream.range(0, 128).mapToObj(i -> "one-" + i).collect(Collectors.toList());
        List<String> stringsTwo = IntStream.range(0, 128).mapToObj(i -> "two-" + i).collect(Collectors.toList());
        map.putAll("someKey", stringsOne);
        map.putAll("someKey", stringsTwo);

        Iterator<String> stringIterator = map.get("someKey")
                .distributedIterator("iterator_{set}", "one*", 10);

        // read some elements using iterator
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (stringIterator.hasNext()) {
                strings.add(stringIterator.next());
            }
        }

        // create another iterator instance using the same name
        RSetMultimap<String, String> map2 = redisson.getSetMultimap("set", StringCodec.INSTANCE);
        Iterator<String> stringIterator2 = map2.get("someKey")
                .distributedIterator("iterator_{set}", "one*", 10);


        assertTrue(stringIterator2.hasNext());

        // read all remaining elements
        stringIterator2.forEachRemaining(strings::add);
        stringIterator.forEachRemaining(strings::add);

        assertThat(strings).containsAll(stringsOne);
        assertThat(strings).hasSize(stringsOne.size());
    }
}
