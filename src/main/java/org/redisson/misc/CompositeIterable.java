package org.redisson.misc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompositeIterable<T> implements Iterable<T>, Iterator<T> {

    private List<Iterable<T>> iterablesList;
    private Iterable<T>[] iterables;

    private Iterator<Iterator<T>> listIterator;
    private Iterator<T> currentIterator;

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
        listIterator = iterators.iterator();
        currentIterator = null;
        return this;
    }

    @Override
    public boolean hasNext() {
        if (currentIterator == null || !currentIterator.hasNext()) {
            while (listIterator.hasNext()) {
                Iterator<T> iterator = listIterator.next();
                if (iterator.hasNext()) {
                    currentIterator = iterator;
                    return true;
                }
            }
            return false;
        }
        return currentIterator.hasNext();
    }

    @Override
    public T next() {
        hasNext();
        return currentIterator.next();
    }

    @Override
    public void remove() {
        currentIterator.remove();
    }

}
