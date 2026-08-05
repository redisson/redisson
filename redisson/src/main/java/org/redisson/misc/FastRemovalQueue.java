package org.redisson.misc;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-blocking queue with O(1) removal whose read path allocates nothing.
 *
 * Null elements aren't permitted.
 *
 * @author Nikita Koksharov
 *
 * @param <E> element type
 */
public final class FastRemovalQueue<E> implements Iterable<E> {

    private volatile State<E> state = new State<>();

    public void add(E element) {
        State<E> current = state;
        Node<E> newNode = new Node<>(element);
        while (true) {
            Node<E> indexed = current.index.putIfAbsent(element, newNode);
            if (indexed == null) {
                current.queue.add(newNode);
                return;
            }
            if (indexed.get() != null) {
                return;
            }
            if (current.index.replace(element, indexed, newNode)) {
                current.queue.add(newNode);
                return;
            }
        }
    }

    public boolean remove(E element) {
        State<E> current = state;
        Node<E> node = current.index.remove(element);
        if (node == null || node.claim() == null) {
            return false;
        }
        current.claimed.incrementAndGet();
        discardClaimedHead(current);
        compactIfBacklogged(current);
        return true;
    }

    private static <E> void compactIfBacklogged(State<E> current) {
        if (current.claimed.get() <= current.index.size() + 64) {
            return;
        }
        if (!current.compacting.compareAndSet(false, true)) {
            return;
        }
        try {
            int swept = 0;
            for (Iterator<Node<E>> it = current.queue.iterator(); it.hasNext();) {
                if (it.next().get() == null) {
                    it.remove();
                    swept++;
                }
            }
            current.claimed.addAndGet(-swept);
        } finally {
            current.compacting.set(false);
        }
    }

    private static <E> void discardClaimedHead(State<E> current) {
        for (int i = 0; i < 2; i++) {
            Node<E> head = current.queue.peek();
            if (head == null || head.get() != null) {
                return;
            }
            if (!current.queue.remove(head)) {
                return;
            }
            current.claimed.decrementAndGet();
        }
    }

    public boolean moveToTail(E element) {
        Node<E> node = state.index.get(element);
        if (node == null || node.get() == null) {
            return false;
        }
        if (!node.referenced) {
            node.referenced = true;
        }
        return true;
    }

    public E poll() {
        State<E> current = state;
        // one full revolution is the bound: after that, evict whatever comes up
        int reprieves = current.index.size() + 1;
        Node<E> node;
        while ((node = current.queue.poll()) != null) {
            if (node.get() == null) {
                current.claimed.decrementAndGet();
                continue;
            }
            if (node.referenced && reprieves-- > 0) {
                node.referenced = false;
                current.queue.add(node);
                continue;
            }
            E value = node.claim();
            if (value != null) {
                current.index.remove(value, node);
                return value;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return state.index.isEmpty();
    }

    public int size() {
        return state.index.size();
    }

    public void clear() {
        state = new State<>();
    }

    @Override
    public Iterator<E> iterator() {
        Iterator<Node<E>> nodes = state.queue.iterator();
        return new Iterator<E>() {
            private E next;

            @Override
            public boolean hasNext() {
                while (next == null && nodes.hasNext()) {
                    next = nodes.next().get();
                }
                return next != null;
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                E value = next;
                next = null;
                return value;
            }
        };
    }

    private static final class State<E> {

        private final Map<E, Node<E>> index = new ConcurrentHashMap<>();
        private final Queue<Node<E>> queue = new ConcurrentLinkedQueue<>();

        private final AtomicInteger claimed = new AtomicInteger();
        private final AtomicBoolean compacting = new AtomicBoolean();

    }

    private static final class Node<E> extends AtomicReference<E> {

        private static final long serialVersionUID = 1L;

        private volatile boolean referenced;

        Node(E value) {
            super(value);
        }

        E claim() {
            return getAndSet(null);
        }
    }
}