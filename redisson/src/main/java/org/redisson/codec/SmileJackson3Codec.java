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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.SmileFactory;

/**
 * Smile binary codec.
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class SmileJackson3Codec extends JsonJackson3Codec {

    public SmileJackson3Codec() {
        super(new ObjectMapper(new SmileFactory()));
    }

    public SmileJackson3Codec(ClassLoader classLoader) {
        super(createMapper(classLoader, new ObjectMapper(new SmileFactory())));
    }

    public SmileJackson3Codec(ClassLoader classLoader, SmileJackson3Codec codec) {
        super(createMapper(classLoader, codec.mapObjectMapper.rebuild().build()));
    }
    
}
