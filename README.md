# chat

Бэкенд чата: Java 21, Spring Boot 4.0.5, PostgreSQL, JWT, Firebase Cloud Messaging.

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

Для Docker Compose сначала запустите makeup (в нём поднимается MinIO). Chat app подключается
к **той же Docker-сети**, что и MinIO, и обращается к нему по имени `http://minio:9000`.
Узнайте реальное имя сети MinIO (оно зависит от имени compose-проекта makeup):

```bash
docker inspect makeup-backend-minio-1 \
  --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{"\n"}}{{end}}'
```

Добавьте в `chat/.env` доступ к **тому же** MinIO (значения ключей возьмите из конфигурации makeup):

```dotenv
MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=<ключ доступа из makeup>
MINIO_SECRET_KEY=<секретный ключ из makeup>
MINIO_AVATAR_BUCKET=avatars
MINIO_NETWORK=<имя сети из команды выше>
```

`MINIO_NETWORK` обязателен: compose подключает chat к этой внешней сети. Bucket создаётся
при первой загрузке; ключу MinIO нужны права создания bucket и чтения/записи/удаления объектов.

```bash
docker compose up -d --build app
```

Проверить доступность из контейнера chat:

```bash
docker compose exec app wget -qO- http://minio:9000/minio/health/ready && echo " reachable"
```

При запуске вне Docker задайте `MINIO_URL=http://localhost:9000` и те же ключи.
`AvatarFlowIT` использует собственные PostgreSQL и MinIO в Testcontainers,
проверяя загрузку, замену, удаление, публичное чтение и выдачу в контактах/чате.

## Вложения

Вложения сообщений хранятся в MinIO (bucket `MINIO_ATTACHMENT_BUCKET`, по умолчанию
`attachments`), а не на локальном диске контейнера. Аватары чатов — там же. Для доступа
к файлам используется API (`/api/attachments/...`), bucket остаётся закрытым.

## Конфигурация

| Переменная | Назначение | По умолчанию |
|-----------|-----------|--------------|
| `JWT_SECRET` | ключ подписи JWT | dev-заглушка (в prod обязателен) |
| `DATABASE_PASSWORD` | пароль БД | нет (обязателен) |
| `APP_CORS_ALLOWED_ORIGINS` | origin-паттерны через запятую | `http://localhost:*` |
| `MINIO_ATTACHMENT_BUCKET` | bucket вложений | `attachments` |

Токены сессий хранятся в БД только в виде SHA-256 хэша; refresh-токен ротируется при обновлении.

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
