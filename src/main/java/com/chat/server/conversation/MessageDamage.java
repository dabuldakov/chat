package com.chat.server.conversation;

/**
 * Накопление повреждения сообщения от ответов с зарядом.
 *
 * Каждый ответ добавляет к повреждению 60 % силы удара (минимум 1), но не
 * больше 100. Логика вынесена отдельно, чтобы её можно было проверить тестом
 * без поднятия всего сервиса.
 */
public final class MessageDamage {

    public static final int MAX = 100;

    private MessageDamage() {
    }

    public static int normalize(int damage) {
        return Math.max(0, Math.min(MAX, damage));
    }

    /** Новое повреждение после удара силой [power] (0..100). */
    public static int afterHit(int current, int power) {
        int base = normalize(current);
        int hit = Math.max(0, Math.min(MAX, power));
        if (hit == 0) {
            return base;
        }
        int added = Math.max(1, Math.round(hit * 0.6f));
        return Math.min(MAX, base + added);
    }
}
