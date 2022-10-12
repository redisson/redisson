/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.annotation.REntity;
import org.redisson.client.codec.Codec;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.misc.BiHashMap;

import java.io.Serializable;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class RedissonReference implements Serializable {

    private static final long serialVersionUID = -2378564460151709127L;
    
    private static final BiHashMap<String, String> REACTIVE_MAP = new BiHashMap<>();
    private static final BiHashMap<String, String> RXJAVA_MAP = new BiHashMap<>();

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

        RXJAVA_MAP.put(RAtomicLongRx.class.getName(),         RAtomicLong.class.getName());
        RXJAVA_MAP.put(RBitSetRx.class.getName(),             RBitSet.class.getName());
        RXJAVA_MAP.put(RBlockingQueueRx.class.getName(),      RBlockingQueue.class.getName());
        RXJAVA_MAP.put(RBucketRx.class.getName(),             RBucket.class.getName());
        RXJAVA_MAP.put(RDequeRx.class.getName(),              RDeque.class.getName());
        RXJAVA_MAP.put(RHyperLogLogRx.class.getName(),        RHyperLogLog.class.getName());
        RXJAVA_MAP.put(RLexSortedSetRx.class.getName(),       RLexSortedSet.class.getName());
        RXJAVA_MAP.put(RListRx.class.getName(),               RList.class.getName());
        RXJAVA_MAP.put(RMapCacheRx.class.getName(),           RMapCache.class.getName());
        RXJAVA_MAP.put(RMapRx.class.getName(),                RMap.class.getName());
        RXJAVA_MAP.put(RQueueRx.class.getName(),              RQueue.class.getName());
        RXJAVA_MAP.put(RScoredSortedSetRx.class.getName(),    RScoredSortedSet.class.getName());
        RXJAVA_MAP.put(RSetCacheRx.class.getName(),           RSetCache.class.getName());
        RXJAVA_MAP.put(RSetRx.class.getName(),                RSet.class.getName());

        RXJAVA_MAP.makeImmutable();
    }

    public static void warmUp() {}

    public enum ReferenceType {RXJAVA, REACTIVE, DEFAULT}

    private String type;
    private String keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class<?> type, String keyName) {
        this(type, keyName, null);
    }

    public RedissonReference(Class<?> type, String keyName, Codec codec) {
        if (!ClassUtils.isAnnotationPresent(type, REntity.class)
                && !RObject.class.isAssignableFrom(type)
                    && !RObjectReactive.class.isAssignableFrom(type)
                        && !RObjectRx.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject/RLiveObject/RObjectReactive/RObjectRx");
        }
        if (RObjectReactive.class.isAssignableFrom(type)) {
            this.type = REACTIVE_MAP.get(type.getName());
            if (this.type == null) {
                throw new IllegalArgumentException("There is no Reactive compatible type for " + type);
            }
        } else if (RObjectRx.class.isAssignableFrom(type)) {
            this.type = RXJAVA_MAP.get(type.getName());
            if (this.type == null) {
                throw new IllegalArgumentException("There is no RxJava compatible type for " + type);
            }
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
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getType() throws ClassNotFoundException {
        return Class.forName(type);
    }

    public Class<?> getRxJavaType() throws ClassNotFoundException {
        if (RXJAVA_MAP.containsValue(type)) {
            return Class.forName(RXJAVA_MAP.reverseGet(type)); //live object is not supported in reactive client
        }
        throw new ClassNotFoundException("There is no RxJava compatible type for " + type);
    }

    /**
     * @return the type
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getReactiveType() throws ClassNotFoundException {
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
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<? extends Codec> getCodecType() throws ClassNotFoundException {
        if (codec != null) {
            return (Class<? extends Codec>) Class.forName(codec);
        }
        return null;
    }
}
