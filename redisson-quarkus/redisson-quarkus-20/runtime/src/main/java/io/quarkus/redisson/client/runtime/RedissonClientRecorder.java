/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package io.quarkus.redisson.client.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Recorder
public class RedissonClientRecorder {

    public void configureRedisson(String config) {
        RedissonClientProducer producer = Arc.container().instance(RedissonClientProducer.class).get();
        producer.setConfig(config);
    }

}
