package com.mediavisualizer.client.audio;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds the latest analyzed spectrum data and acts as the hand-off point between the
 * audio thread (producer) and the render thread (consumer).
 * <p>
 * A {@link ReentrantReadWriteLock} is used instead of a naive {@code synchronized} block
 * so that the render thread's frequent reads never block each other, and so that copying
 * data out for rendering is cheap and allocation-free (callers pass in a reusable buffer).
 */
public final class VisualizerData {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean silent = new AtomicBoolean(true);

    private float[] barValues;
    private long lastUpdateNanos;

    /**
     * @param barCount number of visual bars this instance will hold values for.
     */
    public VisualizerData(int barCount) {
        this.barValues = new float[barCount];
    }

    /**
     * Called from the audio thread to publish a new frame of bar values.
     * The provided array is copied internally so the caller may keep reusing its own buffer.
     *
     * @param newValues newest bar magnitudes, normalized to [0, 1].
     * @param isSilent  whether the audio engine currently considers the signal silent.
     */
    public void publish(float[] newValues, boolean isSilent) {
        lock.writeLock().lock();
        try {
            if (barValues.length != newValues.length) {
                barValues = new float[newValues.length];
            }
            System.arraycopy(newValues, 0, barValues, 0, newValues.length);
            lastUpdateNanos = System.nanoTime();
        } finally {
            lock.writeLock().unlock();
        }
        silent.set(isSilent);
    }

    /**
     * Called from the render thread to copy the latest values into a caller-owned buffer.
     * This never allocates on the caller's behalf; {@code destination} must already be sized
     * to at least {@link #getBarCount()}.
     *
     * @param destination buffer to copy into.
     * @return the number of bars written.
     */
    public int copyInto(float[] destination) {
        lock.readLock().lock();
        try {
            int count = Math.min(destination.length, barValues.length);
            System.arraycopy(barValues, 0, destination, 0, count);
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getBarCount() {
        lock.readLock().lock();
        try {
            return barValues.length;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Resizes the internal buffer. Called when the user changes the "Number of Bars" setting.
     */
    public void resize(int newBarCount) {
        lock.writeLock().lock();
        try {
            barValues = new float[newBarCount];
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSilent() {
        return silent.get();
    }

    public long getLastUpdateNanos() {
        return lastUpdateNanos;
    }
}
