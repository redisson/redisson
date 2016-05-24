package org.redisson;

import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assume;
import org.junit.BeforeClass;

import org.junit.Test;
import org.redisson.core.GeoEntry;
import org.redisson.core.GeoPosition;
import org.redisson.core.GeoUnit;
import org.redisson.core.RGeo;

public class RedissonGeoTest extends BaseTest {

    @BeforeClass
    public static void checkRedisVersion() throws IOException, InterruptedException {
        boolean running = RedisRunner.isDefaultRedisServerInstanceRunning();
        if (!running) {
            RedisRunner.startDefaultRedisServerInstance();
        }
        Assume.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.1.0") > 0);
        if (!running) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }
    
    @Test
    public void testAdd() {
        RGeo<String> geo = redisson.getGeo("test");
        assertThat(geo.add(2.51, 3.12, "city1")).isEqualTo(1);
    }

    @Test
    public void testAddEntries() {
        RGeo<String> geo = redisson.getGeo("test");
        assertThat(geo.add(new GeoEntry(3.11, 9.10321, "city1"), new GeoEntry(81.1231, 38.65478, "city2"))).isEqualTo(2);
    }
    
    @Test
    public void testDist() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));
        
        assertThat(geo.dist("Palermo", "Catania", GeoUnit.METERS)).isEqualTo(166274.1516D);
    }
    
    @Test
    public void testDistEmpty() {
        RGeo<String> geo = redisson.getGeo("test");
        
        assertThat(geo.dist("Palermo", "Catania", GeoUnit.METERS)).isNull();
    }
    
    @Test
    public void testHash() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));
        
        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("Palermo", "sqc8b49rny0");
        expected.put("Catania", "sqdtr74hyu0");
        assertThat(geo.hash("Palermo", "Catania")).isEqualTo(expected);
    }

    @Test
    public void testHashEmpty() {
        RGeo<String> geo = redisson.getGeo("test");
        
        assertThat(geo.hash("Palermo", "Catania")).isEmpty();
    }

    
    @Test
    public void testPos() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));
        
        Map<String, GeoPosition> expected = new LinkedHashMap<String, GeoPosition>();
        expected.put("Palermo", new GeoPosition(13.361389338970184, 38.115556395496299));
        expected.put("Catania", new GeoPosition(15.087267458438873, 37.50266842333162));
        assertThat(geo.pos("test2", "Palermo", "test3", "Catania", "test1")).isEqualTo(expected);
    }

    @Test
    public void testPosEmpty() {
        RGeo<String> geo = redisson.getGeo("test");
        
        assertThat(geo.pos("test2", "Palermo", "test3", "Catania", "test1")).isEmpty();
    }
    
    @Test
    public void testRadius() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        assertThat(geo.radius(15, 37, 200, GeoUnit.KILOMETERS)).containsExactly("Palermo", "Catania");
    }
    
    @Test
    public void testRadiusEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radius(15, 37, 200, GeoUnit.KILOMETERS)).isEmpty();
    }

    @Test
    public void testRadiusWithDistance() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        Map<String, Double> expected = new HashMap<String, Double>();
        expected.put("Palermo", 190.4424);
        expected.put("Catania", 56.4413);
        assertThat(geo.radiusWithDistance(15, 37, 200, GeoUnit.KILOMETERS)).isEqualTo(expected);
    }

    @Test
    public void testRadiusWithDistanceHugeAmount() {
        RGeo<String> geo = redisson.getGeo("test");

        for (int i = 0; i < 10000; i++) {
            geo.add(10 + 0.000001*i, 11 + 0.000001*i, "" + i);
        }
        
        Map<String, Double> res = geo.radiusWithDistance(10, 11, 200, GeoUnit.KILOMETERS);
        assertThat(res).hasSize(10000);
    }
    
    @Test
    public void testRadiusWithPositionHugeAmount() {
        RGeo<String> geo = redisson.getGeo("test");

        for (int i = 0; i < 10000; i++) {
            geo.add(10 + 0.000001*i, 11 + 0.000001*i, "" + i);
        }
        
        Map<String, GeoPosition> res = geo.radiusWithPosition(10, 11, 200, GeoUnit.KILOMETERS);
        assertThat(res).hasSize(10000);
    }

    
    @Test
    public void testRadiusWithDistanceBigObject() {
        RGeo<Map<String, String>> geo = redisson.getGeo("test");

        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < 150; i++) {
            map.put("" + i, "" + i);
        }
        
        geo.add(new GeoEntry(13.361389, 38.115556, map));
        
        Map<String, String> map1 = new HashMap<String, String>(map);
        map1.remove("100");
        geo.add(new GeoEntry(15.087269, 37.502669, map1));
        
        Map<String, String> map2 = new HashMap<String, String>(map);
        map2.remove("0");
        geo.add(new GeoEntry(15.081269, 37.502169, map2));

        Map<Map<String, String>, Double> expected = new HashMap<Map<String, String>, Double>();
        expected.put(map, 190.4424);
        expected.put(map1, 56.4413);
        expected.put(map2, 56.3159);
        
        Map<Map<String, String>, Double> res = geo.radiusWithDistance(15, 37, 200, GeoUnit.KILOMETERS);
        assertThat(res.keySet()).containsOnlyElementsOf(expected.keySet());
        assertThat(res.values()).containsOnlyElementsOf(expected.values());
    }

    
    @Test
    public void testRadiusWithDistanceEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radiusWithDistance(15, 37, 200, GeoUnit.KILOMETERS)).isEmpty();
    }

    @Test
    public void testRadiusWithPosition() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        Map<String, GeoPosition> expected = new HashMap<String, GeoPosition>();
        expected.put("Palermo", new GeoPosition(13.361389338970184, 38.115556395496299));
        expected.put("Catania", new GeoPosition(15.087267458438873, 37.50266842333162));
        assertThat(geo.radiusWithPosition(15, 37, 200, GeoUnit.KILOMETERS)).isEqualTo(expected);
    }

    @Test
    public void testRadiusWithPositionEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radiusWithPosition(15, 37, 200, GeoUnit.KILOMETERS)).isEmpty();
    }
    
    @Test
    public void testRadiusMember() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        assertThat(geo.radius("Palermo", 200, GeoUnit.KILOMETERS)).containsExactly("Palermo", "Catania");
    }
    
    @Test
    public void testRadiusMemberEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radius("Palermo", 200, GeoUnit.KILOMETERS)).isEmpty();
    }

    @Test
    public void testRadiusMemberWithDistance() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        Map<String, Double> expected = new HashMap<String, Double>();
        expected.put("Palermo", 0.0);
        expected.put("Catania", 166.2742);
        assertThat(geo.radiusWithDistance("Palermo", 200, GeoUnit.KILOMETERS)).isEqualTo(expected);
    }
    
    @Test
    public void testRadiusMemberWithDistanceEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radiusWithDistance("Palermo", 200, GeoUnit.KILOMETERS)).isEmpty();
    }

    @Test
    public void testRadiusMemberWithPosition() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), new GeoEntry(15.087269, 37.502669, "Catania"));

        Map<String, GeoPosition> expected = new HashMap<String, GeoPosition>();
        expected.put("Palermo", new GeoPosition(13.361389338970184, 38.115556395496299));
        expected.put("Catania", new GeoPosition(15.087267458438873, 37.50266842333162));
        assertThat(geo.radiusWithPosition("Palermo", 200, GeoUnit.KILOMETERS)).isEqualTo(expected);
    }

    @Test
    public void testRadiusMemberWithPositionEmpty() {
        RGeo<String> geo = redisson.getGeo("test");

        assertThat(geo.radiusWithPosition("Palermo", 200, GeoUnit.KILOMETERS)).isEmpty();
    }

}
