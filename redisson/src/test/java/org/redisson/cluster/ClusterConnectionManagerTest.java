package org.redisson.cluster;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Leon Chen
 * @since 2.1.0
 */
public class ClusterConnectionManagerTest {
    private static final Map<String, Integer> TABLE = new HashMap<String, Integer>();

    static {
        TABLE.put("{}abc", 5980);
        TABLE.put(null, 0);
        TABLE.put("", 0);
        TABLE.put("}{abc", 15680);
        TABLE.put("{abc", 444);
        TABLE.put("{}", 15257);
        TABLE.put("abc", 7638);
        TABLE.put("{abc}", 7638);
        TABLE.put("abc{abc}abc", 7638);
    }

    @Test
    public void calcSlot() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        ClusterConnectionManager manager = (ClusterConnectionManager) unsafe.allocateInstance(ClusterConnectionManager.class);
        Field logField = ClusterConnectionManager.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(manager, LoggerFactory.getLogger(ClusterConnectionManager.class));
        for (Map.Entry<String, Integer> entry : TABLE.entrySet()) {
            assertThat(manager.calcSlot(entry.getKey())).isEqualTo(entry.getValue());
        }
    }

}