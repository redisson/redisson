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
package org.redisson.misc;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Pepe Lu
 */
public class CompositeIterator<T> implements Iterator<T> {

    private Iterator<Iterator<T>> listIterator;
    private Iterator<T> currentIterator;

    public CompositeIterator(Iterator<Iterator<T>> iterators) {
        listIterator = iterators;
    }

    @Override
    public boolean hasNext() {
        if (currentIterator == null || !currentIterator.hasNext()) {
            while (listIterator.hasNext()) {
                Iterator<T> iterator = listIterator.next();
                currentIterator = iterator;
                if (iterator.hasNext()) {
                    return true;
                }
            }
            return false;
        }

        return currentIterator.hasNext();
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        return currentIterator.next();
    }

    @Override
    public void remove() {
        if (currentIterator == null) {
            throw new IllegalStateException("next() has not yet been called");
        }

        currentIterator.remove();
    }
}
