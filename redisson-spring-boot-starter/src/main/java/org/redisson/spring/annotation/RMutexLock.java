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
package org.redisson.spring.annotation;

import org.redisson.api.annotation.RMethodLock;
import org.redisson.spring.starter.RedissonAspectConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author qiyue.zhang@aloudata.com
 * @description RFaireLock
 * @date 2023/8/9 13:55
 */
@RMethodLock
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Import({RedissonAspectConfiguration.class})
public @interface RMutexLock {

    @AliasFor(annotation = RMethodLock.class, value = "key")
    String key();

    long waitTime();

    TimeUnit timeUnit();
}
