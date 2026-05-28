package com.example.aipsy;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "aipsy_prefs";
    private static final String KEY_THEME  = "theme_mode";

    private RadioButton radioLight;
    private RadioButton radioDark;

    private int headerPadH, headerPadB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_settings);

        radioLight = findViewById(R.id.radioLight);
        radioDark  = findViewById(R.id.radioDark);

        int currentMode = getPrefs().getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO);
        radioLight.setChecked(currentMode == AppCompatDelegate.MODE_NIGHT_NO);
        radioDark.setChecked(currentMode == AppCompatDelegate.MODE_NIGHT_YES);

        android.view.View headerLayout = findViewById(R.id.headerLayout);
        headerPadH = headerLayout.getPaddingLeft();
        headerPadB = headerLayout.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            headerLayout.setPadding(headerPadH, statusBar + dp(16), headerPadH, headerPadB);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnThemeLight).setOnClickListener(v ->
                applyTheme(AppCompatDelegate.MODE_NIGHT_NO));
        findViewById(R.id.btnThemeDark).setOnClickListener(v ->
                applyTheme(AppCompatDelegate.MODE_NIGHT_YES));
        findViewById(R.id.btnSendReport).setOnClickListener(v -> sendReport());
    }

    private void applyTheme(int mode) {
        getPrefs().edit().putInt(KEY_THEME, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
        radioLight.setChecked(mode == AppCompatDelegate.MODE_NIGHT_NO);
        radioDark.setChecked(mode == AppCompatDelegate.MODE_NIGHT_YES);
        recreate();
    }

    @SuppressWarnings("deprecation")
    private void sendReport() {
        String time   = new java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String device  = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        String os      = android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")";
        String theme   = getPrefs().getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO)
                         == AppCompatDelegate.MODE_NIGHT_YES ? "Тёмная" : "Светлая";

        // Строим HTML диалога
        StringBuilder dialog = new StringBuilder();
        java.util.List<ChatHistory.Entry> entries = ChatHistory.getAll();
        if (entries.isEmpty()) {
            dialog.append("<p style='color:#9FA8DA;font-style:italic'>Диалог пуст</p>");
        } else {
            for (ChatHistory.Entry e : entries) {
                boolean isUser = "user".equals(e.role);
                String align  = isUser ? "right" : "left";
                String bg     = isUser
                        ? "linear-gradient(135deg,#5C6BC0,#7986CB)"
                        : "#F3F4FB";
                String color  = isUser ? "#fff" : "#1A1A2E";
                String name   = isUser ? "Вы" : "Психолог";
                String nameClr = isUser ? "#C5CAE9" : "#8A8FA8";

                dialog.append("<div style='text-align:").append(align).append(";margin:8px 0'>")
                      .append("<span style='font-size:11px;color:").append(nameClr)
                      .append(";display:block;margin-bottom:3px'>")
                      .append(name).append(" · ").append(e.time).append("</span>")
                      .append("<span style='display:inline-block;max-width:80%;background:")
                      .append(bg).append(";color:").append(color)
                      .append(";padding:10px 14px;border-radius:14px;font-size:14px;")
                      .append("line-height:1.5;text-align:left'>")
                      .append(e.text.replace("\n", "<br>"))
                      .append("</span></div>");
            }
        }

        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
            + "body{font-family:Arial,sans-serif;background:#F0F2FB;margin:0;padding:24px}"
            + ".card{background:#fff;border-radius:16px;padding:32px;max-width:640px;"
            + "      margin:0 auto;border:1px solid #E4E6F5}"
            + ".hdr{background:linear-gradient(135deg,#3949AB,#5C6BC0);border-radius:12px;"
            + "     padding:24px 28px;margin-bottom:28px}"
            + ".hdr h1{color:#fff;margin:0;font-size:22px}"
            + ".hdr p{color:#C5CAE9;margin:6px 0 0;font-size:13px}"
            + "h2{color:#3949AB;font-size:13px;margin:24px 0 10px;text-transform:uppercase;letter-spacing:.5px}"
            + "table{width:100%;border-collapse:collapse}"
            + "td{padding:10px 14px;font-size:14px;color:#1A1A2E;border-bottom:1px solid #E4E6F5}"
            + "td:first-child{color:#8A8FA8;width:42%}"
            + ".chat{background:#F8F9FF;border:1px solid #E4E6F5;border-radius:12px;padding:16px}"
            + ".footer{margin-top:28px;text-align:center;font-size:12px;color:#9FA8DA}"
            + "</style></head><body><div class='card'>"
            + "<div class='hdr'><h1>&#129504; ИИ-Психолог &mdash; Отчёт</h1>"
            + "<p>Сформирован автоматически &middot; " + time + "</p></div>"
            + "<h2>&#128241; Устройство</h2><table>"
            + "<tr><td>Модель</td><td>" + device + "</td></tr>"
            + "<tr><td>Android</td><td>" + os + "</td></tr>"
            + "<tr><td>Тема</td><td>" + theme + "</td></tr>"
            + "<tr><td>Версия приложения</td><td>1.0</td></tr>"
            + "<tr><td>ИИ-модель</td><td>GigaChat (Сбербанк)</td></tr>"
            + "</table>"
            + "<h2>&#128172; Диалог (" + entries.size() + " сообщений)</h2>"
            + "<div class='chat'>" + dialog + "</div>"
            + "<div class='footer'>ИИ-Психолог v1.0 &middot; Автоматический отчёт</div>"
            + "</div></body></html>";

        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Отправка отчёта...");
        progress.setCancelable(false);
        progress.show();

        EmailSender.send(
            "Отчёт об ошибке — ИИ-Психолог · " + time,
            html,
            new EmailSender.Callback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(SettingsActivity.this,
                                "✓ Отчёт отправлен", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(SettingsActivity.this,
                                "Ошибка отправки: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            }
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }
}
