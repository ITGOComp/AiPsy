package com.example.aipsy;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceCallActivity extends AppCompatActivity {

    private enum State { CONNECTING, LISTENING, THINKING, SPEAKING, MUTED, ENDED }

    private static final String GIGACHAT_AUTH_KEY = Secrets.GIGACHAT_AUTH_KEY;

    private GigaChatClient gigaChat;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady   = false;
    private boolean speakerOn  = true;
    private boolean muted      = false;
    private State   state      = State.CONNECTING;

    private TextView tvStatus, tvCallTime, tvLastPhrase;
    private ImageView muteIcon, speakerIcon;
    private View pulse1, pulse2, pulse3;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> permLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                handler.postDelayed(this::startListeningCycle, 500);
            } else {
                tvStatus.setText("Нет доступа к микрофону");
                tvLastPhrase.setText("Разрешите доступ к микрофону в настройках телефона");
            }
        });
    private AnimatorSet pulseAnim;

    private int callSeconds = 0;
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (state == State.ENDED) return;
            callSeconds++;
            int m = callSeconds / 60, s = callSeconds % 60;
            tvCallTime.setText(String.format(Locale.getDefault(), "%02d:%02d", m, s));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_call);

        getWindow().setStatusBarColor(0xFF1A1A2E);
        getWindow().getInsetsController().setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        getWindow().setNavigationBarColor(0xFF1A1A2E);

        tvStatus     = findViewById(R.id.tvCallStatus);
        tvCallTime   = findViewById(R.id.tvCallTime);
        tvLastPhrase = findViewById(R.id.tvLastPhrase);
        muteIcon     = findViewById(R.id.muteIcon);
        speakerIcon  = findViewById(R.id.speakerIcon);
        pulse1       = findViewById(R.id.pulse1);
        pulse2       = findViewById(R.id.pulse2);
        pulse3       = findViewById(R.id.pulse3);

        gigaChat = new GigaChatClient(GIGACHAT_AUTH_KEY);

        findViewById(R.id.btnEndCall).setOnClickListener(v -> endCall());
        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
                tts.setSpeechRate(0.92f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onError(String id) {
                        runOnUiThread(() -> { if (state != State.ENDED) startListeningCycle(); });
                    }
                    @Override public void onDone(String id) {
                        runOnUiThread(() -> { if (state != State.ENDED) startListeningCycle(); });
                    }
                });
                ttsReady = true;
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { setState(State.LISTENING); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { setState(State.THINKING); }

            @Override
            public void onResults(Bundle results) {
                if (state == State.ENDED) return;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    ChatHistory.add("user", spoken);
                    tvLastPhrase.setText("Вы: " + spoken);
                    sendToBot(spoken);
                } else {
                    startListeningCycle();
                }
            }

            @Override
            public void onError(int error) {
                if (state == State.ENDED) return;
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    speechRecognizer.destroy();
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(VoiceCallActivity.this);
                    handler.postDelayed(() -> { if (state != State.ENDED) startListeningCycle(); }, 1000);
                    return;
                }
                handler.postDelayed(() -> { if (state != State.ENDED) startListeningCycle(); }, 700);
            }

            @Override public void onPartialResults(Bundle p) {}
            @Override public void onEvent(int t, Bundle b) {}
        });

        handler.post(timerTick);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            handler.postDelayed(this::startListeningCycle, 800);
        } else {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListeningCycle() {
        if (state == State.ENDED || muted) return;
        setState(State.LISTENING);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechRecognizer.startListening(intent);
    }

    private void sendToBot(String text) {
        setState(State.THINKING);
        gigaChat.sendMessage(text, new GigaChatClient.Callback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    if (state == State.ENDED) return;
                    ChatHistory.add("bot", response);
                    tvLastPhrase.setText("Психолог: " + response);

                    CrisisDetector.Level level = CrisisDetector.analyze(text);
                    String toSpeak = (level == CrisisDetector.Level.CRISIS)
                            ? response + " Пожалуйста, позвоните на телефон доверия: 8 800 2000 122."
                            : response;

                    setState(State.SPEAKING);
                    if (speakerOn && ttsReady) {
                        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "resp");
                    } else {
                        handler.postDelayed(() -> { if (state != State.ENDED) startListeningCycle(); }, 1500);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (state != State.ENDED) startListeningCycle();
                });
            }
        });
    }

    private void toggleMute() {
        muted = !muted;
        if (muted) {
            speechRecognizer.stopListening();
            muteIcon.setImageResource(R.drawable.ic_mic_off);
            muteIcon.setAlpha(0.5f);
            setState(State.MUTED);
        } else {
            muteIcon.setImageResource(R.drawable.ic_mic);
            muteIcon.setAlpha(1.0f);
            startListeningCycle();
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        speakerIcon.setAlpha(speakerOn ? 1.0f : 0.4f);
        if (!speakerOn && ttsReady) tts.stop();
    }

    private void endCall() {
        setState(State.ENDED);
        handler.removeCallbacksAndMessages(null);
        stopPulse();

        speechRecognizer.stopListening();
        speechRecognizer.destroy();

        if (ttsReady) { tts.stop(); tts.shutdown(); }

        finish();
    }

    private void setState(State newState) {
        state = newState;
        switch (newState) {
            case LISTENING:
                tvStatus.setText("● Слушаю...");
                tvStatus.setTextColor(0xFF4CAF50);
                startPulse();
                break;
            case THINKING:
                tvStatus.setText("◌ Думаю...");
                tvStatus.setTextColor(0xFF9FA8DA);
                stopPulse();
                break;
            case SPEAKING:
                tvStatus.setText("♪ Говорю...");
                tvStatus.setTextColor(0xFF7986CB);
                startPulse();
                break;
            case MUTED:
                tvStatus.setText("✕ Микрофон выключен");
                tvStatus.setTextColor(0xFFE57373);
                stopPulse();
                break;
            case CONNECTING:
                tvStatus.setText("Соединение...");
                tvStatus.setTextColor(0xFF9FA8DA);
                break;
            case ENDED:
                tvStatus.setText("Звонок завершён");
                stopPulse();
                break;
        }
    }

    private void startPulse() {
        stopPulse();
        pulseAnim = buildPulseAnim();
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        pulse1.setAlpha(0f); pulse2.setAlpha(0f); pulse3.setAlpha(0f);
        pulse1.setScaleX(1f); pulse1.setScaleY(1f);
        pulse2.setScaleX(1f); pulse2.setScaleY(1f);
        pulse3.setScaleX(1f); pulse3.setScaleY(1f);
    }

    private AnimatorSet buildPulseAnim() {
        AnimatorSet set = new AnimatorSet();

        ObjectAnimator a1x = ObjectAnimator.ofFloat(pulse1, "scaleX", 0.6f, 1f);
        ObjectAnimator a1y = ObjectAnimator.ofFloat(pulse1, "scaleY", 0.6f, 1f);
        ObjectAnimator a1a = ObjectAnimator.ofFloat(pulse1, "alpha",  0.6f, 0f);
        a1x.setDuration(1800); a1y.setDuration(1800); a1a.setDuration(1800);

        ObjectAnimator a2x = ObjectAnimator.ofFloat(pulse2, "scaleX", 0.6f, 1f);
        ObjectAnimator a2y = ObjectAnimator.ofFloat(pulse2, "scaleY", 0.6f, 1f);
        ObjectAnimator a2a = ObjectAnimator.ofFloat(pulse2, "alpha",  0.5f, 0f);
        a2x.setDuration(1800); a2y.setDuration(1800); a2a.setDuration(1800);
        a2x.setStartDelay(400); a2y.setStartDelay(400); a2a.setStartDelay(400);

        ObjectAnimator a3x = ObjectAnimator.ofFloat(pulse3, "scaleX", 0.6f, 1f);
        ObjectAnimator a3y = ObjectAnimator.ofFloat(pulse3, "scaleY", 0.6f, 1f);
        ObjectAnimator a3a = ObjectAnimator.ofFloat(pulse3, "alpha",  0.4f, 0f);
        a3x.setDuration(1800); a3y.setDuration(1800); a3a.setDuration(1800);
        a3x.setStartDelay(800); a3y.setStartDelay(800); a3a.setStartDelay(800);

        set.playTogether(a1x, a1y, a1a, a2x, a2y, a2a, a3x, a3y, a3a);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (pulseAnim != null) pulseAnim.start(); // бесконечный повтор
            }
        });
        return set;
    }

    @Override
    public void onBackPressed() { endCall(); }
}
