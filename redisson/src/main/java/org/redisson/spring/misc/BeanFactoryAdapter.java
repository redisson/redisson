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
package org.redisson.spring.misc;

import org.redisson.TaskInjector;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

/**
 * Apdapts to Spring BeanFactory instance to execute tasks with Spring's '@Autowired',
 * '@Value' or JSR-330's '@Inject' annotation.
 *
 */
public class BeanFactoryAdapter implements TaskInjector {

    private final AutowiredAnnotationBeanPostProcessor bpp;

    /**
     *
     * @param beanFactory - Spring BeanFactory instance
     * @return self instance
     */
    public static TaskInjector create(BeanFactory beanFactory){
        return new BeanFactoryAdapter(beanFactory);
    }

    private BeanFactoryAdapter(BeanFactory factory){
        AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
        bpp.setBeanFactory(factory);
        this.bpp = bpp;
    }

    @Override
    public <T> void process(T task) {
        bpp.processInjection(task);
    }
}
