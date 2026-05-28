package com.example.aipsy;

/**
 * ШАБЛОН секретных данных.
 *
 * 1. Скопируйте этот файл и переименуйте в Secrets.java
 * 2. Впишите свои значения
 *
 * Файл Secrets.java добавлен в .gitignore и не попадёт в репозиторий.
 */
public final class Secrets {

    // "Авторизационные данные" из developers.sber.ru → вкладка "Ключи"
    public static final String GIGACHAT_AUTH_KEY = "ВАШ_КЛЮЧ_GIGACHAT";

    // SMTP-аккаунт для отправки отчётов об ошибках
    public static final String FROM_EMAIL   = "отправитель@yandex.ru";
    public static final String APP_PASSWORD = "пароль_приложения";   // Яндекс ID → Пароли приложений
    public static final String TO_EMAIL     = "получатель@gmail.com";

    private Secrets() {}
}
