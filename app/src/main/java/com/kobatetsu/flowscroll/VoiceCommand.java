package com.kobatetsu.flowscroll;

import java.util.Locale;

/** Exact commands only; never treat an ordinary sentence as a command. */
public final class VoiceCommand {
    public static final int NONE = 0, START = 1, STOP = 2;
    private VoiceCommand() {}
    public static int parse(String phrase) {
        if (phrase == null) return NONE;
        String value = phrase.toLowerCase(Locale.ROOT).replaceAll("[\\s　。、.!！?？]", "");
        if (value.equals("ストップ") || value.equals("停止") || value.equals("止めて")
                || value.equals("stop")) return STOP;
        if (value.equals("スクロール") || value.equals("スクロール開始")
                || value.equals("scroll")) return START;
        return NONE;
    }
}
