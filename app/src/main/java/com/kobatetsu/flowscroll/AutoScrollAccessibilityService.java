package com.kobatetsu.flowscroll;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AutoScrollAccessibilityService extends AccessibilityService {
    public static final String ACTION_SHOW_OVERLAY =
            "com.kobatetsu.flowscroll.action.SHOW_OVERLAY";
    public static final String ACTION_START_SCROLL = "com.kobatetsu.flowscroll.START_SCROLL";
    public static final String ACTION_STOP_SCROLL = "com.kobatetsu.flowscroll.STOP_SCROLL";
    public static final String ACTION_VOICE_STATUS = "com.kobatetsu.flowscroll.VOICE_STATUS";
    private int scrollGeneration;
    private TextView voiceStatus;
    private static final String PREFS = "flow_scroll_prefs";
    private static final String KEY_SPEED = "speed";
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int PANEL = Color.rgb(15, 23, 42);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextGesture = this::performNextGesture;
    private boolean gestureInFlight;
    private ScrollMotion motion;
    private GestureDescription.StrokeDescription heldStroke;
    private float touchX, touchY;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private LinearLayout overlay;
    private boolean running;
    private boolean collapsed;
    private int speedLevel = 3;
    private Button playButton;
    private Button slowerButton, fasterButton;
    private TextView speedText;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            stopScrolling();
            stopService(new Intent(context, VoiceControlService.class));
        }
    };

    private final BroadcastReceiver overlayCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SHOW_OVERLAY.equals(intent.getAction())) {
                showOverlay();
            } else if (ACTION_START_SCROLL.equals(intent.getAction())) {
                if (overlay != null) startScrolling();
            } else if (ACTION_STOP_SCROLL.equals(intent.getAction()) || Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopScrolling();
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) stopService(new Intent(context, VoiceControlService.class));
            } else if (ACTION_VOICE_STATUS.equals(intent.getAction()) && voiceStatus != null) {
                voiceStatus.setText(VoiceControlService.status);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        speedLevel = ScrollSpeedProfile.clampLevel(prefs.getInt(KEY_SPEED, 3));
        registerOverlayCommandReceiver();
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty: this app never reads the active app's UI content.
    }

    @Override
    public void onInterrupt() {
        stopScrolling();
        stopService(new Intent(this, VoiceControlService.class));
    }

    @Override
    public void onDestroy() {
        stopScrolling();
        stopService(new Intent(this, VoiceControlService.class));
        removeOverlay();
        if (receiverRegistered) {
            unregisterReceiver(overlayCommandReceiver);
            unregisterReceiver(screenOffReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerOverlayCommandReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(ACTION_SHOW_OVERLAY);
        filter.addAction(ACTION_START_SCROLL);
        filter.addAction(ACTION_STOP_SCROLL);
        filter.addAction(ACTION_VOICE_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayCommandReceiver, filter, getPackageName() + ".INTERNAL", null, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayCommandReceiver, filter, getPackageName() + ".INTERNAL", null);
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
        receiverRegistered = true;
    }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = buildExpandedPanel();

        overlayParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = dp(10);
        overlayParams.y = dp(160);
        windowManager.addView(overlay, overlayParams);
    }

    @SuppressLint("ClickableViewAccessibility")
    private LinearLayout buildExpandedPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(rounded(PANEL, 18, 220));
        panel.setElevation(dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView drag = label("⋮⋮", 18, Color.rgb(203, 213, 225), true);
        drag.setGravity(Gravity.CENTER);
        drag.setContentDescription("操作パネルを移動");
        drag.setOnTouchListener(new OverlayDragListener());
        top.addView(drag, new LinearLayout.LayoutParams(dp(44), dp(36)));

        TextView title = label("自動スクロール", 13, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(title, titleParams);

        Button collapse = smallButton("−", "パネルを小さくする");
        collapse.setOnClickListener(v -> collapsePanel());
        top.addView(collapse, new LinearLayout.LayoutParams(dp(40), dp(38)));

        Button close = smallButton("×", "操作パネルを閉じる");
        close.setOnClickListener(v -> hidePanel());
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(38)));
        panel.addView(top, new LinearLayout.LayoutParams(dp(242), ViewGroup.LayoutParams.WRAP_CONTENT));

        playButton = smallButton("▶", "自動スクロールを開始");
        playButton.setTextSize(26);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(rounded(BLUE, 99, 255));
        playButton.setOnClickListener(v -> toggleScrolling());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        playParams.topMargin = dp(8);
        panel.addView(playButton, playParams);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER);

        Button slower = smallButton("−", "速度を下げる");
        slowerButton = slower;
        slower.setOnClickListener(v -> changeSpeed(-1));
        speedRow.addView(slower, new LinearLayout.LayoutParams(dp(48), dp(44)));

        speedText = label(speedLabel(), 14, Color.WHITE, true);
        speedText.setGravity(Gravity.CENTER);
        speedRow.addView(speedText, new LinearLayout.LayoutParams(dp(96), dp(44)));

        Button faster = smallButton("＋", "速度を上げる");
        fasterButton = faster;
        faster.setOnClickListener(v -> changeSpeed(1));
        speedRow.addView(faster, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        speedParams.topMargin = dp(8);
        panel.addView(speedRow, speedParams);
        refreshSpeedControls();
        voiceStatus = label(VoiceControlService.status, 14, Color.WHITE, false);
        voiceStatus.setGravity(Gravity.CENTER);
        panel.addView(voiceStatus, new LinearLayout.LayoutParams(dp(242), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button voiceOff = smallButton("声操作 ON / OFF", "声操作を切り替える");
        voiceOff.setTextSize(16);
        voiceOff.setOnClickListener(v -> {
            if (VoiceControlService.active) {
                stopService(new Intent(this, VoiceControlService.class));
                voiceStatus.setText("声操作 OFF");
            } else {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        panel.addView(voiceOff, new LinearLayout.LayoutParams(dp(160), dp(48)));
        return panel;
    }

    @SuppressLint("ClickableViewAccessibility")
    private LinearLayout buildCollapsedBubble() {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(rounded(running ? Color.rgb(220, 38, 38) : BLUE, 99, 240));
        bubble.setElevation(dp(8));

        TextView icon = label(running ? "Ⅱ" : "▶", 23, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setContentDescription(running ? "自動スクロールを停止" : "操作パネルを開く");
        icon.setOnClickListener(v -> {
            if (running) {
                stopScrolling();
                refreshCollapsedBubble();
            } else {
                expandPanel();
            }
        });
        icon.setOnTouchListener(new OverlayDragListener());
        bubble.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        return bubble;
    }

    private void toggleScrolling() {
        if (running) {
            stopScrolling();
        } else {
            startScrolling();
        }
        refreshPlayButton();
    }

    private void startScrolling() {
        if (running) return;
        running = true;
        if (!gestureInFlight && heldStroke == null) scrollGeneration++;
        refreshPlayButton();
        refreshCollapsedBubble();
        performNextGesture();
    }

    private void stopScrolling() {
        running = false;
        handler.removeCallbacks(nextGesture);
        // Let the current 120ms piece finish, then lift the SAME pointer.
        // Do not send an unrelated tap to cancel a continued gesture.
        if (!gestureInFlight) releaseHeldTouch();
        refreshPlayButton();
        refreshCollapsedBubble();
    }

    private void performNextGesture() {
        if (!running || gestureInFlight || windowManager == null) return;
        if (Build.VERSION.SDK_INT < 26) { performLegacyGesture(); return; }
        if (motion == null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            float x = metrics.widthPixels * 0.38f;
            float top = metrics.heightPixels * 0.27f;
            float bottom = metrics.heightPixels * 0.72f;
            if (overlay != null) {
                int[] position = new int[2];
                overlay.getLocationOnScreen(position);
                if (bottom > position[1] && top < position[1] + overlay.getHeight()
                        && x >= position[0] && x <= position[0] + overlay.getWidth()) {
                    float leftSpace = Math.max(0, position[0]);
                    float rightEdge = position[0] + overlay.getWidth();
                    float rightSpace = Math.max(0, metrics.widthPixels - rightEdge);
                    x = leftSpace >= rightSpace ? leftSpace / 2f : rightEdge + rightSpace / 2f;
                }
            }
            motion = new ScrollMotion(x, bottom, top, metrics.density,
                    ViewConfiguration.get(this).getScaledTouchSlop());
        }
        ScrollMotion.Step step = motion.next(speedLevel);
        Path path = new Path();
        path.moveTo(step.x, step.fromY);
        if (step.toY != step.fromY) path.lineTo(step.x, step.toY);
        GestureDescription.StrokeDescription stroke = step.newTouch
                ? new GestureDescription.StrokeDescription(path, 0, step.durationMs, step.continues)
                : heldStroke.continueStroke(path, 0, step.durationMs, step.continues);
        touchX = step.x; touchY = step.toY;
        dispatchScrollStroke(stroke, step.continues);
    }

    private void releaseHeldTouch() {
        if (Build.VERSION.SDK_INT < 26 || gestureInFlight || heldStroke == null) return;
        Path path = new Path();
        path.moveTo(touchX, touchY);
        dispatchScrollStroke(heldStroke.continueStroke(path, 0, 60, false), false);
    }

    private void dispatchScrollStroke(GestureDescription.StrokeDescription stroke, boolean continues) {
        final int generation = scrollGeneration;
        gestureInFlight = true;
        boolean accepted;
        try {
            accepted = dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),
                    new GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription gesture) {
                            if (generation != scrollGeneration) return;
                            gestureInFlight = false;
                            heldStroke = continues ? stroke : null;
                            if (!continues) motion = null;
                            if (running) handler.post(nextGesture);
                            else releaseHeldTouch();
                        }
                        @Override public void onCancelled(GestureDescription gesture) {
                            if (generation != scrollGeneration) return;
                            gestureInFlight = false; heldStroke = null; motion = null;
                            if (running) handler.postDelayed(nextGesture, 350);
                        }
                    }, handler);
        } catch (RuntimeException error) {
            accepted = false;
        }
        if (!accepted && generation == scrollGeneration) {
            gestureInFlight = false; heldStroke = null; motion = null;
            if (running) {
                handler.removeCallbacks(nextGesture);
                handler.postDelayed(nextGesture, 350);
            }
        }
    }

    private void performLegacyGesture() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        Path path = new Path();
        float x = metrics.widthPixels * 0.38f, y = metrics.heightPixels * 0.56f;
        float distance = dp(ScrollSpeedProfile.distanceDp(speedLevel));
        path.moveTo(x, y + distance / 2);
        path.lineTo(x, y - distance / 2);
        dispatchScrollStroke(new GestureDescription.StrokeDescription(path, 0,
                Math.min(900, ScrollSpeedProfile.durationMs(speedLevel))), false);
    }

    private void changeSpeed(int delta) {
        speedLevel = ScrollSpeedProfile.clampLevel(speedLevel + delta);
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SPEED, speedLevel).apply();
        refreshSpeedControls();
        // Replace an old pause immediately, but never overlap an in-flight swipe.
        if (running && !gestureInFlight) {
            handler.removeCallbacks(nextGesture);
            handler.post(nextGesture);
        }
    }

    private void refreshSpeedControls() {
        if (speedText != null) speedText.setText(speedLabel());
        if (slowerButton != null) {
            slowerButton.setEnabled(speedLevel > 1);
            slowerButton.setAlpha(speedLevel > 1 ? 1f : 0.4f);
        }
        if (fasterButton != null) {
            fasterButton.setEnabled(speedLevel < 5);
            fasterButton.setAlpha(speedLevel < 5 ? 1f : 0.4f);
        }
    }

    private String speedLabel() {
        return "速度 " + speedLevel + " / 5";
    }

    private void refreshPlayButton() {
        if (playButton == null) return;
        playButton.setText(running ? "Ⅱ" : "▶");
        playButton.setContentDescription(running ? "自動スクロールを停止" : "自動スクロールを開始");
        playButton.setBackground(rounded(running ? Color.rgb(220, 38, 38) : BLUE, 99, 255));
    }

    private void collapsePanel() {
        collapsed = true;
        replaceOverlay(buildCollapsedBubble(), dp(58), dp(58));
    }

    private void hidePanel() {
        stopScrolling();
        stopService(new Intent(this, VoiceControlService.class));
        collapsed = false;
        removeOverlay();
    }

    private void expandPanel() {
        collapsed = false;
        replaceOverlay(buildExpandedPanel(), ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshPlayButton();
    }

    private void refreshCollapsedBubble() {
        if (collapsed) replaceOverlay(buildCollapsedBubble(), dp(58), dp(58));
    }

    private void replaceOverlay(View replacement, int width, int height) {
        if (windowManager == null || overlayParams == null) return;
        if (overlay != null) windowManager.removeView(overlay);
        overlay = (LinearLayout) replacement;
        overlayParams.width = width;
        overlayParams.height = height;
        windowManager.addView(overlay, overlayParams);
    }

    private void removeOverlay() {
        if (windowManager != null && overlay != null) {
            windowManager.removeView(overlay);
            overlay = null;
        }
    }

    private Button smallButton(String text, String description) {
        Button button = new Button(this);
        button.setText(text);
        button.setContentDescription(description);
        button.setTextSize(20);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(rounded(Color.rgb(51, 65, 85), 12, 255));
        return button;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp, int alpha) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setAlpha(alpha);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class OverlayDragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (overlayParams == null || windowManager == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = overlayParams.x;
                    startY = overlayParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(touchX - event.getRawX());
                    int dy = Math.round(event.getRawY() - touchY);
                    if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                    overlayParams.x = Math.max(0, Math.min(startX + dx,
                            getResources().getDisplayMetrics().widthPixels - overlay.getWidth()));
                    overlayParams.y = Math.max(0, Math.min(startY + dy,
                            getResources().getDisplayMetrics().heightPixels - overlay.getHeight() - dp(48)));
                    windowManager.updateViewLayout(overlay, overlayParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && collapsed) view.performClick();
                    return true;
                default:
                    return false;
            }
        }
    }
}
