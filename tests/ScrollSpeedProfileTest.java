package com.kobatetsu.flowscroll;

public final class ScrollSpeedProfileTest {
    public static void main(String[] args) {
        int previous = 0;
        for (int level = 1; level <= 5; level++) {
            int rate = ScrollSpeedProfile.speedDpPerSecond(level);
            if (rate <= previous) throw new AssertionError("speed order");
            previous = rate;
            for (float density : new float[]{1f, 2.75f, 3.5f}) {
                ScrollMotion motion = new ScrollMotion(100, 1000, 100, density, 16 * density);
                ScrollMotion.Step first = motion.next(level);
                if (!first.newTouch || !first.continues || first.fromY - first.toY <= 16 * density)
                    throw new AssertionError("drag activation");
                float cursor = first.toY;
                boolean lifted = false;
                for (int i = 0; i < 2000; i++) {
                    ScrollMotion.Step step = motion.next(level);
                    if (step.newTouch || Math.abs(step.fromY - cursor) > 0.001)
                        throw new AssertionError("broken touch continuity");
                    if (step.toY < 100 || step.toY > step.fromY)
                        throw new AssertionError("out of bounds or reverse scroll");
                    if (!step.continues) {
                        if (step.fromY != step.toY || step.toY != 100)
                            throw new AssertionError("lift must not send another tap or move");
                        lifted = true; break;
                    }
                    float expected = ScrollSpeedProfile.distancePx(level, density, step.durationMs);
                    if (step.toY > 100 && Math.abs((step.fromY - step.toY) - expected) > 0.001)
                        throw new AssertionError("uneven movement");
                    if (step.durationMs > 200) throw new AssertionError("stop response");
                    cursor = step.toY;
                }
                if (!lifted) throw new AssertionError("edge did not release");
            }
        }
        if (ScrollSpeedProfile.speedDpPerSecond(1) > 10) throw new AssertionError("level 1 too fast");
        ScrollMotion motion = new ScrollMotion(100, 1000, 100, 3, 24);
        motion.next(1);
        ScrollMotion.Step slow = motion.next(1), fast = motion.next(5);
        if (slow.toY != fast.fromY || fast.fromY - fast.toY <= slow.fromY - slow.toY)
            throw new AssertionError("speed change must retain touch and update next movement");
        if (ScrollSpeedProfile.clampLevel(-100) != 1 || ScrollSpeedProfile.clampLevel(100) != 5)
            throw new AssertionError("bounds");
        System.out.println("PASS: 5 speeds / 3 densities, continuous paths, constant motion, edge release, speed changes");
    }
}
