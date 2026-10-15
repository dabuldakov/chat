package com.chat.server.identity;

import com.chat.server.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничитель частоты для login/register: не больше N попыток за окно
 * (по умолчанию 10 за 5 минут на ключ).
 *
 * <p>In-memory: рассчитан на одну ноду. При переходе на несколько нод состояние
 * нужно вынести в общий стор (Redis).
 *
 * <p>Ключ для логина — имя пользователя (защита от перебора пароля конкретного
 * аккаунта). Успешный вход сбрасывает счётчик неудач; для регистрации ключ —
 * IP-адрес (защита от массового создания аккаунтов).
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.auth.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${app.auth.rate-limit.window-seconds:300}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    /** Бросает 429, если лимит попыток по ключу уже исчерпан. */
    public void check(String key) {
        Window window = windows.get(key);
        if (window != null && !isExpired(window) && countOf(window) >= maxAttempts) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new RateLimitExceededException("Too many attempts, try again later");
        }
    }

    /** Учитывает попытку (инкремент в текущем окне). */
    public void record(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, current) ->
                (current == null || now - current.start >= windowMillis) ? new Window(now) : current);
        synchronized (window) {
            window.count++;
        }
    }

    /** Попытка + инкремент одним вызовом. */
    public void checkAndRecord(String key) {
        check(key);
        record(key);
    }

    /** Сброс счётчика по ключу (например, после успешного входа). */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Полный сброс (используется в тестах). */
    public void reset() {
        windows.clear();
    }

    private boolean isExpired(Window window) {
        return System.currentTimeMillis() - window.start >= windowMillis;
    }

    private int countOf(Window window) {
        synchronized (window) {
            return window.count;
        }
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
