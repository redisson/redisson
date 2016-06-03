package org.redisson;

import java.io.Serializable;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.core.RMap;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;

/**
 *
 * @author ruigu
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

    @Test
    public void test() {
        RedissonAttachedLiveObjectService<TestREntity, String> s = redisson.<TestREntity, String>getAttachedLiveObjectService();
        TestREntity t = s.get(TestREntity.class, "1");
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
    }
}
