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
package org.redisson.remote;

import java.lang.reflect.Method;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceMethod {

    private final Object bean;
    private final Method method;
    
    public RemoteServiceMethod(Method method, Object bean) {
        super();
        this.method = method;
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }
    
    public Method getMethod() {
        return method;
    }
    
}
