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

import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.api.RMultimapAsync;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RIndex;
import org.redisson.client.RedisException;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class LiveObjectInterceptor {

    public interface Getter {

        Object getValue();
    }

    public interface Setter {

        void setValue(Object value);
    }

    private final RedissonClient redisson;
    private final Class<?> originalClass;
    private final String idFieldName;
    private final Class<?> idFieldType;
    private final NamingScheme namingScheme;
    private final RedissonObjectBuilder objectBuilder;

    public LiveObjectInterceptor(RedissonClient redisson, Class<?> entityClass, String idFieldName, RedissonObjectBuilder objectBuilder) {
        this.redisson = redisson;
        this.originalClass = entityClass;
        this.idFieldName = idFieldName;
        this.objectBuilder = objectBuilder;
        
        namingScheme = objectBuilder.getNamingScheme(entityClass);
        
        try {
            this.idFieldType = ClassUtils.getDeclaredField(originalClass, idFieldName).getType();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @RuntimeType
    public Object intercept(
            @Origin Method method,
            @AllArguments Object[] args,
            @This Object me,
            @FieldValue("liveObjectId") Object id,
            @FieldProxy("liveObjectId") Setter idSetter,
            @FieldProxy("liveObjectId") Getter idGetter,
            @FieldValue("liveObjectLiveMap") RMap<?, ?> map,
            @FieldProxy("liveObjectLiveMap") Setter mapSetter,
            @FieldProxy("liveObjectLiveMap") Getter mapGetter
    ) throws Exception {
        if ("setLiveObjectId".equals(method.getName())) {
            if (args[0].getClass().isArray()) {
                throw new UnsupportedOperationException("RId value cannot be an array.");
            }
            //TODO: distributed locking maybe required.
            String idKey = getMapKey(args[0]);
            if (map != null) {
                if (map.getName().equals(idKey)) {
                    return null;
                }
                try {
                    map.rename(getMapKey(args[0]));
                } catch (RedisException e) {
                    if (e.getMessage() == null || !e.getMessage().startsWith("ERR no such key")) {
                        throw e;
                    }
                    //key may already renamed by others.
                }
            }
            RMap<Object, Object> liveMap = redisson.getMap(idKey, namingScheme.getCodec());
            mapSetter.setValue(liveMap);

            return null;
        }

        if ("getLiveObjectId".equals(method.getName())) {
            if (map == null) {
                return null;
            }
            return namingScheme.resolveId(map.getName());
        }

        if ("getLiveObjectLiveMap".equals(method.getName())) {
            return map;
        }

        if ("isExists".equals(method.getName())) {
            return map.isExists();
        }
        
        if ("delete".equals(method.getName())) {
            FieldList<InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(me.getClass().getSuperclass(), RIndex.class);
            RBatch batch = redisson.createBatch();
            for (InDefinedShape field : fields) {
                String fieldName = field.getName();
                Object value = map.get(fieldName);
                NamingScheme namingScheme = objectBuilder.getNamingScheme(me.getClass().getSuperclass());
                String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), fieldName);
                RMultimapAsync<Object, Object> idsMultimap = batch.getSetMultimap(indexName, namingScheme.getCodec());
                idsMultimap.removeAsync(value, ((RLiveObject) me).getLiveObjectId());
            }
            RFuture<Long> deleteFuture = batch.getKeys().deleteAsync(map.getName());
            batch.execute();
            
            return deleteFuture.getNow() > 0;
        }

        throw new NoSuchMethodException();
    }

    private String getMapKey(Object id) {
        return namingScheme.getName(originalClass, idFieldType, idFieldName, id);
    }

}
