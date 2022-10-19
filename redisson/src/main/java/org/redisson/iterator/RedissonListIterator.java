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
package org.redisson.iterator;

import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class RedissonListIterator<V> implements ListIterator<V> {

    private V prevCurrentValue;
    private V nextCurrentValue;
    private V currentValueHasRead;
    private int currentIndex;
    private boolean hasBeenModified = true;

    public RedissonListIterator(int startIndex) {
        currentIndex = startIndex - 1;
    }

    public abstract V getValue(int index);

    public abstract V remove(int index);

    public abstract void fastSet(int index, V value);

    public abstract void add(int index, V value);

    @Override
    public boolean hasNext() {
        V val = getValue(currentIndex + 1);
        if (val != null) {
            nextCurrentValue = val;
        }
        return val != null;
    }

    @Override
    public V next() {
        if (nextCurrentValue == null && !hasNext()) {
            throw new NoSuchElementException("No such element at index " + currentIndex);
        }
        currentIndex++;
        currentValueHasRead = nextCurrentValue;
        nextCurrentValue = null;
        hasBeenModified = false;
        return currentValueHasRead;
    }

    @Override
    public void remove() {
        if (currentValueHasRead == null) {
            throw new IllegalStateException("Neither next nor previous have been called");
        }
        if (hasBeenModified) {
            throw new IllegalStateException("Element been already deleted");
        }
        remove(currentIndex);
        currentIndex--;
        hasBeenModified = true;
        currentValueHasRead = null;
    }

    @Override
    public boolean hasPrevious() {
        if (currentIndex < 0) {
            return false;
        }
        V val = getValue(currentIndex);
        if (val != null) {
            prevCurrentValue = val;
        }
        return val != null;
    }

    @Override
    public V previous() {
        if (prevCurrentValue == null && !hasPrevious()) {
            throw new NoSuchElementException("No such element at index " + currentIndex);
        }
        currentIndex--;
        hasBeenModified = false;
        currentValueHasRead = prevCurrentValue;
        prevCurrentValue = null;
        return currentValueHasRead;
    }

    @Override
    public int nextIndex() {
        return currentIndex + 1;
    }

    @Override
    public int previousIndex() {
        return currentIndex;
    }

    @Override
    public void set(V e) {
        if (hasBeenModified) {
            throw new IllegalStateException();
        }

        fastSet(currentIndex, e);
    }

    @Override
    public void add(V e) {
        add(currentIndex + 1, e);
        currentIndex++;
        hasBeenModified = true;
    }

}
