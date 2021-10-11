package com.uplink.ulx.bridge;

public class SequenceGenerator {

    private int sequence;
    private final int maximum;

    public SequenceGenerator(int maximum) {
        this.sequence = 0;
        this.maximum = maximum;
    }

    public final int getMaximum() {
        return this.maximum;
    }

    public synchronized int generate() {
        int current = this.sequence;

        // Increment the sequence number
        this.sequence = (this.sequence + 1) % getMaximum();

        return current;
    }
}
