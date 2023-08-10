/**
 * Copyright (c) 2013-2022 Nikita Koksharov
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.annotation;

import org.redisson.spring.type.LockTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author 985492783@qq.com
 * @description RLock annotation.
 * @date 2023/8/9 13:55
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RMutexLock {
    /**
     * empty placeholder.
     */
    String placeholder = "@@empty";

    String name() default placeholder;

    long waitTime() default -1L;

    long leaseTime() default -1L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    LockTypeEnum type() default LockTypeEnum.SIMPLE;
}
