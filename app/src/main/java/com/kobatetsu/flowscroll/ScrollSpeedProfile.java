package com.kobatetsu.flowscroll;

/** Pure-Java speed mapping so it can be tested without Android. */
final class ScrollSpeedProfile {
    private static final int[] DISTANCE_DP = {48, 64, 82, 104, 132};
    private static final long[] DURATION_MS = {1150L, 1000L, 850L, 700L, 560L};

    private ScrollSpeedProfile() {}

    static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    static int distanceDp(int level) {
        return DISTANCE_DP[clampLevel(level) - 1];
    }

    static long durationMs(int level) {
        return DURATION_MS[clampLevel(level) - 1];
    }
}
