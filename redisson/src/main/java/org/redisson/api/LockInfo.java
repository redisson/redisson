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
