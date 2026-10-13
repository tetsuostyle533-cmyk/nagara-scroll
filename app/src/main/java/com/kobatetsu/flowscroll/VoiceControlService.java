package com.kobatetsu.flowscroll;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.*;
import java.util.ArrayList;

/** User-started, bounded on-device listening session. Never restarts after process death. */
public final class VoiceControlService extends Service implements RecognitionListener {
    public static final String ACTION_STOP = "com.kobatetsu.flowscroll.VOICE_OFF";
    private static final String CHANNEL = "voice_control";
    private static final int NOTIFICATION_ID = 41;
    public static volatile boolean active;
    public static volatile String status = "声操作 OFF";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean ending;
    private int failures;
    private final Runnable listenAgain = this::listen;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) { finishSession("画面OFFで声操作を終了しました"); }
    };
    private boolean registered;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            finishSession("声操作 OFF"); return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            finishSession("端末内の音声認識が利用できません"); return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "声操作", NotificationManager.IMPORTANCE_LOW));
            Intent stop = new Intent(this, VoiceControlService.class).setAction(ACTION_STOP);
            PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("ながらスクロール：声操作 ON")
                    .setContentText("スクロール／ストップ • 最大15分").setContentIntent(open).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "声操作を終了", stopIntent).build()).build();
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            ending = false; active = true; failures = 0;
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            registered = true;
            updateStatus("声操作 ON：準備中");
            handler.postDelayed(() -> finishSession("15分経過：声操作を終了しました"), 15 * 60 * 1000L);
            listen();
        } catch (RuntimeException error) { finishSession("声操作を開始できません。アプリから再度ONにしてください"); }
        return START_NOT_STICKY;
    }
    private void listen() {
        if (!active || ending || recognizer == null) return;
        Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { recognizer.startListening(request); }
        catch (RuntimeException error) { finishSession("音声認識を開始できません"); }
    }
    private void retry(long delay) {
        if (!active || ending) return;
        handler.removeCallbacks(listenAgain); handler.postDelayed(listenAgain, delay);
    }
    private void updateStatus(String value) {
        status = value;
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_VOICE_STATUS).setPackage(getPackageName()));
    }
    private void finishSession(String reason) {
        ending = true; active = false; handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_STOP_SCROLL).setPackage(getPackageName()));
        updateStatus(reason); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        ending = true; active = false; handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        if (status.startsWith("声操作 ON")) updateStatus("声操作 OFF");
        if (registered) { unregisterReceiver(screenReceiver); registered = false; }
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_STOP_SCROLL).setPackage(getPackageName()));
        super.onDestroy();
    }
    private void command(Bundle results, boolean partial) {
        ArrayList<String> phrases = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (phrases == null || phrases.isEmpty()) return;
        int command = VoiceCommand.parse(phrases.get(0));
        // Stop early for safety. Start only on a final result.
        if (command == VoiceCommand.STOP || (!partial && command == VoiceCommand.START)) {
            sendBroadcast(new Intent(command == VoiceCommand.STOP
                    ? AutoScrollAccessibilityService.ACTION_STOP_SCROLL
                    : AutoScrollAccessibilityService.ACTION_START_SCROLL).setPackage(getPackageName()));
        }
    }
    @Override public void onResults(Bundle results) { failures = 0; command(results, false); retry(300); }
    @Override public void onPartialResults(Bundle results) { command(results, true); }
    @Override public void onError(int error) {
        if (ending) return;
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { retry(500); return; }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || ++failures >= 3) {
            finishSession("声操作を終了：マイク権限・日本語の音声認識設定を確認してください");
        } else { updateStatus("声操作 ON：再接続中"); retry(2000); }
    }
    @Override public void onReadyForSpeech(Bundle params) { updateStatus("声操作 ON：聞いています"); }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rms) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int type, Bundle params) {}
    @Override public IBinder onBind(Intent intent) { return null; }
}
