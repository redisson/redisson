package org.redisson.codec;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class JsonJacksonCodecTest {

    static class Bean1599 {
       public int id;
       public Object obj;
    }
    
    @Test(expected = JsonMappingException.class)
    public void test() throws JsonParseException, JsonMappingException, IOException {
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
        Assert.fail("Should not pass");
    }

    @Test
    public void shouldNotOverrideProvidedObjectMapperProperties() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);
        JsonJacksonCodec codec = new JsonJacksonCodec(objectMapper);

        Assert.assertTrue(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        Assert.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        Assert.assertFalse(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        Assert.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }
}
