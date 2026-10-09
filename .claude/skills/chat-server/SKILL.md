---
name: chat-server
description: Запуск, настройка, использование и диагностика чат-сервера (Ktor + Koog + DeepSeek) в этом проекте. Используй при запуске сервера, вызовах его API (/chat, /history, /save, /health), разборе логов и настройке окружения.
---

# Чат-сервер (Koog Chat History Tool)

Ktor 3-сервер (Kotlin, JDK 17, Gradle без wrapper — команда `gradle`) в корне проекта. ЛЛМ — DeepSeek через фреймворк Koog (JetBrains); агент — ReAct, ему доступен инструмент `save_chat_history`.

## Запуск

```bash
cd /d/ai/projects/chat
gradle run > chat-server.log 2>&1   # в фоне; логи — в chat-server.log
```

- Готовность: опрос `curl -s http://localhost:8080/health` → `{"status":"ok","llmEnabled":true}`.
- Первый запуск долгий (загрузка зависимостей Gradle) — до ~90 секунд.
- Альтернатива: `gradle installDist && build/install/koog-chat-history/bin/koog-chat-history`.

## Остановка

```bash
netstat -ano | grep LISTENING | grep ":8080"   # последнее число — PID
taskkill //F //PID <pid>                        # двойные слэши обязательны в Git Bash
```

## Переменные окружения

| Переменная          | По умолчанию     | Назначение                                            |
|---------------------|------------------|-------------------------------------------------------|
| `DEEPSEEK_API_KEY`  | —                | ключ DeepSeek; без него `/chat` возвращает 503        |
| `DEEPSEEK_BASE_URL` | api.deepseek.com | базовый URL провайдера (прокси/кастомные)             |
| `CHAT_HISTORY_DIR`  | `chat-history`   | каталог для файлов истории (создаётся автоматически)  |
| `PORT`              | `8080`           | порт                                                  |

## API

- `POST /chat` — `{"conversationId":"id","message":"текст"}` → `{"conversationId","reply","history"}`. История разговора живёт в памяти сервера; весь контекст передаётся ЛЛМ. Если попросить ЛЛМ сохранить историю — агент вызовет `save_chat_history`.
- `GET /chat/{conversationId}` — история; 404, если пусто.
- `POST /chat/{conversationId}/save?fileName=...` — запись в файл напрямую, без ЛЛМ; 404, если пусто.
- `GET /health` — `{"status":"ok","llmEnabled":bool}`.

```bash
# Кириллицу в теле запроса не передавай инлайном в -d: Git Bash на Windows шлёт её в cp866,
# сервер получит кракозябры. Пиши пейлоад в UTF-8 файл и используй --data-binary @file.
curl -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" \
  --data-binary @payload.json      # {"conversationId":"my-chat","message":"Привет!"}
curl -s http://localhost:8080/chat/my-chat
curl -s -X POST "http://localhost:8080/chat/my-chat/save?fileName=my-chat.txt"
curl -s http://localhost:8080/health
```

## Логи (`chat-server.log`)

Логгеры: `[SERVER]` — старт/конфигурация; `[LLM]` — каждый запрос/ответ ЛЛМ (включая внутренние шаги ReAct-агента); `[TOOL]` — вызовы `save_chat_history`; `[CHAT]` — маршруты; плюс Ktor CallLogging.

Типовые признаки проблем:
- `DEEPSEEK_API_KEY is not set` / 503 → задать ключ.
- `[LLM] request FAILED` → неверный ключ, сеть или `DEEPSEEK_BASE_URL`.
- `Address already in use` → порт занят, остановить старый экземпляр.
- `[TOOL] ... saved N messages to ...` — успешное сохранение истории.

## Файлы истории

Формат: заголовок (`=== Chat history ===`, дата, число сообщений) + блоки `[user]`/`[assistant]`. Имена файлов санитизируются (путевые разделители и спецсимволы → `_`), запись возможна только внутрь каталога истории.

## Прочие команды

- Тесты: `gradle test` (отчёты в `build/test-results/`).
- Сборка: `gradle build`.
