package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.JsonJacksonMapValueCodec;
import org.redisson.codec.CborJacksonCodec;
import org.redisson.codec.FstCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.KryoCodec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.MsgPackJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.codec.SnappyCodec;
import org.redisson.core.RMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonCodecTest extends BaseTest {
    private Codec codec = new SerializationCodec();
    private Codec kryoCodec = new KryoCodec();
    private Codec jsonCodec = new JsonJacksonCodec();
    private Codec cborCodec = new CborJacksonCodec();
    private Codec fstCodec = new FstCodec();
    private Codec snappyCodec = new SnappyCodec();
    private Codec msgPackCodec = new MsgPackJacksonCodec();
    private Codec lz4Codec = new LZ4Codec();
    private Codec jsonListOfStringCodec = new JsonJacksonMapValueCodec<List<String>>(new TypeReference<List<String>>() {
    });

    @Test
    public void testLZ4() {
        Config config = createConfig();
        config.setCodec(lz4Codec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testJdk() {
        Config config = createConfig();
        config.setCodec(codec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testMsgPack() {
        Config config = createConfig();
        config.setCodec(msgPackCodec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testFst() {
        Config config = createConfig();
        config.setCodec(fstCodec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testSnappy() {
        Config config = createConfig();
        config.setCodec(snappyCodec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testJson() {
        Config config = createConfig();
        config.setCodec(jsonCodec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testKryo() {
        Config config = createConfig();
        config.setCodec(kryoCodec);
        redisson = Redisson.create(config);

        test();
    }

    @Test
    public void testCbor() {
        Config config = createConfig();
        config.setCodec(cborCodec);
        redisson = Redisson.create(config);

        test();

    }

    @Test
    public void testListOfStrings() {
        Config config = createConfig();
        config.setCodec(new JsonJacksonCodec());
        redisson = Redisson.create(config);

        RMap<String, List<String>> map = redisson.getMap("list of strings", jsonListOfStringCodec);
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));

        RMap<String, List<String>> map2 = redisson.getMap("list of strings", jsonListOfStringCodec);

        assertThat(map2).isEqualTo(map);
    }

    public void test() {
        RMap<Integer, Map<String, Object>> map = redisson.getMap("getAll");
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("double", new Double(100000.0));
        a.put("float", 100.0f);
        a.put("int", 100);
        a.put("long", 10000000000L);
        a.put("boolt", true);
        a.put("boolf", false);
        a.put("string", "testString");
        a.put("array", new ArrayList<Object>(Arrays.asList(1, 2.0, "adsfasdfsdf")));

        map.fastPut(1, a);
        Map<String, Object> resa = map.get(1);
        Assert.assertEquals(a, resa);

        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }
}
