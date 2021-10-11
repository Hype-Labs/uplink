package com.uplink.ulx.drivers.commons.model;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Buffer {

    private final int initialCapacity;
    private int occupiedByteCount;
    private byte[] data;
    private Lock lock;

    /**
     * Constructor. Initializes the buffer by allocating the given initial
     * capacity.
     * @param initialCapacity The buffer's initial capacity.
     */
    public Buffer(int initialCapacity) {
        this.initialCapacity = initialCapacity;
        this.occupiedByteCount = 0;
        this.data = null;
        this.lock = null;
    }

    /**
     * Returns the amount of bytes that the buffer is to physically allocate
     * when it is first allocated.
     * @return The number of bytes to initially allocate for the buffer.
     */
    private int getInitialCapacity() {
        return this.initialCapacity;
    }

    /**
     * Returns the amount of bytes that are currently allocated by the buffer.
     * This corresponds to the buffer's current capacity, which the maximum it
     * can store at this time. However, the buffer's size is not static, so
     * this value of capacity changes over time.
     * @return The buffer's capacity.
     */
    private synchronized int getCapacity() {
        return getData().length;
    }

    /**
     * Returns the amount of bytes within the buffer that are actually occupied.
     * This does not necessarily correspond to the buffer's capacity.
     * @return The amount of bytes occupied by the buffer.
     */
    public final synchronized int getOccupiedByteCount() {
        return this.occupiedByteCount;
    }

    /**
     * Sets the amount of bytes within the buffer that are actually occupied.
     * This value may differ from the buffer's capacity, but must never exceed
     * it.
     * @param occupiedByteCount The amount of bytes occupied by the buffer.
     */
    private synchronized void setOccupiedByteCount(int occupiedByteCount) {
        this.occupiedByteCount = occupiedByteCount;
    }

    /**
     * Returns the amount of bytes that are not occupied by the buffer.
     * @return The amount of bytes in the buffer that are free.
     */
    private synchronized int getFreeByteCount() {
        return getData().length - getOccupiedByteCount();
    }

    /**
     * Returns the buffer to use for I/O operations. If the buffer has already
     * been previously allocated and not cleared, this method will allocate a
     * new byte array. If the buffer is non-null, meaning that it was allocated
     * and not cleared, this will return the buffer that already exists.
     * @return The buffer used for I/O operations.
     */
    public synchronized byte[] getData() {
        if (this.data == null) {
            this.data = new byte[getInitialCapacity()];
        }
        return this.data;
    }

    /**
     * Returns the object that should be used to lock buffer operations. This
     * will guarantee that buffer operations do not overlap.
     * @return The buffer lock.
     */
    public synchronized final Lock getLock() {
        if (this.lock == null) {
            this.lock = new ReentrantLock();
        }
        return this.lock;
    }

    private synchronized void setData(byte[] data) {
        this.data = data;
    }

    public synchronized int append(byte[] data) {
        return append(data, data.length);
    }

    public synchronized int append(byte[] data, int maxLength) {

        // Prevent buffer overflow
        maxLength = Math.min(data.length, maxLength);

        // Grow the buffer to fit the required amount of data, if possible
        grow(getOccupiedByteCount() + maxLength);

        // We're appending only up to the buffer's limit
        int amountToAppend = Math.min(maxLength, getFreeByteCount());

        // Make the copy
        System.arraycopy(data, 0, getData(), getOccupiedByteCount(), amountToAppend);

        // The buffer now has consumed whatever was there before plus what we
        // just appended.
        setOccupiedByteCount(getOccupiedByteCount() + amountToAppend);

        // Return the number of bytes that we actually processed
        return amountToAppend;
    }

    /**
     * Removes the given amount of data from the buffer, resulting in the buffer
     * shrinking by at most that amount of bytes.
     * @param byteCount The number of bytes to trim.
     */
    public synchronized void trim(int byteCount) {

        // Are we being requested to trim more data than what we have?
        byteCount = Math.min(byteCount, getOccupiedByteCount());

        // Shift the buffer left
        System.arraycopy(getData(), byteCount, getData(), 0, getOccupiedByteCount() - byteCount);

        // Trim the capacity to size
        shrink(getOccupiedByteCount() - byteCount);
    }

    /**
     * Consumes data from the buffer into the given byte array. The buffer will
     * shrink by the amount of bytes actually read, which will be the lowest
     * between the buffer's available data and the input buffer's length.
     * @param buffer The output buffer.
     */
    public synchronized int consume(byte[] buffer) {

        // Are we being requested to consume more than we can?
        int byteCount = Math.min(buffer.length, getOccupiedByteCount());

        // Make a copy
        System.arraycopy(getData(), 0, buffer, 0, byteCount);

        // Trim the capacity
        shrink(getOccupiedByteCount() - byteCount);

        return byteCount;
    }

    private synchronized void grow(int toSize) {

        // If the buffer is already big enough, nothing to do
        if (getCapacity() >= toSize) {
            return;
        }

        // We're allocating the buffer specifically to the desired size, but
        // this is a bad strategy. This isn't amortizing the allocations and
        // should be changed in the future. Also, it doesn't care about
        // depleting memory, which is obviously wrong.
        byte[] newBuffer = new byte[toSize];

        // Make a copy
        System.arraycopy(getData(), 0, newBuffer, 0, getData().length);

        // Replace the current buffer
        setData(newBuffer);
    }

    private synchronized void shrink(int toSize) {

        // Create a new a buffer, using the naive and strict approach of
        // allocating exactly the number of bytes we need. This is not optimal,
        // and should be changed in the future.
        byte[] newBuffer = new byte[toSize];

        // Make a copy
        System.arraycopy(getData(), getData().length - toSize, newBuffer, 0, toSize);

        // Replace the current buffer
        setData(newBuffer);

        // Update the occupied byte count
        setOccupiedByteCount(toSize);
    }

    /**
     * Returns {@code true} if the buffer is empty, meaning that its occupied
     * byte count is zero. This happens regardless of the buffer's capacity,
     * which means that an empty buffer may still occupy memory.
     * @return Whether the buffer is empty.
     */
    public boolean isEmpty() {
        return this.getOccupiedByteCount() == 0;
    }
}
