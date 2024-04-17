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
package org.redisson.liveobject.misc;

import jodd.bean.BeanUtil;
import jodd.bean.BeanUtilBean;
import jodd.bean.BeanVisitor;

import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public final class AdvBeanCopy {

    private final Object source;
    private final Object destination;

    public AdvBeanCopy(Object source, Object destination) {
        this.source = source;
        this.destination = destination;
    }
    
    public void copy(List<String> excludedFields) {
        BeanUtil beanUtil = new BeanUtilBean();

        new BeanVisitor(source)
                .ignoreNulls(true)
                .visit((name, value) -> {
                    if (excludedFields.contains(name)) {
                        return;
                    }

                    beanUtil.setProperty(destination, name, value);
                });
    }

}
