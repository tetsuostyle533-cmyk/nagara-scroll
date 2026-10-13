package com.kobatetsu.flowscroll;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int NAVY = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(71, 85, 105);
    private TextView statusText;
    private Button settingsButton;
    private Button voiceButton;
    private TextView voiceStatus;
    private TextView modelStatus;
    private SpeechRecognizer modelRecognizer;
    private int modelGeneration;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshVoice = new Runnable() {
        public void run() {
            if (voiceStatus != null) voiceStatus.setText(VoiceControlService.status);
            if (voiceButton != null) voiceButton.setText(VoiceControlService.active ? "声操作をOFFにする" : "声操作をONにする");
            statusHandler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
        statusHandler.post(refreshVoice);
    }

    @Override protected void onPause() {
        statusHandler.removeCallbacks(refreshVoice);
        super.onPause();
    }

    @Override protected void onDestroy() {
        modelGeneration++;
        if (modelRecognizer != null) { modelRecognizer.destroy(); modelRecognizer = null; }
        super.onDestroy();
    }

    private void prepareJapaneseVoice() {
        if (VoiceControlService.active) {
            Toast.makeText(this, "声操作をOFFにしてから準備してください", Toast.LENGTH_LONG).show(); return;
        }
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            modelStatus.setText("端末の設定で日本語のオフライン音声認識を準備してください"); return;
        }
        final int token = ++modelGeneration;
        if (modelRecognizer != null) modelRecognizer.destroy();
        try {
            modelRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            modelRecognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float r) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onError(int e) {}
                public void onResults(Bundle b) {}
                public void onPartialResults(Bundle b) {}
                public void onEvent(int e, Bundle b) {}
            });
            modelStatus.setText("日本語音声を準備中。初回はWi-Fiにつないでください");
            if (Build.VERSION.SDK_INT >= 34) {
                modelRecognizer.triggerModelDownload(VoiceControlService.recognitionRequest(), getMainExecutor(),
                    new ModelDownloadListener() {
                        private void show(String text) {
                            if (token == modelGeneration && !isDestroyed()) modelStatus.setText(text);
                        }
                        public void onProgress(int progress) { show("日本語音声をダウンロード中：" + progress + "％"); }
                        public void onSuccess() { show("日本語音声の準備ができました。「声操作をONにする」を押してください"); }
                        public void onScheduled() { show("ダウンロードを予約しました。Wi-Fi接続後、もう一度このボタンを押してください"); }
                        public void onError(int error) { show("準備できませんでした（" + error + "）。音声認識アプリの更新とWi-Fiを確認してください"); }
                    });
            } else {
                modelRecognizer.triggerModelDownload(VoiceControlService.recognitionRequest());
                modelStatus.setText("日本語データの準備を依頼しました。少し待ってから声操作をONにしてください");
            }
        } catch (RuntimeException error) {
            modelStatus.setText("日本語音声の準備を開始できません。端末の音声認識アプリを確認してください");
        }
    }

    private void enableVoice() {
        if (!isServiceEnabled()) {
            Toast.makeText(this, "先にユーザー補助をONにしてください", Toast.LENGTH_LONG).show(); return;
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Toast.makeText(this, "声操作にはAndroid 12以降と端末内の日本語音声認識が必要です", Toast.LENGTH_LONG).show(); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101); return;
        }
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_SHOW_OVERLAY).setPackage(getPackageName()));
        try {
            startForegroundService(new Intent(this, VoiceControlService.class));
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
        } catch (RuntimeException error) {
            Toast.makeText(this, "声操作を開始できませんでした", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 101) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) enableVoice();
            else Toast.makeText(this, "マイク未許可：ボタン操作は使えます", Toast.LENGTH_LONG).show();
        }
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scrollView.setBackgroundColor(Color.rgb(248, 250, 252));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(24));
        scrollView.addView(page, matchWrap());

        TextView eyebrow = text("FLOW SCROLL", 12, BLUE, true);
        eyebrow.setLetterSpacing(0.12f);
        page.addView(eyebrow);

        TextView title = text("声で動かす。\n手を使わず読む。", 26, NAVY, true);
        title.setLineSpacing(dp(2), 1f);
        page.addView(title, topMargin(-2));

        TextView lead = text("ChatGPTやChromeの画面を、好みの速さで自動スクロールします。", 16, MUTED, false);
        lead.setLineSpacing(dp(4), 1f);
        page.addView(lead, topMargin(14));

        LinearLayout statusCard = card();
        statusText = text("状態を確認中…", 17, NAVY, true);
        statusCard.addView(statusText);
        page.addView(statusCard, topMargin(26));

        settingsButton = new Button(this);
        settingsButton.setText("ユーザー補助を設定する");
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setTextSize(16);
        settingsButton.setAllCaps(false);
        settingsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        settingsButton.setBackground(rounded(BLUE, 16));
        settingsButton.setOnClickListener(v -> {
            if (isServiceEnabled()) {
                sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_SHOW_OVERLAY)
                        .setPackage(getPackageName()));
            } else {
                showDisclosure();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        buttonParams.topMargin = dp(14);
        page.addView(settingsButton, buttonParams);

        LinearLayout voiceCard = card();
        voiceCard.addView(text("声で開始・停止", 20, NAVY, true));
        voiceCard.addView(text("「スクロール」で開始\n「ストップ」で停止", 20, NAVY, true), topMargin(8));
        voiceStatus = text(VoiceControlService.status, 16, MUTED, false);
        voiceCard.addView(voiceStatus, topMargin(8));
        voiceButton = new Button(this);
        voiceButton.setText("声操作をONにする");
        voiceButton.setTextSize(18);
        voiceButton.setAllCaps(false);
        voiceButton.setOnClickListener(v -> {
            if (VoiceControlService.active) {
                stopService(new Intent(this, VoiceControlService.class));
            } else {
                new AlertDialog.Builder(this).setTitle("声操作を使う")
                    .setMessage("マイクを使い端末内で日本語の命令を認識します。録音・文章は保存しません。声操作は最大15分、画面OFFやパネル終了で止まります。認識の切替中や周囲が騒がしい場所では聞き取れない場合があります。停止ボタンも使えます。")
                    .setNegativeButton("やめる", null)
                    .setPositiveButton("声操作をON", (dialog, which) -> enableVoice()).show();
            }
        });
        voiceCard.addView(voiceButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        Button prepare = new Button(this);
        prepare.setText("日本語音声を準備する");
        prepare.setTextSize(18);
        prepare.setAllCaps(false);
        prepare.setOnClickListener(v -> prepareJapaneseVoice());
        voiceCard.addView(prepare, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        modelStatus = text("声操作が動かないときは、日本語音声の準備と、マイクのアクセスがONかを確認してください。", 16, MUTED, false);
        voiceCard.addView(modelStatus, topMargin(8));
        page.addView(voiceCard, topMargin(16));

        TextView howTitle = text("使い方", 20, NAVY, true);
        page.addView(howTitle, topMargin(32));
        page.addView(step("1", "最初の1回だけ、ユーザー補助で「ながらスクロール 声操作」をON。"), topMargin(14));
        page.addView(step("2", "ChatGPTやChromeなど、読みたい画面を開く。"), topMargin(12));
        page.addView(step("3", "画面端の ▶ を押す。速度は − / ＋ で5段階。"), topMargin(12));

        TextView restrictedNote = text("設定がグレーで選べない場合：設定 → アプリ → ながらスクロール 声操作 → 右上の︙ →「制限付き設定を許可」を先に選びます。", 13, MUTED, false);
        restrictedNote.setLineSpacing(dp(3), 1f);
        restrictedNote.setPadding(dp(14), dp(12), dp(14), dp(12));
        restrictedNote.setBackground(rounded(Color.rgb(226, 232, 240), 12));
        page.addView(restrictedNote, topMargin(16));

        LinearLayout privacy = card();
        privacy.addView(text("プライバシー", 17, NAVY, true));
        TextView privacyBody = text("このアプリは画面の文字を読み取りません。閲覧内容の取得・保存・送信は行いません。声操作は端末内で認識し、音声・認識した文章を保存しません。自動スクロール用のスワイプ操作だけを実行します。", 14, MUTED, false);
        privacyBody.setLineSpacing(dp(3), 1f);
        privacy.addView(privacyBody, topMargin(8));
        page.addView(privacy, topMargin(28));

        TextView note = text("試作版 1.1.1 • ボタン：Android 7以降／声：Android 12以降", 14, Color.rgb(100, 116, 139), false);
        note.setGravity(Gravity.CENTER);
        page.addView(note, topMargin(24));

        return scrollView;
    }

    private void refreshServiceStatus() {
        if (statusText == null || settingsButton == null) return;
        boolean enabled = isServiceEnabled();
        statusText.setText(enabled ? "● 使用できます" : "○ 初回設定が必要です");
        statusText.setTextColor(enabled ? Color.rgb(22, 101, 52) : NAVY);
        settingsButton.setText(enabled ? "操作パネルを表示する" : "ユーザー補助を設定する");
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() != null
                    && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && AutoScrollAccessibilityService.class.getName()
                    .equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private void showDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("ユーザー補助機能について")
                .setMessage("ながらスクロール 声操作は、あなたが開始ボタンを押した後に、他のアプリへスワイプ操作を送るためユーザー補助機能を使います。\n\n画面の文字・入力内容・個人情報は取得、保存、送信しません。通信権限もありません。\n\n同意する場合だけ、次の設定画面で「ながらスクロール 声操作」をONにしてください。")
                .setNegativeButton("やめる", null)
                .setPositiveButton("同意して設定を開く", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .show();
    }

    private LinearLayout step(String number, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView badge = text(number, 14, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(BLUE, 99));
        row.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView copy = text(body, 15, NAVY, false);
        copy.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(12);
        row.addView(copy, copyParams);
        return row;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(rounded(Color.WHITE, 18));
        layout.setElevation(dp(2));
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int marginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(marginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
