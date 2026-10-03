# chat

Бэкенд чата: Java 21, Spring Boot 4.0.5, PostgreSQL, JWT, Firebase Cloud Messaging.

## Архитектура

Приложение — **stateless модульный монолит**. Код организован по feature-модулям,
границы между которыми проверяются ArchUnit-тестом
(`ModuleBoundariesTest`): модули не образуют циклов, технические модули не
зависят от доменных. Зависимости идут в одну сторону:

```
shared (common/config/util/exception)  ← база для всех
storage (MinIO)                        ← инфраструктура, ни от кого не зависит
identity (users, auth, sessions, presence) ← все доменные модули
conversation (чаты, сообщения, вложения) → identity, block, notification, storage
contacts → identity        block → identity, contacts
notification → identity    sync → conversation
account (удаление аккаунта) → identity, conversation, storage   ← лист-оркестратор
```

Приложение не хранит состояние в памяти между запросами (JWT + сессии в БД,
файлы в MinIO), поэтому его можно запускать в нескольких экземплярах за
балансировщиком. Каждому экземпляру нужны **своя БД** (через `DATABASE_URL`) и
общий MinIO — см. «Независимый инстанс».

## Независимый инстанс

Второй экземпляр поднимается теми же артефактами без пересборки, только через
переменные окружения:

- **БД своя у каждого инстанса** — задайте отдельный `DATABASE_URL` (схема
  создаётся Flyway при старте).
- **MinIO общий** — тот же `MINIO_URL`/ключи и bucket; доступ к объектам идёт
  через API приложения, поэтому bucket остаётся закрытым.
- **JWT_SECRET** может быть общим (доверие между инстансами) или своим — второй
  вариант изолирует токены.
- **FCM и SMTP** общие (`FCM_*`, `MAIL_*`), но необязательны: при
  `FCM_ENABLED=false` push работает как no-op.

Секреты задаются только через окружение; профиль `prod` не содержит дефолтов и
не стартует без `JWT_SECRET`.

## Горизонтальное масштабирование

Несколько нод работают с **одной общей БД** и общим MinIO. Приложение stateless
(JWT + сессии в БД, файлы в MinIO), sticky sessions не нужны. Что учитывается:

- **Кеш** — по умолчанию (dev, одна нода) локальный Caffeine. При нескольких
  нодах включается общий **Redis**: `app.cache.redis.enabled=true`
  (`REDIS_HOST`/`REDIS_PORT`). Иначе `@CacheEvict` на одной ноде не виден
  другим, и до TTL отдаётся устаревшее значение.
- **Шедулеры** — `PresenceSweeper`, `MessagePartitionScheduler` и очистка сессий
  защищены лидер-локом на PostgreSQL advisory locks
  (`DistributedLockService`): задачу выполняет ровно одна нода.
- **Flyway** берёт advisory-lock и накатывает миграции один раз; для лишних нод
  можно выставить `SPRING_FLYWAY_ENABLED=false`.
- **Пул БД** — `hikari.maximum-pool-size` умножается на число нод; при росте
  ставьте PgBouncer и/или read-реплики для `@Transactional(readOnly=true)`.

`docker-compose.yaml` уже готов к масштабированию: `app` не публикует порт,
наружу смотрит `nginx` (порт `8086`) и балансирует между репликами.

```bash
# 2 реплики разово
docker compose up -d --scale app=3

# или постоянно через .env
APP_REPLICAS=3
```

Если ноды делят **одну БД** — это масштабирование. Если у каждого своя БД — это
два независимых инстанса (см. выше).

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

## Удаление аккаунта

`DELETE /api/users/me` (chat JWT, `204 No Content`) уничтожает персональные данные,
а не просто скрывает аккаунт. Реализация — `AccountDeletionService`, тесты —
`AccountDeletionIT` (16 сценариев, считает строки нативными запросами в обход
`@SQLRestriction(is_deleted = false)` и проверяет удаление объектов из MinIO).

Удаляется: строка пользователя; каскадом — сессии (вместе с FCM-токенами),
контакты (в обе стороны) и блокировки; чаты без других активных участников
(с сообщениями, вложениями и аватаром чата); в общих чатах — сообщения
пользователя, его вложения и строка участника; аватар пользователя.

Чаты с другими участниками сохраняются: переписка собеседника — его данные.
Владение группой передаётся следующему участнику (иначе чат остался бы без
владельца и потерял бы управляемость), превью и счётчик сообщений в списке чатов
пересчитываются, чтобы удалённое сообщение не продолжало показываться.

Объекты MinIO и кэши чистятся после коммита транзакции, поэтому откат не оставит
файлы без записей в БД. Android вызывает этот эндпоинт перед удалением новостей и
видео на бэкенде `makeup`; если чат-сервер не подтвердил удаление, локальные данные
не стираются, чтобы пользователь мог повторить попытку.

## Вложения

