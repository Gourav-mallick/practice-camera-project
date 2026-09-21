package com.example.myapplication.util;

public class FrameRateCounter {
    public interface FpsListener {
        void onFpsUpdated(int fps);
    }

    private final FpsListener listener;
    private int frameCount = 0;
    private long windowStartMs = System.currentTimeMillis();

    public FrameRateCounter(FpsListener listener) {
        this.listener = listener;
    }

    public void onFrame() {
        frameCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs;

        if (elapsed >= 1000) {
            listener.onFpsUpdated(frameCount);
            frameCount = 0;
            windowStartMs = now;
        }
    }

    public void reset() {
        frameCount = 0;
        windowStartMs = System.currentTimeMillis();
    }
}
