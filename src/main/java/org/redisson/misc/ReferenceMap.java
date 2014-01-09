/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.misc;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent hash map that wraps keys and/or values in SOFT or WEAK references.
 * Does not support <code>null</code> keys or values. Uses identity equality for
 * weak and soft keys. Supports remove listener
 *
 * @author crazybob@google.com (Bob Lee)
 * @author fry@google.com (Charles Fry)
 * @author igor.spasic@gmail.com
 * @author Nikita Koksharov
 */
@SuppressWarnings("unchecked")
public class ReferenceMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {

    public interface RemoveValueListener<V> {

        void onRemove(V value);

    }

    public enum ReferenceType {

        /**
         * Prevents referent from being reclaimed by the garbage collector.
         */
        STRONG,

        /**
         * Referent reclaimed in an LRU fashion when the VM runs low on memory
         * and no strong references exist.
         *
         * @see java.lang.ref.SoftReference
         */
        SOFT,

        /**
         * Referent reclaimed when no strong or soft references exist.
         *
         * @see java.lang.ref.WeakReference
         */
        WEAK,

        /**
         * Similar to weak references except the garbage collector doesn't
         * actually reclaim the referent. More flexible alternative to
         * finalization.
         *
         * @see java.lang.ref.PhantomReference
         */
        PHANTOM,
    }

    protected ConcurrentMap<Object, Object> delegate;

    protected final ReferenceType keyReferenceType;
    protected final ReferenceType valueReferenceType;

    public ReferenceMap(ReferenceType keyReferenceType, ReferenceType valueReferenceType) {
        this(keyReferenceType, valueReferenceType, null);
    }

    /**
     * Concurrent hash map that wraps keys and/or values based on specified
     * reference types.
     *
     * @param keyReferenceType
     *            key reference type
     * @param valueReferenceType
     *            value reference type
     */
    public ReferenceMap(ReferenceType keyReferenceType, ReferenceType valueReferenceType, final RemoveValueListener<V> removeValueListener) {
        if ((keyReferenceType == null) || (valueReferenceType == null)) {
            throw new IllegalArgumentException("References types can not be null");
        }
        if (keyReferenceType == ReferenceType.PHANTOM || valueReferenceType == ReferenceType.PHANTOM) {
            throw new IllegalArgumentException("Phantom references not supported");
        }
        this.delegate = new ConcurrentHashMap<Object, Object>() {

            @Override
            public Object remove(Object key) {
                Object res = super.remove(key);
                if (res != null && removeValueListener != null) {
                    removeValueListener.onRemove((V)res);
                }
                return res;
            }

            @Override
            public boolean remove(Object key, Object value) {
                boolean res = super.remove(key, value);
                if (res && removeValueListener != null) {
                    removeValueListener.onRemove((V)value);
                }
                return res;
            }

        };
        this.keyReferenceType = keyReferenceType;
        this.valueReferenceType = valueReferenceType;
    }

    // ---------------------------------------------------------------- map
    // implementations

    @Override
    public V get(final Object key) {
        Object valueReference = delegate.get(makeKeyReferenceAware(key));
        return dereferenceValue(valueReference);
    }

    private V execute(Strategy strategy, K key, V value) {
        Object keyReference = referenceKey(key);
        return (V) strategy.execute(this, keyReference, referenceValue(keyReference, value));
    }

    @Override
    public V put(K key, V value) {
        return execute(PutStrategy.PUT, key, value);
    }

