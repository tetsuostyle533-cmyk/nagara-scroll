package com.kobatetsu.flowscroll;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.*;
import java.util.ArrayList;

/** User-started on-device session; never resumes after screen off or process death. */
public final class VoiceControlService extends Service {
    public static final String ACTION_STOP = "com.kobatetsu.flowscroll.VOICE_OFF";
    private static final String CHANNEL = "voice_control";
    private static final int NOTIFICATION_ID = 41;
    public static volatile boolean active;
    public static volatile String status = "声操作 OFF";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean ending, listening, stoppedInUtterance;
    private int generation, failures;
    private final Runnable listenAgain = this::listen;
    private final Runnable watchdog = () -> reconnect("応答待ちを解除して再接続中");
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) { finishSession("画面OFFで声操作を終了しました"); }
    };
    private boolean registered;

    static Intent recognitionRequest() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            finishSession("声操作 OFF"); return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            finishSession("マイク権限と端末内の音声認識を確認してください"); return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "声操作", NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop = PendingIntent.getService(this, 1,
                    new Intent(this, VoiceControlService.class).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("ながらスクロール：声操作 ON")
                    .setContentText("スクロール／ストップ • 最大15分").setContentIntent(open).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "声操作を終了", stop).build()).build();
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            ending = false; active = true; failures = 0;
            createRecognizer();
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            registered = true;
            handler.postDelayed(() -> finishSession("15分経過：声操作を終了しました"), 15 * 60 * 1000L);
            listen();
        } catch (RuntimeException error) { finishSession("声操作を開始できません。アプリから再度ONにしてください"); }
        return START_NOT_STICKY;
    }

    private void createRecognizer() {
        releaseRecognizer();
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        final int token = generation;
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean current() { return active && !ending && token == generation && listening; }
            public void onReadyForSpeech(Bundle params) {
                if (current()) updateStatus("声操作 ON：聞いています");
            }
            public void onBeginningOfSpeech() {
                if (current()) updateStatus("声操作 ON：声を検出しました");
            }
            public void onEndOfSpeech() {
                if (!current()) return;
                handler.removeCallbacks(watchdog);
                handler.postDelayed(watchdog, 5000);
            }
            public void onResults(Bundle results) {
                if (!current()) return;
                command(results, false);
                completeListening(); failures = 0; retry(200);
            }
            public void onPartialResults(Bundle results) { if (current()) command(results, true); }
            public void onError(int error) { if (current()) handleError(error); }
            public void onRmsChanged(float rms) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEvent(int type, Bundle params) {}
        });
    }

    private void releaseRecognizer() {
        generation++; listening = false; handler.removeCallbacks(watchdog);
        SpeechRecognizer old = recognizer; recognizer = null;
        if (old != null) { old.cancel(); old.destroy(); }
    }
    private void listen() {
        if (!active || ending || recognizer == null || listening) return;
        listening = true; stoppedInUtterance = false;
        updateStatus("声操作 ON：マイク接続中");
        handler.removeCallbacks(watchdog); handler.postDelayed(watchdog, 20000);
        try { recognizer.startListening(recognitionRequest()); }
        catch (RuntimeException error) { reconnect("音声認識を再接続中"); }
    }
    private void completeListening() { listening = false; handler.removeCallbacks(watchdog); }
    private void retry(long delay) {
        if (!active || ending) return;
        handler.removeCallbacks(listenAgain); handler.postDelayed(listenAgain, delay);
    }
    private void reconnect(String reason) {
        if (!active || ending) return;
        // A lost recognizer must never leave scrolling without a working stop command.
        stopScrolling();
        if (++failures >= 8) {
            finishSession("マイクに接続できません。他の音声アプリを閉じ、マイクのアクセスをONにして再度お試しください");
            return;
        }
        updateStatus("声操作 ON：" + reason);
        try { createRecognizer(); retry(Math.min(5000, 600L * failures)); }
        catch (RuntimeException error) { finishSession("音声認識に接続できません。端末の音声認識アプリを確認してください"); }
    }
    private void handleError(int error) {
        completeListening();
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            failures = 0; updateStatus("声操作 ON：待機中"); retry(250); return;
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            finishSession("マイクが使えません。アプリのマイク権限と端末の「マイクのアクセス」をONにしてください");
        } else if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
            finishSession("日本語データが未準備です。アプリの「日本語音声を準備する」を押してください");
        } else if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
            finishSession("端末内の日本語音声に未対応です。音声認識アプリを更新してください");
        } else if (error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS) {
            stopScrolling(); updateStatus("声操作 ON：認識の混雑で10秒待機中"); retry(10000);
        } else reconnect("認識エラー " + error + "・再接続中");
    }
    private void updateStatus(String value) {
        status = value;
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_VOICE_STATUS).setPackage(getPackageName()));
    }
    private void stopScrolling() {
        sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_STOP_SCROLL).setPackage(getPackageName()));
    }
    private void finishSession(String reason) {
        ending = true; active = false; handler.removeCallbacksAndMessages(null);
        releaseRecognizer(); stopScrolling(); updateStatus(reason);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        ending = true; active = false; handler.removeCallbacksAndMessages(null); releaseRecognizer();
        if (status.startsWith("声操作 ON")) updateStatus("声操作 OFF");
        if (registered) { unregisterReceiver(screenReceiver); registered = false; }
        stopScrolling(); super.onDestroy();
    }
    private void command(Bundle results, boolean partial) {
        ArrayList<String> phrases = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (phrases == null || phrases.isEmpty()) return;
        // Best hypothesis only: weaker alternatives must not trigger unwanted swipes.
        int command = VoiceCommand.parse(phrases.get(0));
        if (command == VoiceCommand.STOP) {
            stoppedInUtterance = true; stopScrolling(); updateStatus("声操作 ON：停止しました");
        } else if (!partial && !stoppedInUtterance && command == VoiceCommand.START) {
            sendBroadcast(new Intent(AutoScrollAccessibilityService.ACTION_START_SCROLL).setPackage(getPackageName()));
            updateStatus("声操作 ON：スクロール開始");
        } else if (!partial && command == VoiceCommand.NONE) {
            updateStatus("声操作 ON：命令を聞き取れませんでした。スクロール／ストップと話してください");
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
