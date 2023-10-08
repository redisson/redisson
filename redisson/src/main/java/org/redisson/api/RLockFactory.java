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
package org.redisson.api;

/**
 * @author 985492783@qq.com
 * @description RLockFactory
 * @date 2023/8/10 15:30
 */
public class RLockFactory {

    private final RedissonClient redisson;

    public RLockFactory(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public RLock createLock(String key, LockTypeEnum type) {
        switch (type) {
            case SPIN:
                return redisson.getSpinLock(key);
            case READ:
                return redisson.getReadWriteLock(key).readLock();
            case WRITE:
                return redisson.getReadWriteLock(key).writeLock();
            case FENCE:
                return redisson.getFencedLock(key);
            case FAIR:
                return redisson.getFairLock(key);
            case SIMPLE:
            default:
                return redisson.getLock(key);
        }
    }
}