    @Override
    public V remove(Object key) {
        Object referenceAwareKey = makeKeyReferenceAware(key);
        Object valueReference = delegate.remove(referenceAwareKey);
        return dereferenceValue(valueReference);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        Object referenceAwareKey = makeKeyReferenceAware(key);
        return delegate.containsKey(referenceAwareKey);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Object valueReference : delegate.values()) {
            if (value.equals(dereferenceValue(valueReference))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    public V putIfAbsent(K key, V value) {
        return execute(PutStrategy.PUT_IF_ABSENT, key, value);
    }

    public boolean remove(Object key, Object value) {
        return delegate.remove(makeKeyReferenceAware(key), makeValueReferenceAware(value));
    }

    public boolean replace(K key, V oldValue, V newValue) {
        Object keyReference = referenceKey(key);
        Object referenceAwareOldValue = makeValueReferenceAware(oldValue);
        return delegate.replace(keyReference, referenceAwareOldValue, referenceValue(keyReference, newValue));
    }

    public V replace(K key, V value) {
        return execute(PutStrategy.REPLACE, key, value);
    }

    // ----------------------------------------------------------------
    // conversions

    /**
     * Dereferences an entry. Returns <code>null</code> if the key or value has
     * been gc'ed.
     */
    Entry dereferenceEntry(Map.Entry<Object, Object> entry) {
        K key = dereferenceKey(entry.getKey());
        V value = dereferenceValue(entry.getValue());
        return (key == null || value == null) ? null : new Entry(key, value);
    }

    /**
     * Creates a reference for a key.
     */
    Object referenceKey(K key) {
        switch (keyReferenceType) {
        case STRONG:
            return key;
        case SOFT:
            return new SoftKeyReference(key);
        case WEAK:
            return new WeakKeyReference(key);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Converts a reference to a key.
     */
    K dereferenceKey(Object o) {
        return (K) dereference(keyReferenceType, o);
    }

    /**
     * Converts a reference to a value.
     */
    V dereferenceValue(Object o) {
        if (o == null) {
            return null;
        }
        Object value = dereference(valueReferenceType, o);
        if (o instanceof InternalReference) {
            InternalReference reference = (InternalReference) o;
            if (value == null) {
                reference.finalizeReferent(); // old value was garbage collected
            }
        }
        return (V) value;
    }

    /**
     * Returns the refererent for reference given its reference type.
     */
    private Object dereference(ReferenceType referenceType, Object reference) {
        return referenceType == ReferenceType.STRONG ? reference : ((Reference) reference).get();
    }

    /**
     * Creates a reference for a value.
     */
    Object referenceValue(Object keyReference, Object value) {
        switch (valueReferenceType) {
        case STRONG:
            return value;
        case SOFT:
            return new SoftValueReference(keyReference, value);
        case WEAK:
            return new WeakValueReference(keyReference, value);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Wraps key so it can be compared to a referenced key for equality.
     */
    private Object makeKeyReferenceAware(Object o) {
        return keyReferenceType == ReferenceType.STRONG ? o : new KeyReferenceAwareWrapper(o);
    }

    /**
     * Wraps value so it can be compared to a referenced value for equality.
     */
    private Object makeValueReferenceAware(Object o) {
        return valueReferenceType == ReferenceType.STRONG ? o : new ReferenceAwareWrapper(o);
    }

    // ---------------------------------------------------------------- inner
    // classes

    /**
     * Marker interface to differentiate external and internal references. Also
     * duplicates FinalizableReference and Reference.get for internal use.
     */
    interface InternalReference {
        /**
         * Invoked on a background thread after the referent has been garbage
         * collected.
         */
        void finalizeReferent();

        Object get();
    }

    /**
     * Tests weak and soft references for identity equality. Compares references
     * to other references and wrappers. If o is a reference, this returns true
     * if r == o or if r and o reference the same non-null object. If o is a
     * wrapper, this returns true if r's referent is identical to the wrapped
     * object.
     */
    private static boolean referenceEquals(Reference r, Object o) {
        if (o instanceof InternalReference) { // compare reference to reference.
            if (o == r) { // are they the same reference? used in cleanup.
                return true;
            }
            Object referent = ((Reference) o).get(); // do they reference
                                                     // identical values? used
                                                     // in conditional puts.
            return referent != null && referent == r.get();
        }
        return ((ReferenceAwareWrapper) o).unwrap() == r.get(); // is the
                                                                // wrapped
                                                                // object
                                                                // identical to
                                                                // the referent?
                                                                // used in
                                                                // lookups.
    }

    /**
     * Returns <code>true</code> if the specified value reference has been
     * garbage collected. The value behind the reference is also passed in,
     * rather than queried inside this method, to ensure that the return
     * statement of this method will still hold true after it has returned (that
     * is, a value reference exists outside of this method which will prevent
     * that value from being garbage collected).
     *
     * @param valueReference
     *            the value reference to be tested
     * @param value
     *            the object referenced by <code>valueReference</code>
     * @return <code>true</code> if <code>valueReference</code> is non-null and
     *         <code>value</code> is <code>null</code>
     */
    private static boolean isExpired(Object valueReference, Object value) {
        return (valueReference != null) && (value == null);
    }

    /**
     * Big hack. Used to compare keys and values to referenced keys and values
     * without creating more references.
     */
    static class ReferenceAwareWrapper {
        final Object wrapped;

        ReferenceAwareWrapper(Object wrapped) {
            this.wrapped = wrapped;
        }

        Object unwrap() {
            return wrapped;
        }

        @Override
        public int hashCode() {
            return wrapped.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj.equals(this); // defer to references equals() logic.
        }
    }

    /**
     * Used for keys. Overrides hash code to use identity hash code.
     */
    static class KeyReferenceAwareWrapper extends ReferenceAwareWrapper {
        KeyReferenceAwareWrapper(Object wrapped) {
            super(wrapped);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(wrapped);
        }
    }

    class SoftKeyReference extends SoftReference<Object> implements InternalReference {
        final int hashCode;

        SoftKeyReference(Object key) {
            super(key, FinalizableReferenceQueue.getInstance());
            this.hashCode = System.identityHashCode(key);
        }

        public void finalizeReferent() {
            delegate.remove(this);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return referenceEquals(this, o);
        }
    }

    class WeakKeyReference extends WeakReference<Object> implements InternalReference {
        final int hashCode;

        WeakKeyReference(Object key) {
            super(key, FinalizableReferenceQueue.getInstance());
            this.hashCode = System.identityHashCode(key);
        }

        public void finalizeReferent() {
            delegate.remove(this);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return referenceEquals(this, o);
        }
    }

    class SoftValueReference extends SoftReference<Object> implements InternalReference {
        final Object keyReference;

        SoftValueReference(Object keyReference, Object value) {
            super(value, FinalizableReferenceQueue.getInstance());
            this.keyReference = keyReference;
        }

        public void finalizeReferent() {
            delegate.remove(keyReference, this);
        }

        @Override
        public boolean equals(Object obj) {
            return referenceEquals(this, obj);
        }
    }

    class WeakValueReference extends WeakReference<Object> implements InternalReference {
        final Object keyReference;

        WeakValueReference(Object keyReference, Object value) {
            super(value, FinalizableReferenceQueue.getInstance());
            this.keyReference = keyReference;
        }

        public void finalizeReferent() {
            delegate.remove(keyReference, this);
        }

        @Override
        public boolean equals(Object obj) {
            return referenceEquals(this, obj);
        }
    }

    static class FinalizableReferenceQueue extends ReferenceQueue<Object> {

        private FinalizableReferenceQueue() {
        }

        void cleanUp(Reference reference) {
            try {
                ((InternalReference) reference).finalizeReferent();
            } catch (Throwable t) {
                throw new IllegalStateException("Unable to clean up after reference", t);
            }
        }

        void start() {
            Thread thread = new Thread("FinalizableReferenceQueue") {
                @Override
                @SuppressWarnings({ "InfiniteLoopStatement" })
                public void run() {
                    while (true) {
                        try {
                            cleanUp(remove());
                        } catch (InterruptedException iex) { /* ignore */
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        }

        static final ReferenceQueue<Object> instance = createAndStart();

        static FinalizableReferenceQueue createAndStart() {
            FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
            queue.start();
            return queue;
        }

        /**
         * Gets instance.
         */
        public static ReferenceQueue<Object> getInstance() {
            return instance;
        }
    }

    // ---------------------------------------------------------------- put
    // strategy

    protected interface Strategy {
        Object execute(ReferenceMap map, Object keyReference, Object valueReference);
    }

    private enum PutStrategy implements Strategy {
        PUT {
            public Object execute(ReferenceMap map, Object keyReference, Object valueReference) {
                return map.dereferenceValue(map.delegate.put(keyReference, valueReference));
            }
        },

        REPLACE {
            public Object execute(ReferenceMap map, Object keyReference, Object valueReference) {
                // ensure that the existing value is not collected
                do {
                    Object existingValueReference;
                    Object existingValue;
                    do {
                        existingValueReference = map.delegate.get(keyReference);
                        existingValue = map.dereferenceValue(existingValueReference);
                    } while (isExpired(existingValueReference, existingValue));

                    if (existingValueReference == null) {
                        return Boolean.valueOf(false); // nothing to replace
                    }

                    if (map.delegate.replace(keyReference, existingValueReference, valueReference)) {
                        return existingValue; // existingValue did not expire
                                              // since we still have a reference
                                              // to it
                    }
                } while (true);
            }
        },

        PUT_IF_ABSENT {
            public Object execute(ReferenceMap map, Object keyReference, Object valueReference) {
                Object existingValueReference;
                Object existingValue;
                do {
                    existingValueReference = map.delegate.putIfAbsent(keyReference, valueReference);
                    existingValue = map.dereferenceValue(existingValueReference);
                } while (isExpired(existingValueReference, existingValue));
                return existingValue;
            }
        },
    }

    // ---------------------------------------------------------------- map
    // entry set

    class Entry implements Map.Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return this.key;
        }

        public V getValue() {
            return this.value;
        }

        public V setValue(V newValue) {
            value = newValue;
            return put(key, newValue);
        }

        @Override
        public int hashCode() {
            return key.hashCode() * 31 + value.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReferenceMap.Entry)) {
                return false;
            }

            Entry entry = (Entry) o;
            return key.equals(entry.key) && value.equals(entry.value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    private volatile Set<Map.Entry<K, V>> entrySet;

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new EntrySet();
        }
        return entrySet;
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new ReferenceIterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            V v = ReferenceMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            return ReferenceMap.this.remove(e.getKey(), e.getValue());
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private class ReferenceIterator implements Iterator<Map.Entry<K, V>> {
        private Iterator<Map.Entry<Object, Object>> i = delegate.entrySet().iterator();
        private Map.Entry<K, V> nextEntry;
        private Map.Entry<K, V> lastReturned;

        private ReferenceIterator() {
            advanceToNext();
        }

        private void advanceToNext() {
            while (i.hasNext()) {
                Map.Entry<K, V> entry = dereferenceEntry(i.next());
                if (entry != null) {
                    nextEntry = entry;
                    return;
                }
            }
            nextEntry = null;
        }

        public boolean hasNext() {
            return nextEntry != null;
        }

        public Map.Entry<K, V> next() {
            if (nextEntry == null) {
                throw new NoSuchElementException();
            }
            lastReturned = nextEntry;
            advanceToNext();
            return lastReturned;
        }

        public void remove() {
            ReferenceMap.this.remove(lastReturned.getKey());
        }
    }

}
