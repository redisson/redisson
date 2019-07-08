package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.redisson.RedissonSetMultimapTest.SimpleKey;
import org.redisson.RedissonSetMultimapTest.SimpleValue;
import org.redisson.api.RListMultimap;
import org.redisson.api.RSetMultimap;

public class RedissonListMultimapTest extends BaseTest {

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
    public void testReadAllKeySet() {
        RListMultimap<String, String> map = redisson.getListMultimap("test1");
        map.put("1", "4");
        map.put("2", "5");
        map.put("3", "6");

        assertThat(map.readAllKeySet()).containsExactly("1", "2", "3");
    }

    @Test
    public void testSize() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("1"), new SimpleValue("4"));

        assertThat(map.size()).isEqualTo(3);

        assertThat(map.fastRemove(new SimpleKey("0"))).isEqualTo(1);

        List<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(s).isEmpty();
        assertThat(map.size()).isEqualTo(1);
    }
    
    @Test
    public void testKeySize() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("1"), new SimpleValue("4"));

        assertThat(map.keySize()).isEqualTo(2);

        assertThat(map.fastRemove(new SimpleKey("0"))).isEqualTo(1);

        List<SimpleValue> s = map.get(new SimpleKey("0"));
        assertThat(s).isEmpty();
        assertThat(map.size()).isEqualTo(1);
    }
    

    @Test
    public void testPut() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("{multi.map}.some.key");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.size()).isEqualTo(5);

        List<SimpleValue> s1 = map.get(new SimpleKey("0"));
        assertThat(s1).containsExactly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"));

        List<SimpleValue> allValues = map.getAll(new SimpleKey("0"));
        assertThat(allValues).containsExactly(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"));

        List<SimpleValue> s2 = map.get(new SimpleKey("3"));
        assertThat(s2).containsExactly(new SimpleValue("4"));
    }

    @Test
    public void testRemoveAllFromCollection() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
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
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("2"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));

        List<SimpleValue> values = map.removeAll(new SimpleKey("0"));
        assertThat(values).containsExactly(new SimpleValue("1"), new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"));
        assertThat(map.size()).isZero();

        List<SimpleValue> values2 = map.removeAll(new SimpleKey("0"));
        assertThat(values2).isEmpty();
    }

    @Test
    public void testFastRemove() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("1"))).isTrue();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("2"))).isTrue();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("2"))).isTrue();
        assertThat(map.put(new SimpleKey("0"), new SimpleValue("3"))).isTrue();

        long removed = map.fastRemove(new SimpleKey("0"), new SimpleKey("1"));
        assertThat(removed).isEqualTo(1);
        assertThat(map.size()).isZero();
    }

    @Test
    public void testContainsKey() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        assertThat(map.containsKey(new SimpleKey("0"))).isTrue();
        assertThat(map.containsKey(new SimpleKey("1"))).isFalse();
    }

    @Test
    public void testContainsValue() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("{1}test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));

        assertThat(map.containsValue(new SimpleValue("1"))).isTrue();
        assertThat(map.containsValue(new SimpleValue("0"))).isFalse();
    }

    @Test
    public void testContainsEntry() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));

        assertThat(map.containsEntry(new SimpleKey("0"), new SimpleValue("1"))).isTrue();
        assertThat(map.containsEntry(new SimpleKey("0"), new SimpleValue("2"))).isFalse();
    }

    @Test
    public void testRange() {
        RListMultimap<Integer, Integer> map = redisson.getListMultimap("test1");
        map.put(1, 1);
        map.put(1, 2);
        map.put(1, 3);
        map.put(1, 4);
        map.put(1, 5);

        assertThat(map.get(1).range(1)).containsExactly(1, 2);
        assertThat(map.get(1).range(1, 3)).containsExactly(2, 3, 4);
    }

    
    @Test
    public void testRemove() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
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
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        List<SimpleValue> values = Arrays.asList(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"));
        assertThat(map.putAll(new SimpleKey("0"), values)).isTrue();
        assertThat(map.putAll(new SimpleKey("0"), Arrays.asList(new SimpleValue("1")))).isTrue();

        List<SimpleValue> testValues = Arrays.asList(new SimpleValue("1"), new SimpleValue("2"), new SimpleValue("3"), new SimpleValue("3"), new SimpleValue("1"));
        assertThat(map.get(new SimpleKey("0"))).containsExactlyElementsOf(testValues);
    }

    @Test
    public void testKeySet() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.keySet()).containsOnly(new SimpleKey("0"), new SimpleKey("3"));
        assertThat(map.keySet().size()).isEqualTo(2);
    }

    @Test
    public void testValues() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("3"));
        map.put(new SimpleKey("2"), new SimpleValue("5"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.values().size()).isEqualTo(5);
        assertThat(map.values()).containsOnly(new SimpleValue("1"), new SimpleValue("1"), new SimpleValue("3"), new SimpleValue("5"), new SimpleValue("4"));
    }

    @Test
    public void testEntrySet() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        assertThat(map.entries().size()).isEqualTo(3);
        List<Map.Entry<SimpleKey, SimpleValue>> testMap = new ArrayList<Map.Entry<SimpleKey, SimpleValue>>();
        testMap.add(new AbstractMap.SimpleEntry(new SimpleKey("0"), new SimpleValue("1")));
        testMap.add(new AbstractMap.SimpleEntry(new SimpleKey("0"), new SimpleValue("1")));
        testMap.add(new AbstractMap.SimpleEntry(new SimpleKey("3"), new SimpleValue("4")));
        assertThat(map.entries()).containsOnlyElementsOf(testMap);
    }

    @Test
    public void testReplaceValues() {
        RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
        map.put(new SimpleKey("0"), new SimpleValue("1"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));

        List<SimpleValue> values = Arrays.asList(new SimpleValue("11"), new SimpleValue("12"), new SimpleValue("12"));
        List<SimpleValue> oldValues = map.replaceValues(new SimpleKey("0"), values);
        assertThat(oldValues).containsExactly(new SimpleValue("1"));

        List<SimpleValue> allValues = map.getAll(new SimpleKey("0"));
        assertThat(allValues).containsExactlyElementsOf(values);
    }


}
