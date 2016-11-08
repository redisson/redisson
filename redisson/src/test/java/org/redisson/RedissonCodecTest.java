package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.JsonJacksonMapValueCodec;
import org.redisson.codec.CborJacksonCodec;
import org.redisson.codec.FstCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.KryoCodec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.MsgPackJacksonCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.codec.SmileJacksonCodec;
import org.redisson.codec.SnappyCodec;
import org.redisson.config.Config;
import com.fasterxml.jackson.core.type.TypeReference;

public class RedissonCodecTest extends AbstractBaseTest {
    
    private Codec avroCodec = new SmileJacksonCodec();
    private Codec smileCodec = new SmileJacksonCodec();
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
        Config config = redissonRule.getSharedConfig();
        config.setCodec(lz4Codec);

        test(config);
    }

    @Test
    public void testJdk() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(codec);

        test(config);
    }
    
    @Test
    public void testMsgPack() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(msgPackCodec);

        test(config);
    }
    
    @Test
    public void testSmile() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(smileCodec);

        test(config);
    }

    @Test
    public void testAvro() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(avroCodec);

        test(config);
    }

    @Test
    public void testFst() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(fstCodec);

        test(config);
    }

    @Test
    public void testSnappy() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(snappyCodec);

        test(config);
    }

    @Test
    public void testJson() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(jsonCodec);

        test(config);
    }

    @Test
    public void testKryo() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(kryoCodec);

        test(config);
    }

    @Test
    public void testCbor() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(cborCodec);

        test(config);

    }

    @Test
    public void testListOfStrings() {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(new JsonJacksonCodec());
        RedissonClient redisson = redissonRule.createClient(config);

        RMap<String, List<String>> map = redisson.getMap("list of strings", jsonListOfStringCodec);
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));

        RMap<String, List<String>> map2 = redisson.getMap("list of strings", jsonListOfStringCodec);

        assertThat(map2).isEqualTo(map);
    }

    public void test(Config config) {
        RedissonClient redisson = redissonRule.createClient(config);
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
