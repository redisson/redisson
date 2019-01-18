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
package org.redisson.client.protocol.convertor;

import java.math.BigDecimal;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class NumberConvertor implements Convertor<Object> {

    private Class<?> resultClass;

    public NumberConvertor(Class<?> resultClass) {
        super();
        this.resultClass = resultClass;
    }

    @Override
    public Object convert(Object result) {
        String res = (String) result;
        if (resultClass.isAssignableFrom(Long.class)) {
            Object obj = Long.parseLong(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Integer.class)) {
            Object obj = Integer.parseInt(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Float.class)) {
            Object obj = Float.parseFloat(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Double.class)) {
            Object obj = Double.parseDouble(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(BigDecimal.class)) {
            Object obj = new BigDecimal(res);
            return obj;
        }
        throw new IllegalStateException("Wrong value type!");
    }

}
