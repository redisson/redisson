/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
 * <p>
 * Null elements aren't permitted. The queue holds an element one time only. It compares
 * elements with equals() and hashCode(), and these must not change while the element is
 * queued. The iterator is weakly consistent.
 *
 * @author Nikita Koksharov
 *
 * @param <E> element type
 */
public final class FastRemovalQueue<E> implements Iterable<E> {

    /** Dead nodes unlinked per removal, so no single caller pays for the whole prefix. */
    private static final int DROP_BUDGET = 64;

    /** Nodes examined per removal by the sweep cursor. */
    private static final int SWEEP_BUDGET = 16;

    /** Below this the backlog is not worth sweeping. */
    private static final int SWEEP_THRESHOLD = 64;

    private volatile State<E> state = new State<>();

    /**
     * Adds the element at the tail. Does nothing if an equal element is queued.
     *
     * @param element element to add
     */
    public void add(E element) {
        State<E> current = state;
        Node<E> newNode = new Node<>(element);
        while (true) {
            Node<E> indexed = current.index.putIfAbsent(element, newNode);
            if (indexed == null) {
                publish(current, newNode);
                return;
            }
            if (indexed.get() != null) {
                return;
            }
            if (current.index.replace(element, indexed, newNode)) {
                publish(current, newNode);
                return;
            }
        }
    }

    private void publish(State<E> current, Node<E> newNode) {
        current.queue.add(newNode);
        current.live.incrementAndGet();
    }

    public boolean remove(E element) {
        State<E> current = state;
        Node<E> node = current.index.remove(element);
        if (node == null || node.claim() == null) {
            return false;
        }
        current.live.decrementAndGet();

        // poll() can take the node out of the queue between its liveness check and its claim.
        // Then the node is settled before this line and must not be counted.
        if (node.backlog.compareAndSet(Node.UNCOUNTED, Node.COUNTED)) {
            current.claimed.incrementAndGet();
        }
        dropDeadPrefix(current);
        sweepIfBacklogged(current);
        return true;
    }

    private void dropDeadPrefix(State<E> current) {
        for (int dropped = 0; dropped < DROP_BUDGET; dropped++) {
            Node<E> head = current.queue.peek();

            // remove(head) is false when another thread took the head first, and then it has
            // walked the whole queue for nothing.
            if (head == null || head.get() != null || !current.queue.remove(head)) {
                return;
            }
            settle(current, head);
        }
    }

    /**
     * The prefix drop cannot reach a dead node until everything ahead of it has gone, so a
     * caller that removes out of order strands them behind a live element. One thread at a
     * time advances a cursor a fixed distance and hands it back, which reaches the middle
     * without any single call paying for the queue.
     */
    private void sweepIfBacklogged(State<E> current) {
        int backlog = current.claimed.get();
        if (backlog <= SWEEP_THRESHOLD || backlog <= current.live.get()) {
            return;
        }
        if (!current.sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            Iterator<Node<E>> cursor = current.sweeper;
            if (cursor == null) {
                cursor = current.queue.iterator();
            }
            for (int examined = 0; examined < SWEEP_BUDGET && cursor.hasNext(); examined++) {
                Node<E> node = cursor.next();
                if (node.get() == null) {
                    cursor.remove();
                    settle(current, node);
                }
            }
            if (cursor.hasNext()) {
                current.sweeper = cursor;
            } else {
                current.sweeper = null;
            }
        } finally {
            current.sweeping.set(false);
        }
    }

    /**
     * Takes a node out of the backlog count, one time only.
     */
    private void settle(State<E> current, Node<E> node) {
        if (node.backlog.getAndSet(Node.SETTLED) == Node.COUNTED) {
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
        int reprieves = Math.max(0, current.live.get()) + 1;
        Node<E> node;
        while ((node = current.queue.poll()) != null) {
            if (node.get() == null) {
                settle(current, node);
                continue;
            }
            if (node.referenced && reprieves-- > 0) {
                node.referenced = false;
                current.queue.add(node);
                continue;
            }
            E value = node.claim();
            if (value != null) {
                current.live.decrementAndGet();
                current.index.remove(value, node);
                return value;
            }

            // remove() claimed the node after the check above. The node is out of the queue,
            // so nothing else can discard it and take it out of the count
            settle(current, node);
        }
        return null;
    }

    public boolean isEmpty() {
        Queue<Node<E>> queue = state.queue;
        Node<E> head = queue.peek();
        if (head == null) {
            return true;
        }
        if (head.get() != null) {
            return false;
        }
        // removed nodes at the head: look for a live one behind them
        for (Node<E> node : queue) {
            if (node.get() != null) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return Math.max(0, state.live.get());
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
        /** Nodes published to {@link #queue} and not yet claimed. */
        private final AtomicInteger live = new AtomicInteger();
        /** Nodes that remove() claimed and that nothing has discarded yet. */
        private final AtomicInteger claimed = new AtomicInteger();
        private final AtomicBoolean sweeping = new AtomicBoolean();
        /** Sweep position carried between calls; guarded by {@link #sweeping}. */
        private volatile Iterator<Node<E>> sweeper;
    }

    private static final class Node<E> extends AtomicReference<E> {

        private static final long serialVersionUID = 1L;

        private static final int UNCOUNTED = 0;
        private static final int COUNTED = 1;
        private static final int SETTLED = 2;

        private volatile boolean referenced;

        /**
         * Tells if the node is in {@link State#claimed}: UNCOUNTED, then COUNTED when remove()
         * claims it, then SETTLED when the first caller discards it. A node that poll() claims
         * is never counted.
         */
        private final AtomicInteger backlog = new AtomicInteger(UNCOUNTED);

        Node(E value) {
            super(value);
        }

        E claim() {
            return getAndSet(null);
        }
    }
}