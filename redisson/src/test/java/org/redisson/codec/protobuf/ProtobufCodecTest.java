package org.redisson.codec.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.Kryo5Codec;
import org.redisson.codec.ProtobufCodec;
import org.redisson.codec.protobuf.protostuffData.StuffData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ProtobufCodecTest {

    @Test
    public void testBlacklist() throws IOException {
        Class<StuffData> stuffDataClass = StuffData.class;
        ProtobufCodec protobufCodec = new ProtobufCodec(stuffDataClass);
        Encoder valueEncoder = protobufCodec.getValueEncoder();

        //classes in blacklist will not be serialized using protobuf ,but instead will use blacklistCodec
        protobufCodec.addBlacklist(stuffDataClass);
        final StuffData stuffData = getStuffData();
        ByteBuf buf = valueEncoder.encode(stuffData);
        byte[] jsonBytes = new byte[buf.readableBytes()];
        buf.readBytes(jsonBytes);
        Assertions.assertTrue(isValidJson(new String(jsonBytes)));

        //classes not in blacklist will be serialized using protobuf
        protobufCodec.removeBlacklist(stuffDataClass);
        buf = valueEncoder.encode(stuffData);
        byte[] protobufBytes = new byte[buf.readableBytes()];
        buf.readBytes(protobufBytes);
        StuffData desStuffData = deserializeProtobufBytes(protobufBytes, StuffData.class);
        Assertions.assertEquals(stuffData, desStuffData);
    }

    @Test
    public void testBlacklistCodec() throws IOException {
        Class<StuffData> stuffDataClass = StuffData.class;

        //default blacklistCodec is JsonJacksonCodec
        ProtobufCodec protobufCodec = new ProtobufCodec(stuffDataClass);
        protobufCodec.addBlacklist(stuffDataClass);
        ByteBuf buf = protobufCodec.getValueEncoder().encode(getStuffData());
        byte[] jsonBytes = new byte[buf.readableBytes()];
        buf.readBytes(jsonBytes);
        Assertions.assertTrue(isValidJson(new String(jsonBytes)));

        //replace default blacklistCodec with Kryo5Codec
        Kryo5Codec kryo5Codec = new Kryo5Codec();
        protobufCodec = new ProtobufCodec(String.class, kryo5Codec);
        LinkedHashSet<String> v11 = new LinkedHashSet<>();
        v11.add("123");
        ByteBuf v1 = protobufCodec.getValueEncoder().encode(v11);
        LinkedHashSet<String> v11_1 = (LinkedHashSet<String>) kryo5Codec.getValueDecoder().decode(v1, null);
        Assertions.assertTrue(v11_1.size() == 1 && v11_1.contains("123"));

        //illegal blacklistCodec
        Assertions.assertThrows(IllegalArgumentException.class
                , () -> new ProtobufCodec(stuffDataClass, new ProtobufCodec(stuffDataClass))
                , "BlacklistCodec can not be ProtobufCodec");
    }

    private <T> T deserializeProtobufBytes(byte[] data, Class<T> clazz) {
        Schema<T> schema = RuntimeSchema.getSchema(clazz);
        T obj = schema.newMessage();
        ProtostuffIOUtil.mergeFrom(data, obj, schema);
        return obj;
    }

    @NotNull
    private StuffData getStuffData() {
        final StuffData stuffData = new StuffData();
        stuffData.setAge(18);
        List<String> hobbies = new ArrayList<>();
        hobbies.add("game");
        hobbies.add("game");
        stuffData.setHobbies(hobbies);
        stuffData.setName("ccc");
        return stuffData;
    }

    private boolean isValidJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

}
