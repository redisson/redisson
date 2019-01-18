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
package org.redisson.spring.misc;

import java.lang.reflect.InvocationTargetException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.support.ArgumentConvertingMethodInvoker;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class BeanMethodInvoker extends ArgumentConvertingMethodInvoker
        implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        prepare();
        try {
            invoke();
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof Exception) {
                throw (Exception) ex.getTargetException();
            }
            if (ex.getTargetException() instanceof Error) {
                throw (Error) ex.getTargetException();
            }
            throw ex;
        }
    }
}
