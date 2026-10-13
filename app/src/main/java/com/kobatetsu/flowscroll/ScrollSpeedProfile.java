package com.kobatetsu.flowscroll;

/** Pure-Java speed mapping so it can be tested without Android. */
final class ScrollSpeedProfile {
    private static final int[] SPEED_DP_PER_SECOND = {8, 16, 28, 44, 68};

    private ScrollSpeedProfile() {}

    static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    static int speedDpPerSecond(int level) {
        return SPEED_DP_PER_SECOND[clampLevel(level) - 1];
    }

    static float distancePx(int level, float density, long durationMs) {
        return speedDpPerSecond(level) * density * durationMs / 1000f;
    }

    // Android 7 fallback: ordinary short strokes without deliberate waiting.
    static int distanceDp(int level) { return 40 + 8 * clampLevel(level); }
    static long durationMs(int level) {
        return distanceDp(level) * 1000L / speedDpPerSecond(level);
    }
    static long pauseMs(int level) {
        return 0;
    }
}
