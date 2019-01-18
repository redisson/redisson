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
/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.redisson.hibernate.strategy;

import java.io.Serializable;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.TransactionalDataRegion;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cfg.Settings;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;

/**
 * 
 * @author Nikita Koksharov
 * @author Strong Liu
 *
 */
public class AbstractReadWriteAccessStrategy extends BaseRegionAccessStrategy {

    private final UUID uuid = UUID.randomUUID();
    private final AtomicLong nextLockId = new AtomicLong();
    final RMapCache<Object, Object> mapCache;
    
    public AbstractReadWriteAccessStrategy(Settings settings, GeneralDataRegion region, RMapCache<Object, Object> mapCache) {
        super(settings, region);
        this.mapCache = mapCache;
    }

    @Override
    public Object get(SharedSessionContractImplementor session, Object key, long txTimestamp) throws CacheException {
        RLock readLock = mapCache.getReadWriteLock(key).readLock();
        readLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);

            if (item != null && item.isReadable(txTimestamp)) {
                return item.getValue();
            } else {
                return null;
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, long txTimestamp, Object version, boolean minimalPutOverride)
            throws CacheException {
        RLock writeLock = mapCache.getReadWriteLock(key).writeLock();
        writeLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);
            Comparator<Object> comparator = ((TransactionalDataRegion)region).getCacheDataDescription().getVersionComparator();
            boolean writeable = item == null || item.isWriteable(txTimestamp, version, comparator);
            if (writeable) {
                region.put(session, key, new Item(value, version, region.nextTimestamp()));
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) throws CacheException {
        RLock writeLock = mapCache.getReadWriteLock(key).writeLock();
        writeLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);
            long timeout = region.nextTimestamp() + region.getTimeout();
            
            Lock lock;
            if (item == null) {
                lock = new Lock(timeout, uuid, nextLockId.getAndIncrement(), version);
            } else {
                lock = item.lock(timeout, uuid, nextLockId.getAndIncrement());
            }
            region.put(session, key, lock);
            return lock;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) throws CacheException {
        RLock writeLock = mapCache.getReadWriteLock(key).writeLock();
        writeLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);

            if (item != null && item.isUnlockable(lock)) {
                decrementLock(session, key, (Lock)item);
            } else {
                handleLockExpiry(session, key, item);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Unlock and re-put the given key, lock combination.
     */
    protected void decrementLock(SharedSessionContractImplementor session, Object key, Lock lock) {
        lock.unlock(region.nextTimestamp());
        region.put(session, key, lock);
    }
    
    /**
     * Handle the timeout of a previous lock mapped to this key
     */
    protected void handleLockExpiry(SharedSessionContractImplementor session, Object key, Lockable lock) {
        long ts = region.nextTimestamp() + region.getTimeout();
        // create new lock that times out immediately
        Lock newLock = new Lock(ts, uuid, nextLockId.getAndIncrement(), null);
        newLock.unlock(ts);
        region.put(session, key, newLock);
    }
    
    /**
     * Interface type implemented by all wrapper objects in the cache.
     */
    public interface Lockable {

        /**
         * Returns <code>true</code> if the enclosed value can be read by a transaction started at the given time.
         */
        public boolean isReadable(long txTimestamp);

        /**
         * Returns <code>true</code> if the enclosed value can be replaced with one of the given version by a
         * transaction started at the given time.
         */
        public boolean isWriteable(long txTimestamp, Object version, Comparator<Object> versionComparator);

        /**
         * Returns the enclosed value.
         */
        public Object getValue();

        /**
         * Returns <code>true</code> if the given lock can be unlocked using the given SoftLock instance as a handle.
         */
        public boolean isUnlockable(SoftLock lock);

        /**
         * Locks this entry, stamping it with the UUID and lockId given, with the lock timeout occuring at the specified
         * time.  The returned Lock object can be used to unlock the entry in the future.
         */
        public Lock lock(long timeout, UUID uuid, long lockId);
    }

    /**
     * Wrapper type representing unlocked items.
     */
    public final static class Item implements Serializable, Lockable {

        private static final long serialVersionUID = 1L;

        private Object value;
        private Object version;
        private long timestamp;

        /**
         * Creates an unlocked item wrapping the given value with a version and creation timestamp.
         */
        public Item(Object value, Object version, long timestamp) {
            this.value = value;
            this.version = version;
            this.timestamp = timestamp;
        }

        @Override
        public boolean isReadable(long txTimestamp) {
            return txTimestamp > timestamp;
        }

        @Override
        public boolean isWriteable(long txTimestamp, Object newVersion, Comparator<Object> versionComparator) {
            return version != null && versionComparator.compare( version, newVersion ) < 0;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public boolean isUnlockable(SoftLock lock) {
            return false;
        }

        @Override
        public Lock lock(long timeout, UUID uuid, long lockId) {
            return new Lock(timeout, uuid, lockId, version);
        }
    }

    /**
     * Wrapper type representing locked items.
     */
    public final static class Lock implements Serializable, Lockable, SoftLock {

        private static final long serialVersionUID = 2L;

        private UUID sourceUuid;
        private long lockId;
        private Object version;

        private long timeout;
        private boolean concurrent;
        private int multiplicity = 1;
        private long unlockTimestamp;

        /**
         * Creates a locked item with the given identifiers and object version.
         */
        public Lock(long timeout, UUID sourceUuid, long lockId, Object version) {
            this.timeout = timeout;
            this.lockId = lockId;
            this.version = version;
            this.sourceUuid = sourceUuid;
        }

        @Override
        public boolean isReadable(long txTimestamp) {
            return false;
        }

        @Override
        public boolean isWriteable(long txTimestamp, Object newVersion, Comparator versionComparator) {
            if ( txTimestamp > timeout ) {
                // if timedout then allow write
                return true;
            }
            if ( multiplicity > 0 ) {
                // if still locked then disallow write
                return false;
            }
            return version == null ? txTimestamp > unlockTimestamp : versionComparator.compare(
                    version,
                    newVersion
            ) < 0;
        }

        @Override
        public Object getValue() {
            return null;
        }

        @Override
        public boolean isUnlockable(SoftLock lock) {
            return equals( lock );
        }

        @Override
        public boolean equals(Object o) {
            if ( o == this ) {
                return true;
            }
            else if ( o instanceof Lock ) {
                return ( lockId == ( (Lock) o ).lockId ) && sourceUuid.equals( ( (Lock) o ).sourceUuid );
            }
            else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hash = ( sourceUuid != null ? sourceUuid.hashCode() : 0 );
            int temp = (int) lockId;
            for ( int i = 1; i < Long.SIZE / Integer.SIZE; i++ ) {
                temp ^= ( lockId >>> ( i * Integer.SIZE ) );
            }
            return hash + temp;
        }

        /**
         * Returns true if this Lock has been concurrently locked by more than one transaction.
         */
        public boolean wasLockedConcurrently() {
            return concurrent;
        }

        @Override
        public Lock lock(long timeout, UUID uuid, long lockId) {
            concurrent = true;
            multiplicity++;
            this.timeout = timeout;
            return this;
        }

        /**
         * Unlocks this Lock, and timestamps the unlock event.
         */
        public void unlock(long timestamp) {
            if ( --multiplicity == 0 ) {
                unlockTimestamp = timestamp;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder( "Lock Source-UUID:" + sourceUuid + " Lock-ID:" + lockId );
            return sb.toString();
        }
    }
    

}
