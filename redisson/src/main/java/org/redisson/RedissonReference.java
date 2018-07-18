/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson;

import java.io.Serializable;
import org.redisson.client.codec.Codec;
import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.api.annotation.REntity;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.misc.BiHashMap;
import org.redisson.reactive.RedissonAtomicLongReactive;
import org.redisson.reactive.RedissonBitSetReactive;
import org.redisson.reactive.RedissonBlockingQueueReactive;
import org.redisson.reactive.RedissonBucketReactive;
import org.redisson.reactive.RedissonDequeReactive;
import org.redisson.reactive.RedissonHyperLogLogReactive;
import org.redisson.reactive.RedissonLexSortedSetReactive;
import org.redisson.reactive.RedissonListReactive;
import org.redisson.reactive.RedissonMapCacheReactive;
import org.redisson.reactive.RedissonMapReactive;
import org.redisson.reactive.RedissonQueueReactive;
import org.redisson.reactive.RedissonScoredSortedSetReactive;
import org.redisson.reactive.RedissonSetCacheReactive;
import org.redisson.reactive.RedissonSetReactive;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReference implements Serializable {

    private static final BiHashMap<String, String> reactiveMap = new BiHashMap<String, String>();

    static {
        reactiveMap.put(RedissonAtomicLongReactive.class.getName(),         RedissonAtomicLong.class.getName());
        reactiveMap.put(RedissonBitSetReactive.class.getName(),             RedissonBitSet.class.getName());
        reactiveMap.put(RedissonBlockingQueueReactive.class.getName(),      RedissonBlockingQueue.class.getName());
        reactiveMap.put(RedissonBucketReactive.class.getName(),             RedissonBucket.class.getName());
        reactiveMap.put(RedissonDequeReactive.class.getName(),              RedissonDeque.class.getName());
        reactiveMap.put(RedissonHyperLogLogReactive.class.getName(),        RedissonHyperLogLog.class.getName());
        reactiveMap.put(RedissonLexSortedSetReactive.class.getName(),       RedissonLexSortedSet.class.getName());
        reactiveMap.put(RedissonListReactive.class.getName(),               RedissonList.class.getName());
        reactiveMap.put(RedissonMapCacheReactive.class.getName(),           RedissonMapCache.class.getName());
        reactiveMap.put(RedissonMapReactive.class.getName(),                RedissonMap.class.getName());
        reactiveMap.put(RedissonQueueReactive.class.getName(),              RedissonQueue.class.getName());
        reactiveMap.put(RedissonScoredSortedSetReactive.class.getName(),    RedissonScoredSortedSet.class.getName());
        reactiveMap.put(RedissonSetCacheReactive.class.getName(),           RedissonSetCache.class.getName());
        reactiveMap.put(RedissonSetReactive.class.getName(),                RedissonSet.class.getName());

        reactiveMap.makeImmutable();
    }

    public static void warmUp() {}

    private String type;
    private String keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class type, String keyName) {
        this(type, keyName, null);
    }

    public RedissonReference(Class type, String keyName, Codec codec) {
        if (!ClassUtils.isAnnotationPresent(type, REntity.class) && !RObject.class.isAssignableFrom(type) && !RObjectReactive.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject or RLiveObject or RObjectReactive");
        }
        this.type = RObjectReactive.class.isAssignableFrom(type)
                ? reactiveMap.get(type.getName())
                : type.getName();
        this.keyName = keyName;
        this.codec = codec != null ? codec.getClass().getName() : null;
    }

    /**
     * @return the type
     * @throws java.lang.Exception - which could be:
     *     LinkageError - if the linkage fails
     *     ExceptionInInitializerError - if the initialization provoked by this method fails
     *     ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getType() throws Exception {
        return Class.forName(type);
    }

    /**
     * @return the type
     * @throws java.lang.Exception - which could be:
     *     LinkageError - if the linkage fails
     *     ExceptionInInitializerError - if the initialization provoked by this method fails
     *     ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getReactiveType() throws Exception {
        if (reactiveMap.containsValue(type)) {
            return Class.forName(reactiveMap.reverseGet(type));//live object is not supported in reactive client
        }
        throw new ClassNotFoundException("There is no Reactive compatible type for " + type);
    }

    /**
     * @return type name in string
     */
    public String getTypeName() {
        return type;
    }

    /**
     * @return type name in string
     */
    public String getReactiveTypeName() {
        return type + "Reactive";
    }

    /**
     * @param type the type to set
     */
    public void setType(Class<?> type) {
        if (!ClassUtils.isAnnotationPresent(type, REntity.class) && (!RObject.class.isAssignableFrom(type) || !RObjectReactive.class.isAssignableFrom(type))) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject or RLiveObject or RObjectReactive");
        }
        this.type = type.getName();
    }

    /**
     * @return the keyName
     */
    public String getKeyName() {
        return keyName;
    }

    /**
     * @param keyName the keyName to set
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
    
    public String getCodec() {
        return codec;
    }

    /**
     * @return the codec
     * @throws java.lang.Exception - which could be:
     *     LinkageError - if the linkage fails
     *     ExceptionInInitializerError - if the initialization provoked by this method fails
     *     ClassNotFoundException - if the class cannot be located 
     */
    public Class<? extends Codec> getCodecType() throws Exception {
        return (Class<? extends Codec>) (codec == null
                ? null
                : Class.forName(codec));
    }

    /**
     * @return Codec name in string
     */
    public String getCodecName() {
        return codec;
    }

    /**
     * @param codec the codec to set
     */
    public void setCodecType(Class<? extends Codec> codec) {
        this.codec = codec.getName();
    }
    
}
