---
description: Сохранить историю чата в файл напрямую, без ЛЛМ (POST /chat/{id}/save)
argument-hint: [conversationId] [fileName]
---

Сохрани историю чата в текстовый файл напрямую (без участия ЛЛМ):

1. `conversationId` — первое слово `$ARGUMENTS` (по умолчанию `default`), `fileName` — второе (по умолчанию `chat-<conversationId>.txt`).
2. Выполни:
   ```bash
   curl -s -X POST "http://localhost:${PORT:-8080}/chat/<conversationId>/save?fileName=<fileName>"
   ```
3. Проверь, что файл появился: каталог `${CHAT_HISTORY_DIR:-chat-history}` в корне проекта. Покажи первые строки файла.
4. Если 404 — история пуста: сначала отправь сообщения (`/chat-send`).
