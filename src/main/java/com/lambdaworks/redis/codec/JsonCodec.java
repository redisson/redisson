package com.lambdaworks.redis.codec;

import java.nio.ByteBuffer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

public class JsonCodec extends RedisCodec<Object, Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonCodec() {
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setVisibilityChecker(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                            .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                            .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

        // type info inclusion
        TypeResolverBuilder<?> typer = new DefaultTypeResolverBuilder(DefaultTyping.NON_FINAL);
        typer.init(JsonTypeInfo.Id.CLASS, null);
        typer.inclusion(JsonTypeInfo.As.PROPERTY);
        objectMapper.setDefaultTyping(typer);
    }

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return decode(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return decode(bytes);
    }

    private Object decode(ByteBuffer bytes) {
        try {
            return objectMapper.readValue(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.limit(), Object.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] encodeKey(Object key) {
        return encodeValue(key);
    }

    @Override
    public byte[] encodeValue(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
