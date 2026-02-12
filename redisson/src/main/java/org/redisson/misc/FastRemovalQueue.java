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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe queue with O(1) complexity for removal operation.
 *
 * @author Nikita Koksharov
 *
 * @param <E> element type
 */
public final class FastRemovalQueue<E> implements Iterable<E> {

    private final Map<E, Node<E>> index = new ConcurrentHashMap<>();
    private final DoublyLinkedList<E> list = new DoublyLinkedList<>();

    public void add(E element) {
        Node<E> newNode = new Node<>(element);
        if (index.putIfAbsent(element, newNode) == null) {
            list.add(newNode);
        }
    }

    public boolean moveToTail(E element) {
        Node<E> node = index.get(element);
        if (node != null) {
            list.moveToTail(node);
            return true;
        }
        return false;
    }

    public boolean remove(E element) {
        Node<E> node = index.remove(element);
        if (node != null) {
            return list.remove(node);
        }
        return false;
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }

    public int size() {
        return index.size();
    }

    public E poll() {
        Node<E> node = list.removeFirst();
        if (node != null) {
            index.remove(node.value);
            return node.value;
        }
        return null;
    }

    public void clear() {
        index.clear();
        list.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    static class Node<E> {
        private final E value;
        private Node<E> prev;
        private volatile Node<E> next;
        private volatile boolean deleted;

        Node(E value) {
            this.value = value;
        }

        public void setDeleted() {
            deleted = true;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public E getValue() {
            return value;
        }
    }

    static class DoublyLinkedList<E> implements Iterable<E> {
        private final WrappedLock lock = new WrappedLock();
        private Node<E> head;
        private Node<E> tail;

        DoublyLinkedList() {
        }

        public void clear() {
            lock.execute(() -> {
                head = null;
                tail = null;
            });
        }

        public void add(Node<E> newNode) {
            lock.execute(() -> {
                addNode(newNode);
            });
        }

        private void addNode(Node<E> newNode) {
            Node<E> currentTail = tail;
            tail = newNode;
            if (currentTail == null) {
                head = newNode;
            } else {
                newNode.prev = currentTail;
                currentTail.next = newNode;
            }
        }

        public boolean remove(Node<E> node) {
            Boolean r = lock.execute(() -> {
                if (node.isDeleted()) {
                    return false;
                }

                removeNode(node);
                node.setDeleted();
                return true;
            });
            return Boolean.TRUE.equals(r);
        }

        private void removeNode(Node<E> node) {
            Node<E> prevNode = node.prev;
            Node<E> nextNode = node.next;

            if (prevNode != null) {
                prevNode.next = nextNode;
            } else {
                head = nextNode;
            }

            if (nextNode != null) {
                nextNode.prev = prevNode;
            } else {
                tail = prevNode;
            }
        }

        public void moveToTail(Node<E> node) {
            lock.execute(() -> {
                if (node.isDeleted()) {
                    return;
                }

                removeNode(node);

                node.prev = null;
                node.next = null;
                addNode(node);
            });
        }

        public Node<E> removeFirst() {
            return lock.execute(() -> {
                Node<E> currentHead = head;
                if (head == tail) {
                    head = null;
                    tail = null;
                } else {
                    head = head.next;
                    head.prev = null;
                }
                if (currentHead != null) {
                    currentHead.setDeleted();
                }
                return currentHead;
            });
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                private Node<E> current = head;

                @Override
                public boolean hasNext() {
                    while (current != null && current.isDeleted()) {
                        current = current.next;
                    }
                    return current != null;
                }

                @Override
                public E next() {
                    if (current == null) {
                        throw new NoSuchElementException();
                    }
                    E value = current.getValue();
                    current = current.next;
                    return value;
                }
            };
        }
    }
}