Вложения сообщений хранятся в MinIO (bucket `MINIO_ATTACHMENT_BUCKET`, по умолчанию
`attachments`), а не на локальном диске контейнера. Аватары чатов — там же. Для доступа
к файлам используется API (`/api/attachments/...`), bucket остаётся закрытым.

## Конфигурация

Конфигурация разбита по профилям: `application.yaml` (общая), `application-dev.yaml`
(локальные значения «из коробки») и `application-prod.yaml` (только окружение,
без небезопасных дефолтов). Профиль по умолчанию — `dev`; в Docker Compose
выставляется `SPRING_PROFILES_ACTIVE=prod`.

| Переменная | Назначение | По умолчанию |
|-----------|-----------|--------------|
| `SPRING_PROFILES_ACTIVE` | профиль (`dev`/`prod`) | `dev` |
| `JWT_SECRET` | ключ подписи JWT | dev-заглушка; в `prod` обязателен |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | доступ к БД инстанса | `localhost:5432/chat_db`, `admin/admin` |
| `APP_CORS_ALLOWED_ORIGINS` | origin-паттерны через запятую | `http://localhost:*` |
| `MINIO_URL` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | объектное хранилище | `localhost:9000`, `minioadmin/minioadmin` |
| `MINIO_AVATAR_BUCKET` / `MINIO_ATTACHMENT_BUCKET` | bucket’ы | `avatars` / `attachments` |
| `FCM_ENABLED` | включать ли Firebase push | `false` |
| `FCM_SERVICE_ACCOUNT_FILE` | путь к сервисному аккаунту в classpath | `firebase-service-account.json` |
| `APP_CACHE_REDIS_ENABLED` | общий Redis-кеш вместо локального Caffeine | `false` (в `prod` — `true`) |
| `REDIS_HOST` / `REDIS_PORT` | адрес Redis | `redis:6379` |
| `APP_REPLICAS` | число реплик `app` в compose | `1` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP для писем | пустые |
| `SERVER_PORT` | порт приложения | `8080` |

Токены сессий хранятся в БД только в виде SHA-256 хэша; refresh-токен ротируется при обновлении.

## Виды тестов

| Тип | Где лежат | Суффикс | Что нужно | Команда |
|-----|-----------|---------|-----------|---------|
| Юнит-тесты | `src/test/java` | `*Test` | ничего | `./gradlew test` |
| Архитектурные | `src/test/java` (`architecture/`) | `*Test` | ничего | `./gradlew test` |
| Интеграционные | `src/integrationTest/java` | `*IT` | Docker | `./gradlew integrationTest` |
| Гейт покрытия | — | — | Docker | `./gradlew jacocoTestCoverageVerification` |

Архитектурные тесты (`ModuleBoundariesTest`) проверяют границы модулей через
ArchUnit: отсутствие циклов и запрет обратных зависимостей. Покрытие строк
бизнес-кода — не ниже 70% (JaCoCo), отчёт объединяет unit- и integration-тесты.

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
src/main/java/com/chat/server/
├── identity/        # пользователи, аутентификация, сессии, presence, аватары
├── conversation/    # чаты, участники, сообщения, вложения
├── storage/         # MinIO: конфиг, файлы, аватары
├── contacts/        # контакты
├── block/           # блокировки
├── notification/    # FCM (Firebase опционален)
├── sync/            # инкрементальная синхронизация
├── account/         # оркестрация удаления аккаунта
└── common|config|exception|util/   # shared-ядро
src/test/java/com/chat/server/                    # юнит- и архитектурные тесты
src/integrationTest/java/com/chat/server/integration/
├── AbstractIntegrationTest.java                  # база: контекст + Testcontainers + очистка БД
├── containers/                                   # фабрика PostgreSQL-контейнера
└── *IT.java                                      # интеграционные тесты
src/integrationTest/resources/application-integration-test.yaml
```

## Автодеплой

При push в `main` GitHub Actions по SSH заходит на VPS, обновляет чекаут и пересобирает
только сервис `app` чата (PostgreSQL и MinIO не трогаются):

```bash
cd "$DEPLOY_PATH"                      # например, /opt/chat
git fetch --prune origin main && git reset --hard origin/main
docker compose up -d --build app
```

Workflow — `.github/workflows/deploy.yml` (можно запустить вручную: Actions → deploy → Run workflow).

Секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Назначение |
|--------|-----------|
| `DEPLOY_HOST` | адрес VPS |
| `DEPLOY_USER` | SSH-пользователь |
| `DEPLOY_SSH_KEY` | приватный SSH-ключ без пароля |
| `DEPLOY_PATH` | каталог чекаута на сервере, например `/opt/chat` |

Первичная настройка сервера:

```bash
git clone git@github.com:dabuldakov/chat.git /opt/chat
cd /opt/chat
# создать .env (см. раздел про MinIO выше; в git не коммитится)
docker compose up -d
```

Важно: chat использует внешнюю Docker-сеть MinIO из makeup, поэтому на сервере
makeup должен быть поднят раньше. Дальнейшие деплои идут автоматически при push в `main`.
