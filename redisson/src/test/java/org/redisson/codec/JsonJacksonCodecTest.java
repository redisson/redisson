package org.redisson.codec;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonJacksonCodecTest {

    static class Bean1599 {
       public int id;
       public Object obj;
    }
    
    @Test
    public void test() {
        Assertions.assertThrows(JsonMappingException.class, () -> {
            String JSON =
                    "{'id': 124,\n" +
                            " 'obj':[ 'com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl',\n" +
                            "  {\n" +
                            "    'transletBytecodes' : [ 'AAIAZQ==' ],\n" +
                            "    'transletName' : 'a.b',\n" +
                            "    'outputProperties' : { }\n" +
                            "  }\n" +
                            " ]\n" +
                            "}";
            JSON = JSON.replace("'", "\"");

            JsonJacksonCodec codec = new JsonJacksonCodec();
            codec.getObjectMapper().readValue(JSON, Bean1599.class);
        });
    }

    @Test
    public void shouldNotOverrideProvidedObjectMapperProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);
        JsonJacksonCodec codec = new JsonJacksonCodec(objectMapper);

        Assertions.assertTrue(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        Assertions.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        Assertions.assertFalse(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        Assertions.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }
}
