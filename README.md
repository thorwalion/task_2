# Koog Chat History Tool

Ktor-сервер с инструментом для ЛЛМ, который сохраняет историю чата в текстовый файл.

- **Фреймворк для вызовов ЛЛМ** — [Koog](https://github.com/JetBrains/koog) (JetBrains), модуль `koog-ktor`.
- **Роутинг** — Ktor 3.
- **Логирование** — все запросы к ЛЛМ и ответы от неё пишутся в консоль (Logback console appender):
  - `LoggingLLMClient` оборачивает реальный LLM-клиент и логирует **каждый** запрос/ответ,
    включая внутренние шаги ReAct-агента (reasoning, tool planning и т.д.);
  - маршруты логируют пользовательский ввод и итоговый ответ;
  - инструмент логирует свои вызовы и результат;
  - Ktor `CallLogging` логирует HTTP-запросы.

## Требования

- JDK 17+
- `DEEPSEEK_API_KEY` — ключ API DeepSeek (без него сервер стартует, но маршрут `/chat` вернёт 503)

## Сборка и запуск

```bash
gradle run
# или
gradle installDist && build/install/koog-chat-history/bin/koog-chat-history
```

Переменные окружения:

| Переменная          | По умолчанию     | Назначение                                      |
|---------------------|------------------|-------------------------------------------------|
| `DEEPSEEK_API_KEY`  | —                | ключ API DeepSeek (обязателен для вызовов ЛЛМ)  |
| `DEEPSEEK_BASE_URL` | api.deepseek.com | базовый URL провайдера (для прокси/кастомных)   |
| `CHAT_HISTORY_DIR`  | `chat-history`   | каталог, куда инструмент пишет текстовые файлы  |
| `PORT`              | `8080`           | порт сервера                                    |

## API

### `POST /chat` — сообщение ЛЛМ

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "my-chat", "message": "Привет!"}'
```

Ответ: `{"conversationId": "...", "reply": "...", "history": [...]}`.

Если в сообщении попросить ЛЛМ сохранить историю («сохрани историю чата в файл»),
агент вызовет инструмент `save_chat_history`, и файл появится в каталоге `chat-history/`.

### `GET /chat/{conversationId}` — посмотреть историю

```bash
curl http://localhost:8080/chat/my-chat
```

### `POST /chat/{conversationId}/save?fileName=...` — сохранить историю напрямую

Вызывает тот же инструмент без участия ЛЛМ:

```bash
curl -X POST "http://localhost:8080/chat/my-chat/save?fileName=my-chat.txt"
```

### `GET /health` — статус сервера

```bash
curl http://localhost:8080/health
```

## Как это устроено

```
client ──POST /chat──▶ Ktor (routing)
                          │
                          ▼
                     install(Koog)          ← Koog plugin: LLM-клиенты + конфигурация агента
                          │                    (system prompt, maxAgentIterations, registerTools)
                          ▼
                     call.aiAgent(reActStrategy(), DeepSeekModels.DeepSeekV4Pro, input)
                          │
                          ▼
              DeepSeekLLMClient ── wrapped ──▶ LoggingLLMClient   ← логирование каждого запроса/ответа в консоль
                          │
                          ▼
              ToolRegistry: SaveChatHistoryTool (SimpleTool)      ← ЛЛМ вызывает его для сохранения истории
                          │
                          ▼
                chat-history/<fileName>.txt
```

- `SaveChatHistoryTool` — `SimpleTool<SaveChatHistoryArgs>` из Koog. Аргументы (`fileName`,
  `messages`) описываются JSON-схемой, которую Koog передаёт ЛЛМ; результат — строка-подтверждение.
- История хранится в памяти (`ChatHistoryStore`) по `conversationId`; при каждом запросе в ЛЛМ
  передаётся весь контекст, поэтому модель может передать полную историю в инструмент.
- В `install(Koog) { agentConfig { registerTools { ... } } }` инструмент регистрируется
  глобально и доступен всем агентам, создаваемым в маршрутах.

## Пример логов в консоли

```
12:00:01.234 INFO  [LLM]  - [LLM] >>> request (model=deepseek-v4-pro, tools=[save_chat_history]):
[system]
You are a helpful chat assistant ...
[user]
user: Привет!
...
12:00:03.456 INFO  [LLM]  - [LLM] <<< response (model=deepseek-v4-pro, 2200 ms, finishReason=tool_calls):
[assistant]
<CallTool>: ...
12:00:03.460 INFO  [TOOL] - [TOOL] save_chat_history invoked: fileName=chat-history.txt, messages=3
12:00:03.461 INFO  [TOOL] - [TOOL] save_chat_history saved 3 messages to D:\ai\projects\chat\chat-history\chat-history.txt
```

## Файл, создаваемый инструментом

```
=== Chat history ===
Saved at: 2026-10-09 12:00:03
Messages: 3

[user]
Привет!

[assistant]
Привет! Чем могу помочь?

[user]
Сохрани историю чата в файл chat-history.txt
```
