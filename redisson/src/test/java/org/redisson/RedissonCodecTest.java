package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.JsonJacksonMapCodec;
import org.redisson.codec.AvroJacksonCodec;
import org.redisson.codec.CborJacksonCodec;
import org.redisson.codec.FstCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.KryoCodec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.SerializationCodec;
import org.redisson.codec.SmileJacksonCodec;
import org.redisson.codec.SnappyCodec;
import org.redisson.codec.SnappyCodecV2;
import org.redisson.config.Config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

import io.netty.buffer.ByteBuf;
import net.bytebuddy.utility.RandomString;

public class RedissonCodecTest extends BaseTest {
    private Codec smileCodec = new SmileJacksonCodec();
    private Codec codec = new SerializationCodec();
    private Codec kryoCodec = new KryoCodec();
    private Codec jsonCodec = new JsonJacksonCodec();
    private Codec cborCodec = new CborJacksonCodec();
    private Codec fstCodec = new FstCodec();
    private Codec snappyCodec = new SnappyCodec();
    private Codec snappyCodecV2 = new SnappyCodecV2();
//    private Codec msgPackCodec = new MsgPackJacksonCodec();
    private Codec lz4Codec = new LZ4Codec();
    private Codec jsonListOfStringCodec = new JsonJacksonMapCodec(
                    new TypeReference<String>() {}, new TypeReference<List<String>>() {});

    @Test
    public void testLZ4() {
        Config config = createConfig();
        config.setCodec(lz4Codec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testJdk() {
        Config config = createConfig();
        config.setCodec(codec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }
    
//    @Test
//    public void testMsgPack() {
//        Config config = createConfig();
//        config.setCodec(msgPackCodec);
//        RedissonClient redisson = Redisson.create(config);
//
//        test(redisson);
//    }
    
    @Test
    public void testSmile() {
        Config config = createConfig();
        config.setCodec(smileCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }
    
    @Test
    public void testAvro() throws IOException {
        AvroMapper am = new AvroMapper();
        AvroSchema schema = am.schemaFor(TestObject.class);
        Codec avroCodec = new AvroJacksonCodec(TestObject.class, schema);
        
        Config config = createConfig();
        config.setCodec(avroCodec);
        RedissonClient redisson = Redisson.create(config);

        RBucket<TestObject> b = redisson.getBucket("bucket");
        b.set(new TestObject("1", "2"));
        
        assertThat(b.get()).isEqualTo(new TestObject("1", "2"));
    }

    @Test
    public void testFst() {
        Config config = createConfig();
        config.setCodec(fstCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testSnappyBig() throws IOException {
        SnappyCodec sc = new SnappyCodec();
        String randomData = RandomString.make(Short.MAX_VALUE*2 + 142);
        ByteBuf g = sc.getValueEncoder().encode(randomData);
        String decompressedData = (String) sc.getValueDecoder().decode(g, null);
        assertThat(decompressedData).isEqualTo(randomData);
    }
    
    @Test
    public void testSnappy() {
        Config config = createConfig();
        config.setCodec(snappyCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }
    
    @Test
    public void testSnappyV2() {
        Config config = createConfig();
        config.setCodec(snappyCodecV2);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }
    
    @Test
    public void testSnappyBigV2() throws IOException {
        Codec sc = new SnappyCodecV2();
        String randomData = RandomString.make(Short.MAX_VALUE*2 + 142);
        ByteBuf g = sc.getValueEncoder().encode(randomData);
        String decompressedData = (String) sc.getValueDecoder().decode(g, null);
        assertThat(decompressedData).isEqualTo(randomData);
    }


    @Test
    public void testJson() {
        Config config = createConfig();
        config.setCodec(jsonCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testKryo() {
        Config config = createConfig();
        config.setCodec(kryoCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testCbor() {
        Config config = createConfig();
        config.setCodec(cborCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);

    }

    @Test
    public void testListOfStrings() {
        Config config = createConfig();
        config.setCodec(new JsonJacksonCodec());
        RedissonClient redisson = Redisson.create(config);

        RMap<String, List<String>> map = redisson.getMap("list of strings", jsonListOfStringCodec);
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));

        RMap<String, List<String>> map2 = redisson.getMap("list of strings", jsonListOfStringCodec);

        assertThat(map2).isEqualTo(map);
        
        redisson.shutdown();
    }

    public void test(RedissonClient redisson) {
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
        
        redisson.shutdown();
    }
}
