/**
 * Copyright (c) 2013-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Avro binary codec
 *
 * @author Nikita Koksharov
 *
 */
public class AvroJacksonCodec extends JsonJacksonCodec {

    public static class AvroExtendedMapper extends AvroMapper {

        private static final long serialVersionUID = -560070554221164163L;

        private final AvroSchema schema;
        private final Class<?> type;
        
        public AvroExtendedMapper(Class<?> type, AvroSchema schema) {
            super();
            this.type = type;
            this.schema = schema;
        }
        
        @Override
        public AvroMapper copy() {
            _checkInvalidCopy(AvroExtendedMapper.class);
            return new AvroExtendedMapper(type, schema);
        }

        @Override
        public void writeValue(OutputStream out, Object value)
                throws IOException, JsonGenerationException, JsonMappingException {
            writerFor(type).with(schema).writeValue(out, value);
        }
        @Override
        public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
            return writerFor(type).with(schema).writeValueAsBytes(value);
        }
        
        @Override
        public <T> T readValue(InputStream src, Class<T> valueType)
                throws IOException, JsonParseException, JsonMappingException {
            return readerFor(type).with(schema).readValue(src);
        } 
        
    }

    public AvroJacksonCodec(Class<?> type, AvroSchema schema) {
        super(new AvroExtendedMapper(type, schema));
    }
    
    public AvroJacksonCodec(ClassLoader classLoader) {
        super(createObjectMapper(classLoader, new ObjectMapper(new AvroFactory())));
    }
    
    public AvroJacksonCodec(ClassLoader classLoader, AvroJacksonCodec codec) {
        super(createObjectMapper(classLoader, codec.mapObjectMapper.copy()));
    }
    
    @Override
    protected void initTypeInclusion(ObjectMapper mapObjectMapper) {
    }
    
}
