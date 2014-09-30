package ru.ovchinnicov;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-Blocking queue implementation
 * @author Valerii Ovchinnikov
 */
class NonBlockingQueue<T> implements Queue<T> {
    // you can tune this according to contention
    private final static long BACK_OFF_TIME = 10;
    private AtomicReference<State<T>> state;
    {
       Node<T> head = new Node(null);
       state = new AtomicReference<State<T>>(new State(head, head));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void add(T elem) throws InterruptedException {
        Node<T> newTail = new Node<>(elem);
        while (true) {
            State<T> oldState = state.get();
            if
            newTail.next = oldState.tail();
            State<T> newState;
            if(head.value == null) { // empty queue
                Node<T> newHead = new Node<T>(elem);
                newState = new State<>(newHead, newHead);
            } else {
                newState = new State<>(oldState.head, new Node<>(elem, oldState.tail));
            }

            if(state.compareAndSet(oldState, newState)) {
                break;
            }
            await();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T remove() throws InterruptedException {
        while (true) {
            Node<T> h = head.get();
            T value = h.value();
            if (value != null && h.casValue(value, null)) {
                // no other thread can cas head now
                if (h.next() != null) { // else removed last node from queue. tail/head reset handled by add method
                    if (!head.compareAndSet(h, h.next())) {
                        assert false : "Remove: couldn't set head after old head's value set to null";
                    }
                }
                return value;
            } else if (h.next() == null) {
                // empty queue
                return null;
            }
            // some other thread is removing head now
            await();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return head.get() == tail.get() && tail.get().value() == null;
    }

    private void await() throws InterruptedException {
        try {
            Thread.sleep(BACK_OFF_TIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static class State<T> {
        final Node<T> head;
        final Node<T> tail;
        State(Node<T> h, Node<T> t) {
             head = h;
             tail = t;
        }
    }


    private static class Node<T> {
        final AtomicReference<Node<T>> next = new AtomicReference<>();
        final AtomicReference<T> value = new AtomicReference<>();

        Node(T value) {
            this.value.set(value);
        }

        boolean casNext(Node<T> exp, Node<T> upd) {
            return next.compareAndSet(exp, upd);
        }

        Node<T> next() {
            return next.get();
        }

        T value() {
            return value.get();
        }

        boolean casValue(T exp, T upd) {
            return value.compareAndSet(exp, upd);
        }

        boolean isEmpty() {
            return value.get() == null && next.get() == null;
        }
    }
}
