package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.codec.*;
import org.redisson.codec.protobuf.nativeData.Proto2AllTypes;
import org.redisson.codec.protobuf.nativeData.Proto3AllTypes;
import org.redisson.codec.protobuf.protostuffData.StuffData;
import org.redisson.config.Config;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonCodecTest extends RedisDockerTest {
    private Codec kryo5Codec = new Kryo5Codec();
    private Codec foryCodec = new ForyCodec();
    private Codec zstdCodec = new ZStdCodec();
    private Codec smileCodec = new SmileJacksonCodec();
    private Codec codec = new SerializationCodec();
    private Codec jsonCodec = new JsonJacksonCodec();
    private Codec json3Codec = new JsonJackson3Codec();
    private Codec cborCodec = new CborJacksonCodec();
    private Codec snappyCodecV2 = new SnappyCodecV2();
    //    private Codec msgPackCodec = new MsgPackJacksonCodec();
    private Codec lz4Codec = new LZ4Codec();
    private Codec jsonListOfStringCodec = new TypedJsonJacksonCodec(
            new TypeReference<String>() {}, new TypeReference<List<String>>() {});
    private Codec protobufV2Codec = new ProtobufCodec(String.class, Proto2AllTypes.AllTypes2.class);
    private Codec protobufV3Codec = new ProtobufCodec(String.class, Proto3AllTypes.AllTypes3.class,new JsonJacksonCodec());
    private Codec protobufStuffDataCodec = new ProtobufCodec( StuffData.class);

    @Test
    public void testUUIDJackson() {
        Config config = createConfig();
        config.setCodec(jsonCodec);
        RedissonClient redisson = Redisson.create(config);

        RBucket<UUID> bucket = redisson.getBucket("test");
        UUID value = UUID.randomUUID();
        bucket.set(value);
        assertThat(bucket.get()).isEqualTo(value);
    }

    @Test
    public void testUUIDJackson3() {
        Config config = createConfig();
        config.setCodec(json3Codec);
        RedissonClient redisson = Redisson.create(config);

        RBucket<UUID> set = redisson.getBucket("test");
        UUID value = UUID.randomUUID();
        set.set(value);
        assertThat(set.get()).isEqualTo(value);
    }

    @Test
    public void testKryo5Codec() {
        Config config = createConfig();
        config.setCodec(kryo5Codec);
        RedissonClient redisson = Redisson.create(config);

        RBucket<Object> bucket = redisson.getBucket("test");
        bucket.set(Collections.unmodifiableList(new ArrayList<>()));
        List s = (List) bucket.get();
        assertThat(s).isEmpty();

        bucket.set(Collections.checkedList(new ArrayList<>(), Object.class));
        List s4 = (List) bucket.get();
        assertThat(s4).isEmpty();

        bucket.set(Collections.synchronizedMap(Map.of("1", "2")));
        Map s2 = (Map) bucket.get();
        assertThat(s2).hasSize(1);

        redisson.shutdown();
    }

    @Test
    public void testLZ4() {
        Config config = createConfig();
        config.setCodec(lz4Codec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testZstd() {
        Config config = createConfig();
        config.setCodec(zstdCodec);
        RedissonClient redisson = Redisson.create(config);

        test(redisson);
    }

    @Test
    public void testFory() {
        Config config = createConfig();
        config.setCodec(foryCodec);
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
    public void testProtobufV2() {
        //native V2
        Config config = createConfig();
        config.setCodec(protobufV2Codec);
        RedissonClient redisson = Redisson.create(config);
        final Proto2AllTypes.AllTypes2 allTypes2 = Proto2AllTypes.AllTypes2.newBuilder()
                .addDoubleType(1)
                .addDoubleType(1.1)
                .setFloatType(1.1f)
                .setInt32Type(1)
                .setInt64Type(1)
                .setUint32Type(1)
                .setUint64Type(1)
                .setSint32Type(1)
                .setSint64Type(1)
                .setFixed32Type(1)
                .setFixed64Type(1)
                .setSfixed32Type(1)
                .setSfixed64Type(1)
                .setBoolType(true)
                .setStringType("1")
                .setBytesType(ByteString.copyFrom("1".getBytes()))
                .build();
        final RMap<String, Proto2AllTypes.AllTypes2> v2rMap = redisson.getMap("protobuf2Map");
        v2rMap.put("V2",allTypes2);
        final Proto2AllTypes.AllTypes2 getAllTypes2 = v2rMap.get("V2");
        Assertions.assertEquals(allTypes2, getAllTypes2);
        redisson.shutdown();
    }

    @Test
    public void testProtobufV3() {
        //native V3
        Config config = createConfig();
        config.setCodec(protobufV3Codec);
        RedissonClient redisson = Redisson.create(config);
        final Proto3AllTypes.AllTypes3 allTypes3 = Proto3AllTypes.AllTypes3.newBuilder()
                .addDoubleType(1.1)
                .addDoubleType(1.2)
                .setFloatType(1.1f)
                .setInt32Type(1)
                .setInt64Type(1)
                .setUint32Type(1)
                .setUint64Type(1)
                .setSint32Type(1)
                .setSint64Type(1)
                .setFixed32Type(1)
                .setFixed64Type(1)
                .setSfixed32Type(1)
                .setSfixed64Type(1)
                .setBoolType(true)
                .setStringType("1")
                .setBytesType(ByteString.copyFrom("1".getBytes()))
                .build();
        final RMap<String, Proto3AllTypes.AllTypes3> v3rMap = redisson.getMap("protobuf3Map");
        v3rMap.put("V3",allTypes3);
        final Proto3AllTypes.AllTypes3 getAllTypes3 = v3rMap.get("V3");
        Assertions.assertEquals(allTypes3, getAllTypes3);
        redisson.shutdown();
    }

    @Test
    public void testProtostuff() {
        //protostuff (a framework that bypasses the need to compile .proto files into .java file.)
        Config config = createConfig();
        config.setCodec(protobufStuffDataCodec);
        RedissonClient redisson = Redisson.create(config);
        final StuffData stuffData = new StuffData();
        stuffData.setAge(18);
        List<String> hobbies = new ArrayList<>();
        hobbies.add("game");
        hobbies.add("game");
        stuffData.setHobbies(hobbies);
        stuffData.setName("ccc");
        RList<StuffData> protostuffList = redisson.getList("protostuffList");
        protostuffList.add(stuffData);
        final StuffData getStuffData = protostuffList.get(0);
        Assertions.assertEquals(stuffData, getStuffData);
        redisson.shutdown();
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
        a.put("double", 100000.0);
        a.put("float", 100.0f);
        a.put("int", 100);
        a.put("long", 10000000000L);
        a.put("boolt", true);
        a.put("boolf", false);
        a.put("string", "testString");
        a.put("array", new ArrayList<Object>(Arrays.asList(1, 2.0, "adsfasdfsdf")));

        map.fastPut(1, a);
        Map<String, Object> resa = map.get(1);
        Assertions.assertEquals(a, resa);

        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assertions.assertTrue(set.contains(new TestObject("2", "3")));
        Assertions.assertTrue(set.contains(new TestObject("1", "2")));
        Assertions.assertFalse(set.contains(new TestObject("1", "9")));
        
        redisson.shutdown();
    }
}
