package com.example.aipsy;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String GIGACHAT_AUTH_KEY = Secrets.GIGACHAT_AUTH_KEY;

    private static final String PREFS_NAME    = "aipsy_prefs";
    private static final String KEY_THEME     = "theme_mode";
    private static final String KEY_FIRST_RUN = "first_run_shown";
    private static final String KEY_TTS_ON    = "tts_enabled";

    private GigaChatClient gigaChat;
    private ChatAdapter    adapter;
    private RecyclerView   recyclerView;
    private EditText       inputEditText;
    private View           sendButton;
    private View           micButton;
    private ImageView      micIcon;
    private ImageView      ttsToggle;
    private TextView       tvStatus;

    private TextToSpeech tts;
    private boolean ttsReady    = false;
    private boolean ttsEnabled  = true;
    private boolean isListening = false;
    private int adapterMessageCount = 0;

    private final ActivityResultLauncher<Intent> speechLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            setListening(false);
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                ArrayList<String> matches = result.getData()
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    inputEditText.setText(spoken);
                    sendMessage();
                }
            }
        });

    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startListening();
            else Toast.makeText(this, "Нужно разрешение микрофона", Toast.LENGTH_SHORT).show();
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int savedTheme = getPrefs().getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getInsetsController().setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);

        View headerLayout = findViewById(R.id.headerLayout);
        ViewCompat.setOnApplyWindowInsetsListener(headerLayout, (v, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBar + dpToPx(16),
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        View inputBarLayout = findViewById(R.id.inputBarLayout);
        ViewCompat.setOnApplyWindowInsetsListener(inputBarLayout, (v, insets) -> {
            int ime    = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottom = Math.max(ime, navBar);
            v.setPadding(v.getPaddingLeft(), dpToPx(10), v.getPaddingRight(), bottom + dpToPx(10));
            return insets;
        });

        gigaChat  = new GigaChatClient(GIGACHAT_AUTH_KEY);
        adapter   = new ChatAdapter();

        recyclerView  = findViewById(R.id.recyclerView);
        inputEditText = findViewById(R.id.inputEditText);
        sendButton    = findViewById(R.id.sendButton);
        micButton     = findViewById(R.id.micButton);
        micIcon       = findViewById(R.id.micIcon);
        ttsToggle     = findViewById(R.id.ttsToggle);
        tvStatus      = findViewById(R.id.tvStatus);

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(adapter);

        Message welcome = new Message(
                "Здравствуйте! Я здесь, чтобы выслушать вас.\nО чём вы хотите поговорить?",
                Message.Type.BOT);
        adapter.addMessage(welcome);
        ChatHistory.add("bot", welcome.getText());
        adapterMessageCount = 1;

        sendButton.setOnClickListener(v -> sendMessage());
        micButton.setOnClickListener(v -> onMicClick());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnCall).setOnClickListener(v ->
                startActivity(new Intent(this, VoiceCallActivity.class)));

        ttsEnabled = getPrefs().getBoolean(KEY_TTS_ON, true);
        updateTtsToggleIcon();
        ttsToggle.setOnClickListener(v -> {
            ttsEnabled = !ttsEnabled;
            getPrefs().edit().putBoolean(KEY_TTS_ON, ttsEnabled).apply();
            if (!ttsEnabled && ttsReady) tts.stop();
            updateTtsToggleIcon();
            Toast.makeText(this, ttsEnabled ? "Озвучивание включено" : "Озвучивание выключено",
                    Toast.LENGTH_SHORT).show();
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(new Locale("ru", "RU"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setSpeechRate(0.92f);
                tts.setPitch(1.0f);
                ttsReady = true;
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeVisible) scrollToBottom();
            return insets;
        });

        showFirstRunDialog();
    }

    private void onMicClick() {
        if (isListening) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (ttsReady) tts.stop();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите — я слушаю...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            setListening(true);
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            setListening(false);
            Toast.makeText(this, "Распознавание речи не поддерживается", Toast.LENGTH_SHORT).show();
        }
    }

    private void setListening(boolean listening) {
        isListening = listening;
        micButton.setBackground(ContextCompat.getDrawable(this,
                listening ? R.drawable.bg_mic_listening : R.drawable.bg_mic_button));
        micIcon.setImageResource(listening ? R.drawable.ic_mic_off : R.drawable.ic_mic);
    }

    private void updateTtsToggleIcon() {
        ttsToggle.setAlpha(ttsEnabled ? 1.0f : 0.35f);
    }

    private void sendMessage() {
        String text = inputEditText.getText().toString().trim();
        if (text.isEmpty()) return;

        CrisisDetector.Level level = CrisisDetector.analyze(text);

        if (level == CrisisDetector.Level.CRISIS) {
            adapter.addMessage(new Message(text, Message.Type.USER));
            ChatHistory.add("user", text);
            inputEditText.setText("");
            scrollToBottom();
            showCrisisDialog();
            return;
        }

        adapter.addMessage(new Message(text, Message.Type.USER));
        ChatHistory.add("user", text);
        inputEditText.setText("");
        setTyping(true);

        gigaChat.sendMessage(text, new GigaChatClient.Callback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    setTyping(false);
                    adapter.addMessage(new Message(response, Message.Type.BOT));
                    ChatHistory.add("bot", response);
                    scrollToBottom();
                    if (level == CrisisDetector.Level.HIGH_STRESS) showHighStressNote();
                    if (ttsEnabled && ttsReady) {
                        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null,
                                "resp_" + System.currentTimeMillis());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setTyping(false);
                    adapter.addMessage(new Message("Ошибка связи: " + error, Message.Type.BOT));
                    scrollToBottom();
                });
            }
        });
    }

    private void setTyping(boolean typing) {
        sendButton.setEnabled(!typing);
        micButton.setEnabled(!typing);
        tvStatus.setText(typing ? "● Печатает..." : "● Онлайн");
        tvStatus.setTextColor(getColor(typing ? R.color.primary_light : R.color.online_green));
        if (typing) { adapter.showTyping(); scrollToBottom(); }
        else         { adapter.hideTyping(); }
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            int last = adapter.getItemCount() - 1;
            if (last >= 0) recyclerView.smoothScrollToPosition(last);
        });
    }

    private void showFirstRunDialog() {
        SharedPreferences prefs = getPrefs();
        if (prefs.getBoolean(KEY_FIRST_RUN, false)) return;
        prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Добро пожаловать! 👋")
                .setMessage(
                    "Вы можете общаться текстом или голосом — нажмите кнопку 🎤 и говорите.\n\n" +
                    "Если заметите ошибки — нажмите ⚙ → «Отправить отчёт»."
                )
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void showCrisisDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Вы не одни")
                .setMessage(
                    "Я вижу, что вам сейчас очень тяжело.\n\n" +
                    "Пожалуйста, позвоните на телефон доверия — " +
                    "там работают живые психологи, бесплатно и круглосуточно:\n\n" +
                    "📞  8-800-2000-122\n\n" +
                    "Я рядом, но живой специалист поможет вам лучше."
                )
                .setPositiveButton("Позвонить сейчас", (d, w) ->
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:88002000122"))))
                .setNegativeButton("Продолжить разговор", null)
                .setCancelable(false)
                .show();
    }

    private void showHighStressNote() {
        new AlertDialog.Builder(this)
                .setTitle("Я здесь")
                .setMessage(
                    "Похоже, вам сейчас нелегко. Я рядом и слушаю.\n\n" +
                    "Если захотите поговорить с живым психологом — " +
                    "это нормально. Телефон доверия: 8-800-2000-122 (бесплатно)."
                )
                .setPositiveButton("Понятно", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int adapterCount = adapter.getItemCount();
        java.util.List<ChatHistory.Entry> all = ChatHistory.getAll();
        if (all.size() > adapterMessageCount) {
            for (int i = adapterMessageCount; i < all.size(); i++) {
                ChatHistory.Entry e = all.get(i);
                Message.Type type = "user".equals(e.role) ? Message.Type.USER : Message.Type.BOT;
                adapter.addMessage(new Message(e.text, type));
            }
            adapterMessageCount = all.size();
            scrollToBottom();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
