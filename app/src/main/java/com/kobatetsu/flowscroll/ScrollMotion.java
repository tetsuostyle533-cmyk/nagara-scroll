package com.kobatetsu.flowscroll;

/** Plans contiguous movement; all normal segments retain the same touch. */
final class ScrollMotion {
    // Fewer callback boundaries; Android still samples each path per display frame.
    static final long FRAME_MS = 800;
    static final class Step {
        final float x, fromY, toY;
        final long durationMs;
        final boolean newTouch, continues;
        Step(float x, float from, float to, long duration, boolean first, boolean more) {
            this.x = x; fromY = from; toY = to; durationMs = duration;
            newTouch = first; continues = more;
        }
    }
    private final float x, top, density;
    private float y;
    private final float initialDistance;
    private boolean first = true, atTop;

    ScrollMotion(float x, float bottom, float top, float density, float touchSlopPx) {
        if (bottom <= top || top < 0 || density <= 0 || touchSlopPx < 0)
            throw new IllegalArgumentException("invalid swipe lane");
        this.x = x; y = bottom; this.top = top; this.density = density;
        initialDistance = Math.min(bottom - top, touchSlopPx + 2 * density);
    }
    Step next(int level) {
        if (atTop) return new Step(x, y, y, 60, false, false);
        float from = y;
        boolean newTouch = first;
        long duration = first ? 80 : FRAME_MS;
        float distance = first ? initialDistance : ScrollSpeedProfile.distancePx(level, density, duration);
        float remaining = y - top;
        // Keep the requested speed when the final segment reaches the lane edge.
        if (!first && distance > remaining) {
            duration = Math.max(1L, Math.round(remaining * 1000d
                    / (ScrollSpeedProfile.speedDpPerSecond(level) * density)));
        }
        y = Math.max(top, y - distance);
        first = false;
        atTop = y <= top;
        return new Step(x, from, y, duration, newTouch, true);
    }
}
