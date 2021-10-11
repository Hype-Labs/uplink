package com.uplink.ulx.bridge.network.model;

import com.uplink.ulx.model.Instance;

public class Ticket {

    private final int sequenceIdentifier;
    private final Instance instance;

    public Ticket(int sequenceIdentifier, Instance instance) {
        this.sequenceIdentifier = sequenceIdentifier;
        this.instance = instance;
    }

    public final int getSequenceIdentifier() {
        return this.sequenceIdentifier;
    }

    public final Instance getInstance() {
        return this.instance;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Ticket ticket = (Ticket)other;

        if (getSequenceIdentifier() != ticket.getSequenceIdentifier()) {
            return false;
        }

        return getInstance().equals(ticket.getInstance());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + getSequenceIdentifier();
        hash = 31 * hash + getInstance().getStringIdentifier().hashCode();
        return hash;
    }

}
