package org.redisson;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.core.RMap;
import org.redisson.core.RObject;
import org.redisson.liveobject.RAttachedLiveObjectService;
import org.redisson.liveobject.RLiveObject;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonAttachedLiveObjectServiceTest extends BaseTest {

    @REntity
    public static class TestREntity implements Comparable<TestREntity>, Serializable {

        @RId
        private String name;
        private String value;

        public TestREntity(String name) {
            this.name = name;
        }

        public TestREntity(String name, String value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestREntity o) {
            int res = name.compareTo(o.name);
            if (res == 0) {
                return value.compareTo(o.value);
            }
            return res;
        }
    }

    @REntity
    public static class TestREntityWithRMap implements Comparable<TestREntityWithRMap>, Serializable {

        @RId
        private String name;
        private RMap value;

        public TestREntityWithRMap(String name) {
            this.name = name;
        }

        public TestREntityWithRMap(String name, RMap value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public RMap getValue() {
            return value;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setValue(RMap value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestREntityWithRMap o) {
            int res = name.compareTo(o.name);
            if (res == 0 || value != null || o.value != null) {
                if (value.getName() == null) {
                    return -1;
                }
                return value.getName().compareTo(o.value.getName());
            }
            return res;
        }
    }

    @REntity
    public static class TestREntityWithMap implements Comparable<TestREntityWithMap>, Serializable {

        @RId
        private String name;
        private Map value;

        public TestREntityWithMap(String name) {
            this.name = name;
        }

        public TestREntityWithMap(String name, Map value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Map getValue() {
            return value;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setValue(Map value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestREntityWithMap o) {
            return name.compareTo(o.name);
        }
    }

    @REntity
    public static class TestREntityIdNested implements Comparable<TestREntityIdNested>, Serializable {

        @RId
        private TestREntity name;
        private String value;

        public TestREntityIdNested(TestREntity name) {
            this.name = name;
        }

        public TestREntityIdNested(TestREntity name, String value) {
            super();
            this.name = name;
            this.value = value;
        }

        public TestREntity getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public void setName(TestREntity name) {
            this.name = name;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestREntityIdNested o) {
            int res = name.compareTo(o.name);
            if (res == 0 || value != null || o.value != null) {
                return value.compareTo(o.value);
            }
            return res;
        }
    }

    @REntity
    public static class TestREntityValueNested implements Comparable<TestREntityValueNested>, Serializable {

        @RId
        private String name;
        private TestREntityWithRMap value;

        public TestREntityValueNested(String name) {
            this.name = name;
        }

        public TestREntityValueNested(String name, TestREntityWithRMap value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public TestREntityWithRMap getValue() {
            return value;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setValue(TestREntityWithRMap value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestREntityValueNested o) {
            int res = name.compareTo(o.name);
            if (res == 0 || value != null || o.value != null) {
                return value.compareTo(o.value);
            }
            return res;
        }
    }

    @Test
    public void testBasics() {
        RAttachedLiveObjectService s = redisson.getAttachedLiveObjectService();
        TestREntity t = s.<TestREntity, String>get(TestREntity.class, "1");
        assertEquals("1", t.getName());
        assertTrue(!redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "1")).isExists());
        t.setName("3333");
        assertEquals("3333", t.getName());
        assertTrue(!redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).isExists());
        t.setValue("111");
        assertEquals("111", t.getValue());
        assertTrue(redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).isExists());
        assertTrue(!redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "1")).isExists());
        assertEquals("111", redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).get("value"));
//        ((RLiveObject) t).getLiveObjectLiveMap().put("value", "555");
//        assertEquals("555", redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).get("value"));
//        assertEquals("3333", ((RObject) t).getName());//field access takes priority over the implemented interface.
    }

    @Test
    public void testLiveObjectWithCollection() {
        RAttachedLiveObjectService s = redisson.getAttachedLiveObjectService();
        TestREntityWithMap t = s.<TestREntityWithMap, String>get(TestREntityWithMap.class, "2");
        RMap<String, String> map = redisson.<String, String>getMap("testMap");
        t.setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithMap, String>get(TestREntityWithMap.class, "2")
                .getValue().get("field"));
        s.get(TestREntityWithMap.class, "2").getValue().put("field", "333");
        assertEquals("333",
                s.<TestREntityWithMap, String>get(TestREntityWithMap.class, "2")
                .getValue().get("field"));
        HashMap<String, String> map2 = new HashMap<>();
        map2.put("field", "hello");
        t.setValue(map2);
        assertEquals("hello",
                s.<TestREntityWithMap, String>get(TestREntityWithMap.class, "2")
                .getValue().get("field"));
    }

    @Test
    public void testLiveObjectWithRObject() {
        RAttachedLiveObjectService s = redisson.getAttachedLiveObjectService();
        TestREntityWithRMap t = s.<TestREntityWithRMap, String>get(TestREntityWithRMap.class, "2");
        RMap<String, String> map = redisson.<String, String>getMap("testMap");
        t.setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithRMap, String>get(TestREntityWithRMap.class, "2")
                .getValue().get("field"));
        s.get(TestREntityWithRMap.class, "2").getValue().put("field", "333");
        assertEquals("333",
                s.<TestREntityWithRMap, String>get(TestREntityWithRMap.class, "2")
                .getValue().get("field"));
    }

    @Test
    public void testLiveObjectWithNestedLiveObjectAsId() {
        RAttachedLiveObjectService s = redisson.getAttachedLiveObjectService();
        TestREntity t1 = s.<TestREntity, String>get(TestREntity.class, "1");
        try {
            s.<TestREntityIdNested, TestREntity>get(TestREntityIdNested.class, t1);
        } catch (Exception e) {
            assertEquals("Field with RId annotation cannot be a type of which class is annotated with REntity.", e.getCause().getMessage());
        }
    }

    @Test
    public void testLiveObjectWithNestedLiveObjectAsValue() throws Exception {
        RAttachedLiveObjectService s = redisson.getAttachedLiveObjectService();
        TestREntityWithRMap t1 = s.<TestREntityWithRMap, String>get(TestREntityWithRMap.class, "111");
        TestREntityValueNested t2 = s.<TestREntityValueNested, String>get(TestREntityValueNested.class, "122");
        RMap<String, String> map = redisson.<String, String>getMap("32123");
        t2.setValue(t1);
        t2.getValue().setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithRMap, String>get(TestREntityWithRMap.class, "111")
                .getValue().get("field"));
        assertEquals("123",
                s.<TestREntityValueNested, String>get(TestREntityValueNested.class, "122")
                .getValue().getValue().get("field"));
    }
}
