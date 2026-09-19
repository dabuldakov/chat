# chat

Бэкенд чата: Java 21, Spring Boot 4.0.5, PostgreSQL, JWT, WebSocket, Firebase Cloud Messaging.

## Требования

- JDK 21
- Docker (нужен для интеграционных тестов — PostgreSQL поднимается в Testcontainers)

Gradle ставить не нужно, используется wrapper — `./gradlew`.

## Аватары пользователей (MinIO)

Android загружает аватар из экрана профиля в `POST /api/users/me/avatar`
(multipart-поле `file`, chat JWT). Ответ: `{"avatarUrl":"/api/avatars/<userUuid>/<version>.png"}`.
`GET /api/users/me` возвращает текущий URL, `DELETE /api/users/me/avatar` удаляет аватар.
Публичный `GET /api/avatars/<userUuid>/<version>.png` выдаёт картинку через chat API:
bucket остаётся закрытым, внешний адрес MinIO приложению не нужен.
Поддерживаются JPEG/PNG до 5 MB; изображение преобразуется в PNG до 512 px.
Новая версия получает новый URL. Контакты, приватные чаты и сообщения возвращают этот URL.

Для Docker Compose сначала запустите makeup (в нём поднимается MinIO и публикуется порт 9000).
Добавьте в `chat/.env` доступ к **тому же** MinIO (значения ключей возьмите из конфигурации makeup):

```dotenv
MINIO_URL=http://90.188.89.63:9000
MINIO_ACCESS_KEY=<ключ доступа из makeup>
MINIO_SECRET_KEY=<секретный ключ из makeup>
MINIO_AVATAR_BUCKET=avatars
```

Chat app обращается к MinIO по опубликованному на хосте порту 9000 через
`extra_hosts: 90.188.89.63:host-gateway` (уже задан в compose) — отдельная Docker-сеть
и её имя не нужны. `MINIO_URL` по умолчанию `http://90.188.89.63:9000`; если сервер
другой, задайте свой адрес. Bucket создаётся при первой загрузке; ключу MinIO нужны
права создания bucket и чтения/записи/удаления объектов.

```bash
docker compose up -d --build app
```

При запуске вне Docker задайте `MINIO_URL=http://localhost:9000` и те же ключи.
`AvatarFlowIT` использует собственные PostgreSQL и MinIO в Testcontainers,
проверяя загрузку, замену, удаление, публичное чтение и выдачу в контактах/чате.

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
