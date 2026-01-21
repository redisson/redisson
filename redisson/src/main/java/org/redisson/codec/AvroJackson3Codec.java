/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.avro.AvroFactory;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Avro binary codec
 *
 * @author Nikita Koksharov
 *
 */
public class AvroJackson3Codec extends JsonJackson3Codec {

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
        public void writeValue(OutputStream out, Object value)
                throws JacksonException {
            writerFor(type).with(schema).writeValue(out, value);
        }
        @Override
        public byte[] writeValueAsBytes(Object value) throws JacksonException {
            return writerFor(type).with(schema).writeValueAsBytes(value);
        }

        @Override
        public <T> T readValue(InputStream src, Class<T> valueType)
                throws JacksonException {
            return readerFor(type).with(schema).readValue(src);
        }

    }

    public AvroJackson3Codec(Class<?> type, AvroSchema schema) {
        super(new AvroExtendedMapper(type, schema));
    }

    public AvroJackson3Codec(ClassLoader classLoader) {
        super(createMapper(classLoader, new ObjectMapper(new AvroFactory())));
    }

    public AvroJackson3Codec(ClassLoader classLoader, AvroJackson3Codec codec) {
        super(createMapper(classLoader, codec.mapObjectMapper.rebuild().build()));
    }

    @Override
    protected void initTypeInclusion(JsonMapper.Builder builder) {
    }

}
