# План: аудиосообщения (голосовые) — отложено на развитие

Статус: **отложено**. Ниже — согласованный дизайн на будущее, чтобы вернуться к нему
без повторного анализа.

## Что уже есть (готовая база)
- БД: `attachments` (есть enum `AUDIO`, колонка `duration`, `metadata` JSON);
  `messages.message_type` уже включает `AUDIO`.
- Загрузка файлов: `POST /api/attachments/upload` (multipart) → MinIO
  (`FileUploadService.store("attachments", …)`, тип `audio/*` → `AUDIO`).
- Скачивание: `GET /api/attachments/{uuid}` — авторизованное, с проверкой участия в чате.
- Приложение: Media3 ExoPlayer + `media3-datasource-okhttp`; паттерн auth+кэш —
  `utils/VideoPlayerHelper.kt`; Glide уже шлёт токен для `chat-muzea.su`
  (`GlideModule.AUTH_HOSTS`); кэши `VideoCache` (LRU 300 МБ), `GifCache` (JSON).

## Гэпы, которые надо закрыть
1. **Привязка вложений к сообщению не работает:** `MessageService.sendMessage`
   игнорирует `attachmentUuids` (вложения создаёт только GIF-путь).
   `AttachmentService.attachToMessage` есть, но не вызван нигде, кроме тестов.
2. **Нет Range/seek** в чате (нет `HttpRange`/`206`).
3. В приложении **нет** `RECORD_AUDIO`, нет API/DTO для `/api/attachments`,
   нет команды записи и воспроизведения аудио.
4. `AttachmentResponse`/`MessageAttachment` на клиенте не мапят `mimeType`,
   `fileSize`, `duration`, `attachmentUuid`.

## 1. Запись
- `MediaRecorder`, **AAC в `.m4a`** (совместимо с API 18; Opus/OGG — с API 29, позже).
- Моно, 32–64 kbps (~0.25–0.5 МБ/мин).
- Права: `RECORD_AUDIO` (runtime; `dexter` уже подключён, но не используется).
- UI: кнопка микрофона в панели ввода; таймер, «свайп для отмены», лимит длительности.
- Волна (waveform): опрос `getMaxAmplitude()` ~каждые 80 мс → массив 0–100 (≈48 точек),
  передаём в `metadata` вложения — получатель рисует волну без скачивания аудио.

## 2. Хранение
- Аудио — бинарник в **MinIO**, метаданные — в Postgres (`attachments`). В БД не кладём.
- Загрузка: существующий `POST /api/attachments/upload` → attachment; затем отправка
  сообщения с ссылкой на attachment (нужна доработка п.1 гэпов).
- Лимиты на старте: длительность ≤ 5 мин, размер ≤ 10 МБ (бэкенд multipart сейчас 5 МБ).

## 3. Отдача и воспроизведение
- Скачивание через `GET /api/attachments/{uuid}` (auth). Для плеера — ExoPlayer с
  `OkHttpDataSource` (Bearer) + `CacheDataSource` (паттерн `VideoPlayerHelper`).
- Range/seek — позже (добавить `HttpRange`/`206` на эндпоинт скачивания).
- Пуш: «🎤 Голосовое сообщение».

## 4. Кэш на устройстве (3 слоя)
1. HTTP-кэш OkHttp/Glide — вспомогательный.
2. **Media3 `SimpleCache`** — рекомендуется: отдельный `AudioCache` (cacheDir/audio,
   ~200 МБ LRU), чтение через `CacheDataSource` (повтор — из кэша, играет по мере загрузки).
3. «Сохранить себе» навсегда — не сейчас.
- Волна — из метаданных, аудио **не автоскачиваем**: играем по тапу, потом кэш
  (опционально префетч свежих).

## 5. Стратегии обмена
- Транспорт: сервер-релей через MinIO (рекомендуется). Альтернативы: P2P WebRTC
  (NAT/оффлайн), presigned прямой upload/download.
- Загрузка: multipart к chat API (рекомендуется); presigned — позже при нагрузке.
- Воспроизведение: прогрессивный стрим + кэш (рекомендуется); Range — позже.
- Надёжность: оптимистичный UI, статусы sent/uploading/failed, оффлайн-очередь с
  ретраем. Доставка получателям — текущий sync сообщений (поллинг ~3 с), без WebSocket.

## 6. Модель данных (что менять)
- Backend: связать вложения в `sendMessage`; отдавать в `AttachmentDto` `mimeType`,
  `fileSize`, `duration` (duration есть), волну — в `metadata`.
- App: добавить `attachments` в `SendMessageRequest`; в `AttachmentResponse`/
  `MessageAttachment` — `attachmentUuid`, `mimeType`, `fileSize`, `duration`.

## 7. Приватность
- Аудио — персональные данные: доступ только участникам чата (уже есть), **обновить
  текст согласия** («голосовые сообщения»). E2E-шифрования нет (как и для картинок).

## 8. Фазы
- **Фаза 1 (MVP):** привязка вложений + AUDIO на бэке; запись (AAC+m4a), волна,
  загрузка, оптимистичный UI; аудио-бабл с ExoPlayer (auth + `AudioCache`),
  тап-воспроизведение, «играет только одно»; без Range.
- **Фаза 2:** seek (Range), скорость 1x/1.5x/2x, оффлайн-очередь/ретраи, префетч,
  менеджер воспроизведения, Opus.
- **Фаза 3:** транскрипция, P2P.

## 9. Открытые вопросы (решить перед стартом)
1. Формат: AAC+m4a (рекомендую) или сразу Opus?
2. Отдача: серверный auth-эндпоинт (рекомендую) или presigned MinIO?
3. Кэш: отдельный `AudioCache` (рекомендую) или делить `VideoCache`?
4. UX: hold-to-record или тап старт/стоп?
5. Автоскачивание: только по тапу (рекомендую) или префетч свежих?
6. Плеер: один общий на экран (рекомендую) или по одному на бабл (как у GIF)?
7. Лимиты: 5 мин / 10 МБ — ок?
