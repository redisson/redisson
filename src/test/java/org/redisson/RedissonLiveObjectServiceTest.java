package org.redisson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.core.RMap;
import org.redisson.api.RLiveObjectService;
import org.redisson.api.RLiveObject;
import org.redisson.core.RAtomicLong;
import org.redisson.liveobject.resolver.DefaultNamingScheme;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.resolver.DistributedAtomicLongIdGenerator;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonLiveObjectServiceTest extends BaseTest {

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
        RLiveObjectService s = redisson.getLiveObjectService();
        TestREntity t = s.<TestREntity, String>getOrCreate(TestREntity.class, "1");
        assertEquals("1", t.getName());
        assertTrue(!redisson.getMap(DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "1")).isExists());
        t.setName("3333");
        assertEquals("3333", t.getName());
        assertTrue(!redisson.getMap(DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).isExists());
        t.setValue("111");
        assertEquals("111", t.getValue());
        assertTrue(redisson.getMap(DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).isExists());
        assertTrue(!redisson.getMap(DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "1")).isExists());
        assertEquals("111", redisson.getMap(DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).get("value"));
//        ((RLiveObject) t).getLiveObjectLiveMap().put("value", "555");
//        assertEquals("555", redisson.getMap(REntity.DefaultNamingScheme.INSTANCE.getName(TestREntity.class, "name", "3333")).get("value"));
//        assertEquals("3333", ((RObject) t).getName());//field access takes priority over the implemented interface.
    }

    @Test
    public void testLiveObjectWithCollection() {
        RLiveObjectService s = redisson.getLiveObjectService();
        TestREntityWithMap t = s.<TestREntityWithMap, String>getOrCreate(TestREntityWithMap.class, "2");
        RMap<String, String> map = redisson.<String, String>getMap("testMap");
        t.setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithMap, String>getOrCreate(TestREntityWithMap.class, "2")
                .getValue().get("field"));
        s.getOrCreate(TestREntityWithMap.class, "2").getValue().put("field", "333");
        assertEquals("333",
                s.<TestREntityWithMap, String>getOrCreate(TestREntityWithMap.class, "2")
                .getValue().get("field"));
        HashMap<String, String> map2 = new HashMap<>();
        map2.put("field", "hello");
        t.setValue(map2);
        assertEquals("hello",
                s.<TestREntityWithMap, String>getOrCreate(TestREntityWithMap.class, "2")
                .getValue().get("field"));
    }

    @Test
    public void testLiveObjectWithRObject() {
        RLiveObjectService s = redisson.getLiveObjectService();
        TestREntityWithRMap t = s.<TestREntityWithRMap, String>getOrCreate(TestREntityWithRMap.class, "2");
        RMap<String, String> map = redisson.<String, String>getMap("testMap");
        t.setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithRMap, String>getOrCreate(TestREntityWithRMap.class, "2")
                .getValue().get("field"));
        s.getOrCreate(TestREntityWithRMap.class, "2").getValue().put("field", "333");
        assertEquals("333",
                s.<TestREntityWithRMap, String>getOrCreate(TestREntityWithRMap.class, "2")
                .getValue().get("field"));
    }

    @Test
    public void testLiveObjectWithNestedLiveObjectAsId() {
        RLiveObjectService s = redisson.getLiveObjectService();
        TestREntity t1 = s.<TestREntity, String>getOrCreate(TestREntity.class, "1");
        try {
            s.<TestREntityIdNested, TestREntity>getOrCreate(TestREntityIdNested.class, t1);
            fail("Should not be here");
        } catch (Exception e) {
            assertEquals("Field with RId annotation cannot be a type of which class is annotated with REntity.", e.getCause().getMessage());
        }
    }

    @Test
    public void testLiveObjectWithNestedLiveObjectAsValue() throws Exception {
        RLiveObjectService s = redisson.getLiveObjectService();
        TestREntityWithRMap t1 = s.<TestREntityWithRMap, String>getOrCreate(TestREntityWithRMap.class, "111");
        TestREntityValueNested t2 = s.<TestREntityValueNested, String>getOrCreate(TestREntityValueNested.class, "122");
        RMap<String, String> map = redisson.<String, String>getMap("32123");
        t2.setValue(t1);
        t2.getValue().setValue(map);
        map.put("field", "123");
        assertEquals("123",
                s.<TestREntityWithRMap, String>getOrCreate(TestREntityWithRMap.class, "111")
                .getValue().get("field"));
        assertEquals("123",
                s.<TestREntityValueNested, String>getOrCreate(TestREntityValueNested.class, "122")
                .getValue().getValue().get("field"));
    }

    @REntity
    public static class TestClass {

        private String value;
        private String code;
        private Object content;

        @RId
        private Serializable id;

        public TestClass(Serializable id) {
            this.id = id;
        }

        public Serializable getId() {
            return id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(Object content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof TestClass) || !this.getClass().equals(obj.getClass())) {
                return false;
            }
            TestClass o = (TestClass) obj;
            return Objects.equals(this.id, o.id)
                    && Objects.equals(this.code, o.code)
                    && Objects.equals(this.value, o.value)
                    && Objects.equals(this.content, o.content);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 23 * hash + Objects.hashCode(this.value);
            hash = 23 * hash + Objects.hashCode(this.code);
            hash = 23 * hash + Objects.hashCode(this.id);
            hash = 23 * hash + Objects.hashCode(this.content);
            return hash;
        }

    }

    public static class ObjectId implements Serializable {

        private int id;

        public ObjectId() {
        }

        public ObjectId(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ObjectId)) {
                return false;
            }
            return id == ((ObjectId) obj).id;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 11 * hash + this.id;
            return hash;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return "" + id;
        }

    }

    @Test
    public void testSerializerable() {
        RLiveObjectService service = redisson.getLiveObjectService();

        TestClass t = service.getOrCreate(TestClass.class, "55555");
        assertTrue(Objects.equals("55555", t.getId()));

        t = service.getOrCreate(TestClass.class, 90909l);
        assertTrue(Objects.equals(90909l, t.getId()));

        t = service.getOrCreate(TestClass.class, 90909);
        assertTrue(Objects.equals(90909, t.getId()));

        t = service.getOrCreate(TestClass.class, new ObjectId(9090909));
        assertTrue(Objects.equals(new ObjectId(9090909), t.getId()));

        t = service.getOrCreate(TestClass.class, new Byte("0"));
        assertEquals(new Byte("0"), Byte.valueOf(t.getId().toString()));

        t = service.getOrCreate(TestClass.class, (byte) 90);
        assertEquals((byte) 90, Byte.parseByte(t.getId().toString()));

        t = service.getOrCreate(TestClass.class, Arrays.asList(1, 2, 3, 4));
        List<Integer> l = new ArrayList();
        l.addAll(Arrays.asList(1, 2, 3, 4));
        assertTrue(l.removeAll((List) t.getId()));
        assertTrue(l.isEmpty());

        try {
            service.getOrCreate(TestClass.class, new int[]{1, 2, 3, 4, 5});
            fail("Should not be here");
        } catch (Exception e) {
            assertEquals("RId value cannot be an array.", e.getCause().getMessage());
        }

        try {
            service.getOrCreate(TestClass.class, new byte[]{1, 2, 3, 4, 5});
            fail("Should not be here");
        } catch (Exception e) {
            assertEquals("RId value cannot be an array.", e.getCause().getMessage());
        }
    }

    @Test
    public void testPersist() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        ts.setValue("VALUE");
        TestClass persisted = service.persist(ts);
        assertEquals(new ObjectId(100), persisted.getId());
        assertEquals("VALUE", persisted.getValue());
        try {
            service.persist(ts);
            fail("Should not be here");
        } catch (Exception e) {
            assertEquals("This REntity already exists.", e.getMessage());
        }
    }

    @Test
    public void testMerge() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        ts.setValue("VALUE");
        TestClass merged = service.merge(ts);
        assertEquals(new ObjectId(100), merged.getId());
        assertEquals("VALUE", merged.getValue());
        try {
            service.persist(ts);
        } catch (Exception e) {
            assertEquals("This REntity already exists.", e.getMessage());
        }
        ts = new TestClass(new ObjectId(100));
        ts.setCode("CODE");
        merged = service.merge(ts);
        assertNull(ts.getValue());
        assertEquals("VALUE", merged.getValue());
        assertEquals("CODE", merged.getCode());
    }

    @Test
    public void testDetach() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        ts.setValue("VALUE");
        ts.setCode("CODE");
        TestClass merged = service.merge(ts);
        assertEquals("VALUE", merged.getValue());
        assertEquals("CODE", merged.getCode());
        TestClass detach = service.detach(merged);
        assertEquals(ts, detach);
    }

    @Test
    public void testIsPhantom() {
        RLiveObjectService service = redisson.getLiveObjectService();
        assertFalse(service.isExists(new Object()));
        TestClass ts = new TestClass(new ObjectId(100));
        assertFalse(service.isExists(service.get(TestClass.class, new ObjectId(100))));
        assertFalse(service.isExists(ts));
        ts.setValue("VALUE");
        ts.setCode("CODE");
        TestClass persisted = service.persist(ts);
        assertTrue(service.isExists(service.get(TestClass.class, new ObjectId(100))));
        assertFalse(service.isExists(ts));
        assertTrue(service.isExists(persisted));
    }

    @Test
    public void testIsLiveObject() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        assertFalse(service.isLiveObject(ts));
        TestClass persisted = service.persist(ts);
        assertFalse(service.isLiveObject(ts));
        assertTrue(service.isLiveObject(persisted));
    }

    @Test
    public void testAsLiveObject() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass instance = service.getOrCreate(TestClass.class, new ObjectId(100));
        RLiveObject liveObject = service.asLiveObject(instance);
        assertEquals(new ObjectId(100), liveObject.getLiveObjectId());
        try {
            service.asLiveObject(new Object());
            fail("Should not be here");
        } catch (Exception e) {
            assertTrue(e instanceof ClassCastException);
        }
    }

    @Test
    public void testClassRegistration() {
        RLiveObjectService service = redisson.getLiveObjectService();
        service.registerClass(TestClass.class);
        assertTrue(service.isClassRegistered(TestClass.class));
        RLiveObjectService newService = redisson.getLiveObjectService();
        assertTrue(newService.isClassRegistered(TestClass.class));
        RedissonClient newRedisson = Redisson.create(redisson.getConfig());
        assertFalse(newRedisson.getLiveObjectService().isClassRegistered(TestClass.class));
        newRedisson.shutdown(1, 5, TimeUnit.SECONDS);
    }

    @Test
    public void testClassUnRegistration() {
        RLiveObjectService service = redisson.getLiveObjectService();
        service.registerClass(TestClass.class);
        assertTrue(service.isClassRegistered(TestClass.class));
        RLiveObjectService newService = redisson.getLiveObjectService();
        RedissonClient newRedisson = Redisson.create(redisson.getConfig());
        newRedisson.getLiveObjectService().registerClass(TestClass.class);
        newService.unregisterClass(TestClass.class);
        assertFalse(service.isClassRegistered(TestClass.class));
        assertFalse(newService.isClassRegistered(TestClass.class));
        assertTrue(newRedisson.getLiveObjectService().isClassRegistered(TestClass.class));
        assertFalse(service.isClassRegistered(TestClass.class));
        assertFalse(newService.isClassRegistered(TestClass.class));
        newRedisson.shutdown(1, 5, TimeUnit.SECONDS);
    }

    @Test
    public void testGet() {
        RLiveObjectService service = redisson.getLiveObjectService();
        assertNull(service.get(TestClass.class, new ObjectId(100)));
        TestClass ts = new TestClass(new ObjectId(100));
        TestClass persisted = service.persist(ts);
        assertNull(service.get(TestClass.class, new ObjectId(100)));
        persisted.setCode("CODE");
        assertNotNull(service.get(TestClass.class, new ObjectId(100)));
    }

    @Test
    public void testGetOrCreate() {
        RLiveObjectService service = redisson.getLiveObjectService();
        assertNotNull(service.getOrCreate(TestClass.class, new ObjectId(100)));
        TestClass ts = new TestClass(new ObjectId(100));
        TestClass persisted = service.persist(ts);
        assertNotNull(service.getOrCreate(TestClass.class, new ObjectId(100)));
        persisted.setCode("CODE");
        assertNotNull(service.getOrCreate(TestClass.class, new ObjectId(100)));
    }

    @Test
    public void testRemoveByInstance() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        ts.setCode("CODE");
        TestClass persisted = service.persist(ts);
        assertTrue(service.isExists(persisted));
        service.delete(persisted);
        assertFalse(service.isExists(persisted));
    }

    @Test
    public void testRemoveById() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = new TestClass(new ObjectId(100));
        ts.setCode("CODE");
        TestClass persisted = service.persist(ts);
        assertTrue(service.isExists(persisted));
        service.delete(TestClass.class, new ObjectId(100));
        assertFalse(service.isExists(persisted));
    }
    
    @REntity
    public static class TestClassID1 {

        @RId(generator = DistributedAtomicLongIdGenerator.class)
        private Long name;

        public TestClassID1(Long name) {
            this.name = name;
        }

        public Long getName() {
            return name;
        }
        
    }
    
    @REntity
    public static class TestClassID2 {

        @RId(generator = DistributedAtomicLongIdGenerator.class)
        private Long name;

        public TestClassID2(Long name) {
            this.name = name;
        }

        public Long getName() {
            return name;
        }
        
    }
    
    @Test
    public void testCreate() {
        RLiveObjectService service = redisson.getLiveObjectService();
        TestClass ts = service.create(TestClass.class);
        UUID uuid = UUID.fromString(ts.getId().toString());
        assertEquals(4, uuid.version());
        TestClassID1 tc1 = service.create(TestClassID1.class);
        assertEquals(new Long(1), tc1.getName());
        TestClassID2 tc2 = service.create(TestClassID2.class);
        assertEquals(new Long(1), tc2.getName());
    }
    
}
