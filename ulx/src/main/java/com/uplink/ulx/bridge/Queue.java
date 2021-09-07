package com.uplink.ulx.bridge;

import com.uplink.ulx.model.Message;

import java.util.LinkedList;
import java.util.List;

public class Queue<T> {

    private List<T> queue;

    public Queue() {
        this.queue = null;
    }

    private List<T> getQueue() {
        if (this.queue == null) {
            this.queue = new LinkedList<>();
        }
        return this.queue;
    }

    public synchronized void queue(T obj) {
        getQueue().add(obj);
    }

    public synchronized T dequeue() {
        return isEmpty() ? null : getQueue().remove(0);
    }

    public synchronized boolean isEmpty() {
        return getQueue().isEmpty();
    }
}
