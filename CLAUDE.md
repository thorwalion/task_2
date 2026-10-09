# Koog Chat History Tool

Ktor-сервер, который через фреймворк [Koog](https://github.com/JetBrains/koog) (JetBrains) общается с ЛЛМ (DeepSeek) и умеет сохранять историю чата в текстовый файл с помощью инструмента `save_chat_history` (ReAct-агент).

## Стек

- Kotlin 2.3.10, JDK 17, Gradle (без wrapper — используется системный `gradle`)
- Ktor 3.3.3 (Netty, ContentNegotiation, CallLogging)
- Koog 1.3.0-beta (`koog-ktor`, `prompt-executor-deepseek-client`, `http-client-ktor`)
- Logback (все логи — в консоль; логгеры `SERVER`, `LLM`, `TOOL`, `CHAT`)

## Структура и архитектура

- `src/main/kotlin/chat/Application.kt` — `main()` и `module()`: создание инструмента, `install(Koog) { llm { ... }; agentConfig { prompt/system, maxAgentIterations, registerTools } }`, маршруты `/health` и `chatRoutes(...)`. Модель — `DeepSeekModels.DeepSeekV4Pro`.
- `src/main/kotlin/chat/routes/ChatRoutes.kt` — `POST /chat` (добавляет сообщение в историю, весь контекст передаёт в `aiAgent(reActStrategy(), model, input)`), `GET /chat/{id}`, `POST /chat/{id}/save`.
- `src/main/kotlin/chat/tools/SaveChatHistoryTool.kt` — эталон Koog-инструмента: `SimpleTool<SaveChatHistoryArgs>` + `typeToken`, имя `save_chat_history`, `execute()` возвращает строку-подтверждение для ЛЛМ. Имя файла санитизируется, запись только внутрь `historyDir`.
- `src/main/kotlin/chat/storage/ChatHistoryStore.kt` — история в памяти (`ConcurrentHashMap`), по `conversationId`; при перезапуске пропадает.
- `src/main/kotlin/chat/llm/LoggingLLMClient.kt` — декоратор над `LLMClient`: логирует каждый запрос/ответ ЛЛМ, включая внутренние шаги ReAct-агента.
- `src/main/kotlin/chat/llm/ReasoningFallbackLLMClient.kt` — декоратор над `LLMClient`: страхует ReAct-граф Koog от reasoning-only ответов deepseek-v4-pro (иначе агент застревает с 500) — одна повторная попытка, затем пустой `Text`-part. `Reasoning`-части НЕ удаляются: DeepSeek API требует передавать `reasoning_content` обратно (иначе 400).
- `src/test/kotlin/chat/tools/SaveChatHistoryToolTest.kt` — юнит-тест инструмента.

## Команды

```bash
gradle test                 # юнит-тесты (отчёты в build/test-results/)
gradle build                # сборка
gradle run                  # запуск сервера (блокирующий; для фона: gradle run > chat-server.log 2>&1)
gradle installDist && build/install/koog-chat-history/bin/koog-chat-history   # альтернативный запуск
```

Остановка сервера (Windows/Git Bash):

```bash
netstat -ano | grep LISTENING | grep ":8080"   # последнее число — PID
taskkill //F //PID <pid>                        # двойные слэши обязательны
```

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DEEPSEEK_API_KEY` | — | ключ DeepSeek; без него `/chat` возвращает 503, остальное работает |
| `DEEPSEEK_BASE_URL` | api.deepseek.com | базовый URL провайдера |
| `CHAT_HISTORY_DIR` | `chat-history` | каталог для сохраняемых файлов истории |
| `PORT` | `8080` | порт сервера |

## API

- `POST /chat` `{"conversationId","message"}` → `{"conversationId","reply","history"}`; 400 при пустом сообщении, 503 без ключа API
- `GET /chat/{conversationId}` — история; 404, если пусто
- `POST /chat/{conversationId}/save?fileName=...` — сохранить историю в файл напрямую, без ЛЛМ; 404, если пусто
- `GET /health` — `{"status":"ok","llmEnabled":bool}`

## Конвенции кода

- KDoc/комментарии в коде — на английском; README — на русском.
- Инструменты: пакет `chat.tools`, логгер `TOOL`, валидация через `require`, имя в snake_case, аргументы — `@Serializable` data class.
- Инструменты регистрируются глобально в `registerTools { tool(...) }` в `Application.kt`; правила вызова описываются в `SYSTEM_PROMPT`.
- Новый инструмент должен иметь юнит-тест (`runBlocking` для suspend).

## Инструменты Claude Code в этом проекте

- Команды: `/chat-start`, `/chat-stop`, `/chat-status`, `/chat-send`, `/chat-history`, `/chat-save`, `/chat-test`, `/chat-e2e` (`.claude/commands/`)
- Скиллы: `chat-server` (запуск/API/логи — основной справочник), `koog-tool` (как добавить новый инструмент) — `.claude/skills/`
- Агенты: `chat-tester` (сквозное тестирование API), `koog-tool-author` (разработка), `server-debugger` (диагностика по логам) — `.claude/agents/`
