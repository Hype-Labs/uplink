package com.uplink.ulx.drivers.commons.model;

public class Buffer {

    private final int initialCapacity;
    private int occupiedByteCount;
    private byte[] data;

    public Buffer(int initialCapacity) {
        this.initialCapacity = initialCapacity;
        this.occupiedByteCount = 0;
        this.data = null;
    }

    /**
     * Returns the amount of bytes that the buffer is to physically allocate
     * when it is first allocated.
     * @return The number of bytes to initially allocate for the buffer.
     */
    private int getInitialCapacity() {
        return this.initialCapacity;
    }

    private synchronized int getCapacity() {
        return getData().length;
    }

    private synchronized int getOccupiedByteCount() {
        return this.occupiedByteCount;
    }

    private synchronized void setOccupiedByteCount(int occupiedByteCount) {
        this.occupiedByteCount = occupiedByteCount;
    }

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

    private synchronized void setData(byte[] data) {
        this.data = data;
    }

    public synchronized int append(byte[] data) {

        // Grow the buffer to fit the required amount of data, if possible
        grow(getOccupiedByteCount() + data.length);

        // We're appending only up to the buffer's limit
        int amountToAppend = Math.min(data.length, getFreeByteCount());

        // Make the copy
        System.arraycopy(data, 0, getData(), getOccupiedByteCount(), amountToAppend);

        // The buffer now has consumed whatever was there before plus what we
        // just appended.
        setOccupiedByteCount(getOccupiedByteCount() + amountToAppend);

        // Return the number of bytes that we actually processed
        return amountToAppend;
    }

    public synchronized void trim(int byteCount) {

        // Are we being requested to trim more data than what we have?
        byteCount = Math.min(byteCount, getOccupiedByteCount());

        // Shift the buffer left
        System.arraycopy(getData(), byteCount, getData(), 0, getOccupiedByteCount() - byteCount);

        // Trim the capacity to size
        shrink(getOccupiedByteCount() - byteCount);
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

    public boolean isEmpty() {
        return this.getOccupiedByteCount() == 0;
    }
}
