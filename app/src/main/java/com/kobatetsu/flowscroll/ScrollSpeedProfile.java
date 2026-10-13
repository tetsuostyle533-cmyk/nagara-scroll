package com.kobatetsu.flowscroll;

/** Pure-Java speed mapping so it can be tested without Android. */
final class ScrollSpeedProfile {
    // Slow, tiny drags can be consumed as taps/long presses by the target app.
    // Keep each swipe substantial and short; control reading speed with pauses.
    private static final int[] DISTANCE_DP = {96, 112, 128, 144, 168};
    private static final long SWIPE_MS = 480L;
    private static final long[] PAUSE_MS = {1800L, 1280L, 850L, 530L, 260L};

    private ScrollSpeedProfile() {}

    static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    static int distanceDp(int level) {
        return DISTANCE_DP[clampLevel(level) - 1];
    }

    static long durationMs(int level) {
        return SWIPE_MS;
    }

    static long pauseMs(int level) {
        return PAUSE_MS[clampLevel(level) - 1];
    }
}
