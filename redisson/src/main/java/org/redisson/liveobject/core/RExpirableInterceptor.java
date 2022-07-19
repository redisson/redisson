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
package org.redisson.liveobject.core;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.RExpirable;
import org.redisson.api.RMap;
import org.redisson.api.RMultimap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.annotation.RIndex;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

import java.lang.reflect.Method;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RExpirableInterceptor {

    private final CommandAsyncExecutor commandExecutor;

    public RExpirableInterceptor(CommandAsyncExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @RuntimeType
    public Object intercept(
            @This Object me,
            @Origin Method method,
            @AllArguments Object[] args,
            @FieldValue("liveObjectLiveMap") RMap<String, Object> map
    ) throws Exception {
        Class<?>[] cls = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            cls[i] = args[i].getClass();
        }


        Method m = ClassUtils.searchForMethod(RExpirable.class, method.getName(), cls);
        FieldList<FieldDescription.InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(me.getClass().getSuperclass(), RIndex.class);
        if (!fields.isEmpty()) {
            FieldList<FieldDescription.InDefinedShape> numberFields = fields.filter(ElementMatchers.fieldType(
                                                                                                    ElementMatchers.isSubTypeOf(Number.class).
                                                                                                    or(ElementMatchers.anyOf(int.class, long.class, byte.class, short.class, double.class, float.class))));
            FieldList<FieldDescription.InDefinedShape> nonNumberFields = fields.filter(ElementMatchers.fieldType(ElementMatchers.not(ElementMatchers.isSubTypeOf(Number.class))
                                                                                                            .and(ElementMatchers.not(ElementMatchers.anyOf(int.class, long.class, byte.class, short.class, double.class, float.class)))));
            Class<?> rEntity = me.getClass().getSuperclass();
            NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(rEntity);

            for (FieldDescription.InDefinedShape field : numberFields) {
                String indexName = namingScheme.getIndexName(rEntity, field.getName());
                RScoredSortedSet<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), commandExecutor, indexName, null);
                m.invoke(set, args);
            }

            for (FieldDescription.InDefinedShape field : nonNumberFields) {
                String indexName = namingScheme.getIndexName(rEntity, field.getName());
                RMultimap<Object, Object> idsMultimap = new RedissonSetMultimap<>(namingScheme.getCodec(), commandExecutor, indexName);
                m.invoke(idsMultimap, args);
            }
        }

        return m.invoke(map, args);
    }
}
