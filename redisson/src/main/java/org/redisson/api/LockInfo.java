/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

import org.redisson.connection.ConnectionManager;

import java.util.Objects;

/**
 * This class present information about lock information
 *
 * @author Sergey Kurenchuk
 */
public class LockInfo {

    private final String connectionId;
    private final Long threadOwnerId;
    private final Long expired;

    public LockInfo(String connectionId, Long threadOwnerId, Long expired) {
        this.connectionId = connectionId;
        this.threadOwnerId = threadOwnerId;
        this.expired = expired;
    }

    /**
     * Thread owner id, see {@link RLockReactive#lock(long)}
     * @return thread id
     */
    public Long getThreadOwnerId() {
        return threadOwnerId;
    }

    /**
     * Connection id, see {@link ConnectionManager#getId()}
     * @return connection id
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * @return current ttl
     */
    public Long getExpired() {
        return expired;
    }

    /**
     * @return boolean value if lock already acquired
     */
    public boolean isLocked() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LockInfo lockInfo = (LockInfo) o;
        return Objects.equals(getConnectionId(), lockInfo.getConnectionId()) && getThreadOwnerId().equals(lockInfo.getThreadOwnerId()) && Objects.equals(getExpired(), lockInfo.getExpired());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getConnectionId(), getThreadOwnerId(), getExpired());
    }

    @Override
    public String toString() {
        return "LockInfo{" +
                "connectionId='" + connectionId + '\'' +
                ", threadOwnerId=" + threadOwnerId +
                ", expired=" + expired +
                '}';
    }

}