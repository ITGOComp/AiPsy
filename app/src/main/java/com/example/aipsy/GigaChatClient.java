package com.example.aipsy;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GigaChatClient {

    private static final String TAG = "GigaChatClient";
    private static final String AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String CHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private static final String MODEL = "GigaChat";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");

    private static final String SYSTEM_PROMPT =
            "Ты — профессиональный ИИ-ассистент психолог. Твой стиль: тёплый, принимающий, " +
            "без осуждения. Используй активное слушание: перефразируй слова собеседника, " +
            "задавай открытые вопросы, помогай человеку самому найти ответы. " +
            "Не давай директивных советов сразу. Отвечай на русском языке. " +
            "Если собеседник упоминает желание причинить себе вред или суицидальные мысли, " +
            "обязательно порекомендуй обратиться на телефон доверия 8-800-2000-122 (бесплатно) " +
            "или к живому специалисту, вырази поддержку и не оставляй без ответа. " +
            "Ты не заменяешь врача и не ставишь диагнозы.";

    private final String authKey;
    private final OkHttpClient httpClient;
    private final List<JSONObject> history = new ArrayList<>();

    private String accessToken = null;
    private long tokenExpiryMs = 0;

    public interface Callback {
        void onResponse(String text);
        void onError(String error);
    }

    public GigaChatClient(String authKey) {
        this.authKey = authKey;
        this.httpClient = buildClient();
    }

    private OkHttpClient buildClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sc.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            return new OkHttpClient();
        }
    }

    private void ensureToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryMs - 60_000) return;

        RequestBody body = RequestBody.create("scope=GIGACHAT_API_PERS", FORM);
        Request request = new Request.Builder()
                .url(AUTH_URL)
                .post(body)
                .addHeader("Authorization", "Basic " + authKey)
                .addHeader("RqUID", UUID.randomUUID().toString())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("Ошибка авторизации GigaChat: " + resp.code());
            }
            JSONObject json = new JSONObject(resp.body().string());
            accessToken = json.getString("access_token");
            tokenExpiryMs = json.optLong("expires_at", System.currentTimeMillis() + 1_800_000L);
        } catch (Exception e) {
            throw new IOException("Ошибка парсинга токена: " + e.getMessage());
        }
    }

    public void sendMessage(String userText, Callback callback) {
        new Thread(() -> {
            try {
                ensureToken();

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userText);
                history.add(userMsg);

                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", SYSTEM_PROMPT);
                messages.put(systemMsg);
                for (JSONObject m : history) messages.put(m);

                JSONObject payload = new JSONObject();
                payload.put("model", MODEL);
                payload.put("messages", messages);
                payload.put("temperature", 0.7);

                Request request = new Request.Builder()
                        .url(CHAT_URL)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response resp = httpClient.newCall(request).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        callback.onError("Ошибка API: " + resp.code());
                        return;
                    }
                    JSONObject result = new JSONObject(resp.body().string());
                    String assistantText = result
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    // Сохраняем ответ ассистента в историю
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", assistantText);
                    history.add(assistantMsg);

                    callback.onResponse(assistantText);
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage error", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void clearHistory() {
        history.clear();
    }
}
