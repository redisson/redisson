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
package org.redisson.helidon;

import org.eclipse.microprofile.config.Config;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.PropertiesConvertor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;
import javax.inject.Named;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonExtension implements Extension {

    private final Set<Annotation> qualifiers = new HashSet<>();

    private <T extends RedissonClient> void processRedissonInjectionPoint(@Observes ProcessInjectionPoint<?, T> point) {
        if (point == null) {
            return;
        }
        InjectionPoint injectionPoint = point.getInjectionPoint();
        if (injectionPoint == null) {
            return;
        }

        qualifiers.addAll(injectionPoint.getQualifiers());
    }

    private void addBeans(@Observes AfterBeanDiscovery discovery, BeanManager beanManager) {
        if (discovery == null || beanManager == null) {
            return;
        }

        for (Annotation qualifier : qualifiers) {
            Set<Annotation> qualifiers = Collections.singleton(qualifier);

            discovery.addBean()
                .scope(ApplicationScoped.class)
                .addQualifiers(qualifiers)
                .addTransitiveTypeClosure(RedissonClient.class)
                .produceWith((instance) -> {

                    String instanceName = "default";
                    if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    }

                    Config cfg = instance.select(Config.class).get();
                    String yamlConfig = PropertiesConvertor.toYaml(Redisson.class.getName() + "." + instanceName + ".",
                            cfg.getPropertyNames(), prop -> {
                                return cfg.getValue(prop, String.class);
                    });

                    try {
                        org.redisson.config.Config config = org.redisson.config.Config.fromYAML(yamlConfig);
                        return Redisson.create(config);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
        }
    }


}
