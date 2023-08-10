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

package org.redisson.spring.processor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.spring.annotation.RMutexLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author qiyue.zhang@aloudata.com
 * @description RFaireLockAspect
 * @date 2023/8/9 13:57
 */

@Aspect
public class RMutexLockAspect {

    private final Logger logger = LoggerFactory.getLogger(RMutexLockAspect.class);

    private RedissonClient redisson;

    public RMutexLockAspect(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Pointcut("@annotation(org.redisson.spring.annotation.RMutexLock)")
    public void pointcut() {
    }

    @Around(value = "pointcut() && @annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, RMutexLock lock) throws Throwable {
        RLock redissonLock = redisson.getSpinLock(lock.key());
        boolean isLock = false;
        try {
            isLock = redissonLock.tryLock(lock.waitTime(), lock.timeUnit());
            if (isLock) {
                return pjp.proceed();
            }
        } finally {
            if (isLock) {
                redissonLock.unlock();
            }
        }
        throw new RedisException("lock fail!");
    }
}
