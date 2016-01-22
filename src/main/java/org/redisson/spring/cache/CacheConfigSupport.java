/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.spring.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CacheConfigSupport {

    ObjectMapper mapper = new ObjectMapper();

    public Map<String, CacheConfig> fromJSON(String content) throws IOException {
        return mapper.readValue(content, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(File file) throws IOException {
        return mapper.readValue(file, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(URL url) throws IOException {
        return mapper.readValue(url, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(Reader reader) throws IOException {
        return mapper.readValue(reader, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(InputStream inputStream) throws IOException {
        return mapper.readValue(inputStream, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public String toJSON(Map<String, CacheConfig> configs) throws IOException {
        return mapper.writeValueAsString(configs);
    }

}
