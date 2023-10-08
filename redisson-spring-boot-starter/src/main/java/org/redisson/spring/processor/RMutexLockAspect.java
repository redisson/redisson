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

package org.redisson.spring.processor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.client.RedisException;
import org.redisson.api.annotation.RMutexLock;
import org.redisson.api.RLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.redisson.api.annotation.RMutexLock.placeholder;

/**
 * @author 985492783@qq.com
 * @description Redis Mutex lock aspect.
 * @date 2023/8/9 13:57
 */

@Aspect
public class RMutexLockAspect {

    private final Logger logger = LoggerFactory.getLogger(RMutexLockAspect.class);

    private final String CONNECT_KEY = "@@";

    private RLockFactory factory;

    public RMutexLockAspect(RLockFactory factory) {
        this.factory = factory;
    }

    @Pointcut("@annotation(org.redisson.api.annotation.RMutexLock)")
    public void pointcut() {
    }

    /**
     * lock and unlock automatically.
     */
    @Around(value = "pointcut() && @annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, RMutexLock annotation) throws Throwable {
        String RLockName = annotation.name();
        if (placeholder.equals(RLockName)) {
            String className = pjp.getTarget().getClass().getCanonicalName();
            String methodName = pjp.getSignature().getName();
            RLockName = className + CONNECT_KEY + methodName;
        }
        RLock lock = factory.createLock(RLockName, annotation.type());
        boolean isLock = false;
        try {
            isLock = lock.tryLock(annotation.waitTime(), annotation.leaseTime(), annotation.timeUnit());
            if (isLock) {
                return pjp.proceed();
            }
        } finally {
            if (isLock) {
                lock.unlock();
            }
        }
        throw new RedisException("lock fail!");
    }
}
