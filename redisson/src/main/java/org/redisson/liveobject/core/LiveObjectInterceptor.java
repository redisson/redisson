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

import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.implementation.bind.annotation.*;
import org.redisson.RedissonKeys;
import org.redisson.RedissonMap;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.*;
import org.redisson.api.annotation.RIndex;
import org.redisson.client.RedisException;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

import java.lang.reflect.Method;

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

    private final CommandAsyncExecutor commandExecutor;
    private final ConnectionManager connectionManager;
    private final Class<?> originalClass;
    private final String idFieldName;
    private final Class<?> idFieldType;
    private final NamingScheme namingScheme;

    public LiveObjectInterceptor(CommandAsyncExecutor commandExecutor, ConnectionManager connectionManager, Class<?> entityClass, String idFieldName) {
        this.commandExecutor = commandExecutor;
        this.connectionManager = connectionManager;
        this.originalClass = entityClass;
        this.idFieldName = idFieldName;

        namingScheme = connectionManager.getCommandExecutor().getObjectBuilder().getNamingScheme(entityClass);

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

            RMap<Object, Object> liveMap = new RedissonMap<Object, Object>(namingScheme.getCodec(), commandExecutor,
                                                    idKey, null, null, null);
            mapSetter.setValue(liveMap);

            return null;
        }

        if ("getLiveObjectId".equals(method.getName())) {
            if (map == null) {
                return null;
            }
            return namingScheme.resolveId(map.getName());
        }

        if ("delete".equals(method.getName())) {
            FieldList<InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(me.getClass().getSuperclass(), RIndex.class);
            CommandBatchService ce;
            if (commandExecutor instanceof CommandBatchService) {
                ce = (CommandBatchService) commandExecutor;
            } else {
                ce = new CommandBatchService(connectionManager);
            }
            for (InDefinedShape field : fields) {
                String fieldName = field.getName();
                Object value = map.get(fieldName);

                NamingScheme namingScheme = connectionManager.getCommandExecutor().getObjectBuilder().getNamingScheme(me.getClass().getSuperclass());
                String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), fieldName);

                if (value instanceof Number) {
                    RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
                    set.removeAsync(((RLiveObject) me).getLiveObjectId());
                } else {
                    RMultimapAsync<Object, Object> idsMultimap = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                    idsMultimap.removeAsync(value, ((RLiveObject) me).getLiveObjectId());
                }
            }
            RFuture<Long> deleteFuture = new RedissonKeys(ce).deleteAsync(map.getName());
            ce.execute();
            
            return deleteFuture.getNow() > 0;
        }

        return method.invoke(map, args);
    }

    private String getMapKey(Object id) {
        return namingScheme.getName(originalClass, idFieldType, idFieldName, id);
    }

}
