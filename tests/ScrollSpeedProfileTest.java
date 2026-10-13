package com.kobatetsu.flowscroll;

/** Regression checks for slow swipes being too small/long and speed order. */
public final class ScrollSpeedProfileTest {
    public static void main(String[] args) {
        double previousRate = 0;
        for (int level = 1; level <= 5; level++) {
            int distance = ScrollSpeedProfile.distanceDp(level);
            long duration = ScrollSpeedProfile.durationMs(level);
            long pause = ScrollSpeedProfile.pauseMs(level);
            // Cross a conservative 32dp movement threshold within 200ms.
            if (distance * 200.0 / duration < 32) throw new AssertionError("insufficient initial movement " + level);
            if (duration > 500 || duration < 100) throw new AssertionError("stop latency " + level);
            if (pause < 0 || pause > 2000) throw new AssertionError("excessive pause " + level);
            double rate = distance * 1000.0 / (duration + pause);
            if (rate <= previousRate) throw new AssertionError("speed order " + level);
            previousRate = rate;
        }
        double slow = ScrollSpeedProfile.distanceDp(1) * 1000.0 /
                (ScrollSpeedProfile.durationMs(1) + ScrollSpeedProfile.pauseMs(1));
        if (slow < 30 || slow > 50) throw new AssertionError("slow reading speed");
        if (ScrollSpeedProfile.clampLevel(-100) != 1 || ScrollSpeedProfile.clampLevel(100) != 5)
            throw new AssertionError("bounds");
        if (ScrollSpeedProfile.pauseMs(0) != ScrollSpeedProfile.pauseMs(1)
                || ScrollSpeedProfile.distanceDp(6) != ScrollSpeedProfile.distanceDp(5))
            throw new AssertionError("invalid stored speed");
        System.out.println("PASS: all 5 speeds, slow swipe regression, stop latency, bounds");
    }
}
