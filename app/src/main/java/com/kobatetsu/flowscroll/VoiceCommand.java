package com.kobatetsu.flowscroll;

import java.text.Normalizer;
import java.util.Locale;

/** Exact commands only; ordinary sentences and negations never start scrolling. */
public final class VoiceCommand {
    public static final int NONE = 0, START = 1, STOP = 2;
    private VoiceCommand() {}
    public static int parse(String phrase) {
        if (phrase == null) return NONE;
        String value = Normalizer.normalize(phrase, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s　。、.!！?？]", "");
        switch (value) {
            case "ストップ": case "すとっぷ": case "停止": case "ていし":
            case "止めて": case "とめて": case "止まって": case "とまって":
            case "スクロール停止": case "スクロールを止めて": case "stop":
                return STOP;
            case "スクロール": case "すくろーる": case "スクロール開始":
            case "スクロールして": case "スクロールして下さい": case "スクロールしてください":
            case "scroll": return START;
            default: return NONE;
        }
    }
}
