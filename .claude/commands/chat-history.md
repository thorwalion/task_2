---
description: Показать историю чата из памяти сервера (GET /chat/{conversationId})
argument-hint: [conversationId]
---

Покажи историю чата из памяти сервера:

1. `conversationId` — `$ARGUMENTS`; если пусто — `default`.
2. Выполни: `curl -s http://localhost:${PORT:-8080}/chat/<conversationId>`
3. Выведи историю в читаемом виде: список сообщений `user`/`assistant` по порядку, с содержимым.
4. Если 404 — история пуста или потеряна (она хранится только в памяти и пропадает при перезапуске сервера).
5. Если сервер не отвечает — предложи `/chat-start`.
