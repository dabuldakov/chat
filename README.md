# chat

Бэкенд чата: Java 21, Spring Boot 4.0.5, PostgreSQL, JWT, WebSocket, Firebase Cloud Messaging.

## Требования

- JDK 21
- Docker (нужен для интеграционных тестов — PostgreSQL поднимается в Testcontainers)

Gradle ставить не нужно, используется wrapper — `./gradlew`.

## Виды тестов

| Тип | Где лежат | Суффикс | Что нужно | Команда |
|-----|-----------|---------|-----------|---------|
| Юнит-тесты | `src/test/java` | `*Test` | ничего | `./gradlew test` |
| Интеграционные | `src/integrationTest/java` | `*IT` | Docker | `./gradlew integrationTest` |

Интеграционные тесты поднимают полный Spring-контекст и подключаются к реальному PostgreSQL,
запущенному в контейнере (`postgres:17-alpine`, образ из `PostgresTestContainer`).
Профиль `integration-test`, схема создаётся Hibernate (`ddl-auto`), таблицы очищаются между тестами.

## Запуск

```bash
# только юнит-тесты
./gradlew test

# только интеграционные (нужен Docker)
./gradlew integrationTest

# всё вместе
./gradlew test integrationTest
```

Запуск конкретного класса или метода:

```bash
./gradlew test --tests "com.chat.server.controller.MessageControllerMappingTest"
./gradlew test --tests "com.chat.server.controller.MessageControllerMappingTest.*"

# один интеграционный класс
./gradlew integrationTest --tests "com.chat.server.integration.ChatServiceIT"
```

## Отчёты

- Юнит-тесты: `build/reports/tests/test/index.html`
- Интеграционные: `build/reports/tests/integrationTest/index.html`

## Полезно

```bash
# перезапустить тесты, игнорируя кэш Gradle
./gradlew test integrationTest --rerun-tasks

# очистить результаты перед прогоном
./gradlew cleanTest cleanIntegrationTest test integrationTest
```

Если интеграционные тесты падают на старте — проверьте, что Docker запущен (`docker info`)
и что есть доступ к `postgres:17-alpine` (первый запуск скачивает образ).

## Структура

```
src/test/java/com/chat/server/                    # юнит-тесты
src/integrationTest/java/com/chat/server/integration/
├── AbstractIntegrationTest.java                  # база: контекст + Testcontainers + очистка БД
├── containers/                                   # фабрика PostgreSQL-контейнера
└── *IT.java                                      # интеграционные тесты
src/integrationTest/resources/application-integration-test.yaml
```
