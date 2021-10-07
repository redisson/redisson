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

import io.quarkus.arc.DefaultBean;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ApplicationScoped
public class RedissonClientProducer {

    private String config;
    private RedissonClient redisson;

    @Produces
    @Singleton
    @DefaultBean
    public RedissonClient create() throws IOException {
        if (config != null){
            Config c = Config.fromYAML(config);
            redisson = Redisson.create(c);
        } else {
            redisson = Redisson.create();
        }
        return redisson;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    @PreDestroy
    public void close() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

}
