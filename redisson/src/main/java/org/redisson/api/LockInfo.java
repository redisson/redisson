package org.redisson.api;

import java.util.Objects;

/**
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

    public Long getThreadOwnerId() {
        return threadOwnerId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Long getExpired() {
        return expired;
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
