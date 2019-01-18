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
package org.redisson.liveobject.core;

import java.lang.reflect.Method;

import org.redisson.api.RMap;
import org.redisson.liveobject.misc.ClassUtils;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class FieldAccessorInterceptor {

    @RuntimeType
    public static Object intercept(
            @Origin Method method,
            @AllArguments Object[] args,
            @This Object me,
            @FieldValue("liveObjectLiveMap") RMap<?, ?> map
    ) throws Exception {
        if (args.length >= 1 && String.class.isAssignableFrom(args[0].getClass())) {
            String name = ((String) args[0]).substring(0, 1).toUpperCase() + ((String) args[0]).substring(1);
            if ("get".equals(method.getName()) && args.length == 1) {
                try {
                    return me.getClass().getMethod("get" + name).invoke(me);
                } catch (NoSuchMethodException noSuchMethodException) {
                    throw new NoSuchFieldException((String) args[0]);
                }
            } else if ("set".equals(method.getName()) && args.length == 2) {
                Method m = ClassUtils.searchForMethod(me.getClass(), "set" + name, new Class[]{args[1].getClass()});
                if (m != null) {
                    return m.invoke(me, args[1]);
                } else {
                    throw new NoSuchFieldException((String) args[0]);
                }
            }
        }
        throw new NoSuchMethodException(method.getName() + " has wrong signature");

    }
}
