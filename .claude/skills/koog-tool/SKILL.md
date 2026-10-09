---
name: koog-tool
description: Как добавить новый LLM-инструмент (Koog SimpleTool) в этот проект: класс, регистрация, системный промпт, тест. Используй, когда нужно расширить возможности ReAct-агента новым инструментом.
---

# Добавление нового Koog-инструмента

Шаблон — существующий `src/main/kotlin/chat/tools/SaveChatHistoryTool.kt`. Koog 1.3.0-beta, инструмент наследует `ai.koog.agents.core.tools.SimpleTool<T>`.

## Шаг 1. Класс инструмента

Создай `src/main/kotlin/chat/tools/<Name>Tool.kt`:

```kotlin
package chat.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Аргументы инструмента; Koog генерирует из них JSON-схему для ЛЛМ. */
@Serializable
public data class <Name>Args(
    val someField: String,
)

public class <Name>Tool : SimpleTool<<Name>Args>(
    argsType = typeToken<<Name>Args>(),
    name = NAME,
    description = DESCRIPTION,
) {

    private val logger: Logger = LoggerFactory.getLogger("TOOL")

    override suspend fun execute(args: <Name>Args): String {
        logger.info("[TOOL] {} invoked: someField={}", NAME, args.someField)
        require(args.someField.isNotBlank()) { "someField must not be blank" }
        // ... логика инструмента ...
        return "короткое подтверждение для ЛЛМ"
    }

    public companion object {
        public const val NAME: String = "snake_case_name"
        public const val DESCRIPTION: String =
            "Что делает инструмент, когда его вызывать, и точное описание каждого аргумента."
    }
}
```

Правила:
- `name` — snake_case, стабильный идентификатор для ЛЛМ.
- `description` — полная инструкция для ЛЛМ: когда вызывать и что означают аргументы.
- `execute()` возвращает строку-подтверждение — она уходит обратно в ЛЛМ как результат tool call.
- Валидация аргументов через `require`; логирование в логгер `TOOL`.

## Шаг 2. Регистрация

`src/main/kotlin/chat/Application.kt`, блок `install(Koog) { agentConfig { registerTools { ... } } }`:

```kotlin
registerTools {
    tool(saveChatHistoryTool)
    tool(<name>Tool)   // новый инструмент — доступен всем агентам, создаваемым в маршрутах
}
```

## Шаг 3. Системный промпт

Если агенту нужно понимать, когда вызывать инструмент, добавь правило в `SYSTEM_PROMPT` в `Application.kt` (перечисление инструментов и инструкции по вызову).

## Шаг 4. Тест

Создай `src/test/kotlin/chat/tools/<Name>ToolTest.kt` (kotlin.test; `execute` — suspend, оборачивай в `runBlocking`). Запуск: `gradle test`.

## Проверка

1. `gradle test` — тесты зелёные.
2. `gradle run > chat-server.log 2>&1` (в фоне) и `curl -s http://localhost:8080/health`.
3. Отправь сообщение, побуждающее ЛЛМ вызвать инструмент; в `chat-server.log` должна появиться строка `[TOOL] <name> invoked: ...`.

Связанный справочник: `.claude/skills/chat-server/SKILL.md` (запуск и API сервера).
