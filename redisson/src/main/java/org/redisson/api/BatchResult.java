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
package org.redisson.api;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BatchResult<E> implements List<E> {

    private final List<E> responses;
    private final int syncedSlaves;
    
    public BatchResult(List<E> responses, int syncedSlaves) {
        super();
        this.responses = responses;
        this.syncedSlaves = syncedSlaves;
    }
    
    /**
     * Returns list of result objects for each command
     * 
     * @return list of objects
     */
    public List<?> getResponses() {
        return responses;
    }

    /**
     * Returns amount of successfully synchronized slaves during batch execution
     * 
     * @return slaves amount
     */
    public int getSyncedSlaves() {
        return syncedSlaves;
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public int size() {
        return responses.size();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean isEmpty() {
        return responses.isEmpty();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean contains(Object o) {
        return responses.contains(o);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public Iterator<E> iterator() {
        return responses.iterator();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public Object[] toArray() {
        return responses.toArray();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public <T> T[] toArray(T[] a) {
        return responses.toArray(a);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean add(E e) {
        return responses.add(e);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean remove(Object o) {
        return responses.remove(o);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean containsAll(Collection<?> c) {
        return responses.containsAll(c);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean addAll(Collection<? extends E> c) {
        return responses.addAll(c);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean addAll(int index, Collection<? extends E> c) {
        return responses.addAll(index, c);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean removeAll(Collection<?> c) {
        return responses.removeAll(c);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public boolean retainAll(Collection<?> c) {
        return responses.retainAll(c);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public void clear() {
        responses.clear();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public E get(int index) {
        return responses.get(index);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public E set(int index, E element) {
        return responses.set(index, element);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public void add(int index, E element) {
        responses.add(index, element);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public E remove(int index) {
        return responses.remove(index);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public int indexOf(Object o) {
        return responses.indexOf(o);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public int lastIndexOf(Object o) {
        return responses.lastIndexOf(o);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public ListIterator<E> listIterator() {
        return responses.listIterator();
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public ListIterator<E> listIterator(int index) {
        return responses.listIterator(index);
    }

    /**
     * Use {@link #getResponses()}
     */
    @Deprecated
    public List<E> subList(int fromIndex, int toIndex) {
        return responses.subList(fromIndex, toIndex);
    }

    
}
