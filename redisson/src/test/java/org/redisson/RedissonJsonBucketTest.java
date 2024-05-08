package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.redisson.api.JsonType;
import org.redisson.api.RJsonBucket;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.JacksonCodec;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonJsonBucketTest extends DockerRedisStackTest {

    public static class NestedType {

        private BigDecimal value2;

        private Integer value;

        private List<String> values;

        public BigDecimal getValue2() {
            return value2;
        }

        public void setValue2(BigDecimal value2) {
            this.value2 = value2;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }

    public static class TestType {

        private String name;

        private NestedType type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public NestedType getType() {
            return type;
        }

        public void setType(NestedType type) {
            this.type = type;
        }
    }

    @Test
    public void testCompareAndSetUpdate() {
        RJsonBucket<String> b = redisson.getJsonBucket("test", StringCodec.INSTANCE);
        b.set("{\"foo\": false, \"bar\":true}");
        boolean s = b.compareAndSet("$.foo", false, null);
        assertThat(s).isTrue();
        boolean result = b.isExists();
        assertThat(result).isTrue();
    }

    @Test
    public void testType() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);

        JsonType s = al.getType();
        assertThat(s).isEqualTo(JsonType.OBJECT);
        JsonType s1 = al.getType("name");
        assertThat(s1).isEqualTo(JsonType.STRING);

        RJsonBucket<TestType> al2 = redisson.getJsonBucket("test2", new JacksonCodec<>(TestType.class));
        assertThat(al2.getType()).isNull();
        assertThat(al2.getType("*")).isNull();
    }

    @Test
    public void testIncrementAndGet() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValue2(new BigDecimal(1));
        nt.setValue(1);
        nt.setValues(Arrays.asList("t1", "t2", "t4", "t5", "t6"));
        t.setType(nt);
        t.setName("name1");
        al.set(t);

        Integer s = al.incrementAndGet("type.value", 1);
        assertThat(s).isEqualTo(2);

        BigDecimal s2 = al.incrementAndGet("type.value2", new BigDecimal(1));
        assertThat(s2).isEqualTo(new BigDecimal(2));
    }

    @Test
    public void testCountKeys() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);
        assertThat(al.countKeys()).isEqualTo(1);

        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4", "t5", "t6"));
        al.set("type", nt);
        assertThat(al.countKeys()).isEqualTo(2);
        List<Long> l = al.countKeysMulti("$.type");
        assertThat(l.get(0)).isEqualTo(1L);
    }

    @Test
    public void testClear() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);
        assertThat(al.clear()).isEqualTo(1);

        TestType n = al.get(new JacksonCodec<>(new TypeReference<TestType>() {}));
        assertThat(n.getName()).isNull();

        TestType t1 = new TestType();
        t1.setName("name1");
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4", "t5", "t6"));
        t1.setType(nt);
        al.set(t1);

        assertThat(al.clear("type.values")).isEqualTo(1);

        TestType n2 = al.get(new JacksonCodec<>(new TypeReference<TestType>() {}));
        n2.setName("name2");
        assertThat(n2.getName()).isEqualTo("name2");
        assertThat(n2.getType().getValues()).isEmpty();
    }

    @Test
    public void testTrim() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4", "t5", "t6"));
        t.setType(nt);
        al.set(t);

        long s = al.arrayTrim("type.values", 1, 3);
        assertThat(s).isEqualTo(3);

        List<String> n = al.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "type.values");
        assertThat(n).containsExactly("t2", "t4", "t5");

        List<Long> s2 = al.arrayTrimMulti("$.type.values", 0, 1);
        assertThat(s2).containsExactly(2L);
    }

    @Test
    public void testArrayPoll() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4", "t5", "t6"));
        t.setType(nt);
        al.set(t);

        String v = al.arrayPollLast(new JacksonCodec<>(String.class), "type.values");
        assertThat(v).isEqualTo("t6");
        long s = al.arraySize("type.values");
        assertThat(s).isEqualTo(4);

        List<String> v2 = al.arrayPollLastMulti(new JacksonCodec<>(String.class), "$.type.values");
        assertThat(v2).containsExactly("t5");
        long s2 = al.arraySize("type.values");
        assertThat(s2).isEqualTo(3);

        String v3 = al.arrayPollFirst(new JacksonCodec<>(String.class), "type.values");
        assertThat(v3).isEqualTo("t1");
        long s3 = al.arraySize("type.values");
        assertThat(s3).isEqualTo(2);

        List<String> v4 = al.arrayPollFirstMulti(new JacksonCodec<>(String.class), "$.type.values");
        assertThat(v4).containsExactly("t2");
        long s4 = al.arraySize("type.values");
        assertThat(s4).isEqualTo(1);
    }

    @Test
    public void testArraySize() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4"));
        t.setType(nt);
        al.set(t);

        long s = al.arraySize("type.values");
        assertThat(s).isEqualTo(3);

        List<Long> s1 = al.arraySizeMulti("$.type.values");
        assertThat(s1).containsExactly(3L);
    }

    @Test
    public void testArrayInsert() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t4"));
        t.setType(nt);
        al.set(t);

        long s = al.arrayInsert("type.values", 2, "t3");
        assertThat(s).isEqualTo(4);

        List<Long> s1 = al.arrayInsertMulti("$.type.values", 3, "t5");
        assertThat(s1).containsExactly(5L);

        List<String> n = al.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "type.values");
        assertThat(n).containsExactly("t1", "t2", "t3", "t5", "t4");
    }

    @Test
    public void testArrayIndex() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8"));
        t.setType(nt);
        al.set(t);

        long s1 = al.arrayIndex("type.values", "t3");
        assertThat(s1).isEqualTo(2);

        List<Long> s2 = al.arrayIndexMulti("$.type.values", "t2");
        assertThat(s2).containsExactly(1L);

        long s3 = al.arrayIndex("type.values", "t3", 3, 6);
        assertThat(s3).isEqualTo(-1);

        List<Long> s4 = al.arrayIndexMulti("$.type.values", "t3", 2, 7);
        assertThat(s4).containsExactly(2L);
    }

    @Test
    public void testArrayAppend() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        NestedType nt = new NestedType();
        nt.setValues(Arrays.asList("t1", "t2"));
        t.setType(nt);
        al.set(t);

        long s1 = al.arrayAppend("$.type.values", "t3", "t4");
        assertThat(s1).isEqualTo(4);
        List<String> n = al.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "type.values");
        assertThat(n).containsExactly("t1", "t2", "t3", "t4");

        List<Long> s2 = al.arrayAppendMulti("$.type.values", "t5", "t6");
        assertThat(s2).containsExactly(6L);
        List<String> n2 = al.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "type.values");
        assertThat(n2).containsExactly("t1", "t2", "t3", "t4", "t5", "t6");
    }

    @Test
    public void testStringAppend() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);

        long s1 = al.stringAppend("name", "23");
        assertThat(s1).isEqualTo(7);
        String n = al.get(new JacksonCodec<>(String.class), "name");
        assertThat(n).isEqualTo("name123");

        List<Long> s2 = al.stringAppendMulti("$.name", "45");
        assertThat(s2).containsExactly(9L);
        String n2 = al.get(new JacksonCodec<>(String.class), "name");
        assertThat(n2).isEqualTo("name12345");

    }

    @Test
    public void testStringSize() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        Long s3 = al.stringSize("name");
        assertThat(s3).isNull();

        TestType t = new TestType();
        t.setName("name1");
        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        t.setType(nt);
        al.set(t);

        long s1 = al.stringSize("name");
        assertThat(s1).isEqualTo(5);
        List<Long> s2 = al.stringSizeMulti("$.name");
        assertThat(s2).containsExactly(5L);
    }

    @Test
    public void testGetAndSet() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);

        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        al.set("$.type", nt);

        NestedType nt2 = new NestedType();
        nt2.setValue(124);
        nt2.setValues(Arrays.asList("t4", "t3"));
        NestedType cv = al.getAndSet(new JacksonCodec<>(NestedType.class), "type", nt2);
        assertThat(cv.getValue()).isEqualTo(nt.getValue());
        assertThat(cv.getValues()).isEqualTo(nt.getValues());

        NestedType nt3 = al.get(new JacksonCodec<>(NestedType.class), "type");
        assertThat(nt3.getValue()).isEqualTo(nt2.getValue());
        assertThat(nt3.getValues()).isEqualTo(nt2.getValues());
    }

    @Test
    public void testKeys() {
        RJsonBucket<String> jb = redisson.getJsonBucket("test", StringCodec.INSTANCE);
        jb.set("{\"a\":false, \"nested\": {\"a\": {\"b\":2, \"c\": 1, \"d\": 3}}}");

        List<String> keys1 = jb.getKeys();
        assertThat(keys1).containsExactly("a", "nested");

        List<String> keys3 = jb.getKeys("nested.a");
        assertThat(keys3).containsExactly("b", "c", "d");

        List<List<String>> keys5 = jb.getKeysMulti("$.nested.a");
        assertThat(keys5).isEqualTo(Arrays.asList(Arrays.asList("b", "c", "d")));
    }

    @Test
    public void testCompareAndSet() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);

        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        al.set("$.type", nt);

        nt = al.get(new JacksonCodec<>(NestedType.class), "type");

        NestedType nt2 = new NestedType();
        nt2.setValue(124);
        nt2.setValues(Arrays.asList("t4", "t3"));
        assertThat(al.compareAndSet("$.type", null, nt2)).isFalse();
        assertThat(al.compareAndSet("$.type", nt, nt2)).isTrue();

        Integer n2 = al.get(new JacksonCodec<>(Integer.class), "type.value");
        assertThat(n2).isEqualTo(124);

        List<String> v1 = Arrays.asList("t4", "t3");
        List<String> v2 = Arrays.asList("t5", "t6");
        assertThat(al.compareAndSet("type.values", v1, v2)).isTrue();

        List<List<String>> n3 = al.get(new JacksonCodec<>(new TypeReference<List<List<String>>>(){}), "$.type.values");
        assertThat(n3.get(0)).containsExactly("t5", "t6");
    }

    @Test
    public void testSetIfExists() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");

        al.set(t);

        NestedType nt2 = new NestedType();
        nt2.setValue(124);
        nt2.setValues(Arrays.asList("t4", "t3"));
        assertThat(al.setIfExists("$.type", nt2)).isFalse();

        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        al.set("$.type", nt);

        Integer n2 = al.get(new JacksonCodec<>(Integer.class), "type.value");
        assertThat(n2).isEqualTo(123);

        assertThat(al.setIfExists("$.type", nt2)).isTrue();

        Integer n3 = al.get(new JacksonCodec<>(Integer.class), "type.value");
        assertThat(n3).isEqualTo(124);
    }

    @Test
    public void testTrySet() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");

        al.set(t);

        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        al.set("$.type", nt);

        NestedType nt2 = new NestedType();
        nt2.setValue(124);
        nt2.setValues(Arrays.asList("t4", "t3"));
        assertThat(al.setIfAbsent("$.type", nt2)).isFalse();
        assertThat(al.setIfAbsent("type", nt2)).isFalse();

        Integer n2 = al.get(new JacksonCodec<>(Integer.class), "type.value");
        assertThat(n2).isEqualTo(123);
    }

    @Test
    public void testDelete() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        assertThat(al.delete()).isFalse();
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);
        assertThat(al.delete()).isTrue();
    }

    @Test
    public void testMerge() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        TestType t = new TestType();
        t.setName("name1");
        al.set(t);

        al.merge("$.name", "name2");
        t = al.get();
        assertThat(t.getName()).isEqualTo("name2");
    }

    @Test
    public void testSetGet() {
        RJsonBucket<TestType> al = redisson.getJsonBucket("test", new JacksonCodec<>(TestType.class));
        assertThat(al.get()).isNull();

        TestType t = new TestType();
        t.setName("name1");

        al.set(t);
        t = al.get();
        assertThat(t.getName()).isEqualTo("name1");

        al.set("$.name", "name2");
        t = al.get();
        assertThat(t.getName()).isEqualTo("name2");

        String n = al.get(new JacksonCodec<>(String.class), "name");
        assertThat(n).isEqualTo("name2");

        NestedType nt = new NestedType();
        nt.setValue(123);
        nt.setValues(Arrays.asList("t1", "t2"));
        al.set("$.type", nt);

        List<Integer> n1 = al.get(new JacksonCodec<>(new TypeReference<List<Integer>>(){}), "$.type.value");
        assertThat(n1).containsOnly(123);

        Integer n2 = al.get(new JacksonCodec<>(Integer.class), "type.value");
        assertThat(n2).isEqualTo(123);

        List<String> n3 = al.get(new JacksonCodec<>(new TypeReference<List<String>>(){}), "type.values");
        assertThat(n3).containsExactly("t1", "t2");
    }


}
