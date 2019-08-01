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
package org.redisson;

import java.io.Serializable;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RBitSet;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RDeque;
import org.redisson.api.RDequeReactive;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RHyperLogLogReactive;
import org.redisson.api.RLexSortedSet;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.api.RList;
import org.redisson.api.RListReactive;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.api.RQueue;
import org.redisson.api.RQueueReactive;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.annotation.REntity;
import org.redisson.client.codec.Codec;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.misc.BiHashMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReference implements Serializable {

    private static final long serialVersionUID = -2378564460151709127L;
    
    private static final BiHashMap<String, String> REACTIVE_MAP = new BiHashMap<String, String>();

    static {
        REACTIVE_MAP.put(RAtomicLongReactive.class.getName(),         RAtomicLong.class.getName());
        REACTIVE_MAP.put(RBitSetReactive.class.getName(),             RBitSet.class.getName());
        REACTIVE_MAP.put(RBlockingQueueReactive.class.getName(),      RBlockingQueue.class.getName());
        REACTIVE_MAP.put(RBucketReactive.class.getName(),             RBucket.class.getName());
        REACTIVE_MAP.put(RDequeReactive.class.getName(),              RDeque.class.getName());
        REACTIVE_MAP.put(RHyperLogLogReactive.class.getName(),        RHyperLogLog.class.getName());
        REACTIVE_MAP.put(RLexSortedSetReactive.class.getName(),       RLexSortedSet.class.getName());
        REACTIVE_MAP.put(RListReactive.class.getName(),               RList.class.getName());
        REACTIVE_MAP.put(RMapCacheReactive.class.getName(),           RMapCache.class.getName());
        REACTIVE_MAP.put(RMapReactive.class.getName(),                RMap.class.getName());
        REACTIVE_MAP.put(RQueueReactive.class.getName(),              RQueue.class.getName());
        REACTIVE_MAP.put(RScoredSortedSetReactive.class.getName(),    RScoredSortedSet.class.getName());
        REACTIVE_MAP.put(RSetCacheReactive.class.getName(),           RSetCache.class.getName());
        REACTIVE_MAP.put(RSetReactive.class.getName(),                RSet.class.getName());

        REACTIVE_MAP.makeImmutable();
    }

    public static void warmUp() {}

    private String type;
    private String keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class<?> type, String keyName) {
        this(type, keyName, null);
    }

    public RedissonReference(Class<?> type, String keyName, Codec codec) {
        if (!ClassUtils.isAnnotationPresent(type, REntity.class) && !RObject.class.isAssignableFrom(type) && !RObjectReactive.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject or RLiveObject or RObjectReactive");
        }
        if (RObjectReactive.class.isAssignableFrom(type)) {
            this.type = REACTIVE_MAP.get(type.getName());
        } else {
            this.type = type.getName();
        }
        this.keyName = keyName;
        if (codec != null) {
            this.codec = codec.getClass().getName();
        }
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
        if (REACTIVE_MAP.containsValue(type)) {
            return Class.forName(REACTIVE_MAP.reverseGet(type)); //live object is not supported in reactive client
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
        if (!ClassUtils.isAnnotationPresent(type, REntity.class) 
                && (!RObject.class.isAssignableFrom(type) || !RObjectReactive.class.isAssignableFrom(type))) {
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
        if (codec != null) {
            return (Class<? extends Codec>) Class.forName(codec);
        }
        return null;
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
