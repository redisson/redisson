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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompositeIterable<T> implements Iterable<T> {

    private List<Iterable<T>> iterablesList;
    private Iterable<T>[] iterables;

    public CompositeIterable(List<Iterable<T>> iterables) {
        this.iterablesList = iterables;
    }

    public CompositeIterable(Iterable<T> ... iterables) {
        this.iterables = iterables;
    }

    public CompositeIterable(CompositeIterable<T> iterable) {
        this.iterables = iterable.iterables;
        this.iterablesList = iterable.iterablesList;
    }

    @Override
    public Iterator<T> iterator() {
        List<Iterator<T>> iterators = new ArrayList<Iterator<T>>();
        if (iterables != null) {
            for (Iterable<T> iterable : iterables) {
                iterators.add(iterable.iterator());
            }
        } else {
            for (Iterable<T> iterable : iterablesList) {
                iterators.add(iterable.iterator());
            }
        }
        Iterator<Iterator<T>>  listIterator = iterators.iterator();
        return new CompositeIterator<T>(listIterator);
    }
}
