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
package org.redisson.config;

import org.redisson.TaskInjector;
import org.redisson.spring.misc.BeanFactoryAdapter;
import org.springframework.beans.factory.BeanFactory;

/**
 * Redisson Node configuration
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonNodeConfig extends RedissonNodeFileConfig {

    private TaskInjector injector;

    public RedissonNodeConfig() {
        super();
    }

    public RedissonNodeConfig(Config oldConf) {
        super(oldConf);
    }

    public RedissonNodeConfig(RedissonNodeConfig oldConf) {
        super(oldConf);
        this.injector = oldConf.injector;
    }

    public TaskInjector getTaskInjector() {
        return injector;
    }

    /**
     * Defines injector to execute tasks with annotation e.g. Spring's '@Autowired',
     * '@Value' or JSR-330's '@Inject' annotation.
     *
     * @see org.redisson.spring.misc.BeanFactoryAdapter
     * @param injector - a custom injector instance e.g. org.redisson.spring.misc.BeanFactoryAdapter
     */
    public void setTaskInjector(TaskInjector injector) {
        this.injector = injector;
    }

    /**
     * Defines Spring Bean Factory instance to execute tasks with Spring's '@Autowired',
     * '@Value' or JSR-330's '@Inject' annotation.
     *
     * @param beanFactory - Spring BeanFactory instance
     * @deprecated use {@link #setTaskInjector( TaskInjector)}
     */
    public void setBeanFactory(BeanFactory beanFactory) {
        this.injector = BeanFactoryAdapter.create(beanFactory);
    }

}
