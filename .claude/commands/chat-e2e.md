---
description: Сквозная проверка: запуск сервера, сообщение ЛЛМ, сохранение истории в файл
---

Проведи сквозную проверку всего приложения (E2E):

1. **Подготовка.** Если `/health` не отвечает — запусти сервер, как в `/chat-start`. Если `DEEPSEEK_API_KEY` не задан — остановись и объясни: LLM-часть проверить нельзя (`/chat` вернёт 503), можно проверить только прямые маршруты.
2. **Сообщение ЛЛМ с просьбой сохранить историю** (уникальный conversationId, например `e2e-<timestamp>`):
   ```bash
   curl -s -X POST http://localhost:8080/chat \
     -H "Content-Type: application/json" \
     -d '{"conversationId":"e2e-1","message":"Привет! Сохрани историю этого чата в файл e2e-1.txt"}'
   ```
3. **Проверь вызов инструмента:** в `chat-server.log` должна быть строка `[TOOL] save_chat_history invoked:` и затем `[TOOL] save_chat_history saved N messages to ...\e2e-1.txt`.
4. **Проверь файл:** он должен существовать в `${CHAT_HISTORY_DIR:-chat-history}` и содержать сообщения диалога (заголовок `=== Chat history ===` и блоки `[user]`/`[assistant]`).
5. **Проверь прямую запись без ЛЛМ:** `curl -s -X POST "http://localhost:8080/chat/e2e-1/save?fileName=e2e-1-direct.txt"` — файл должен появиться.
6. **Проверь просмотр истории:** `curl -s http://localhost:8080/chat/e2e-1`.
7. **Итог:** таблица PASS/FAIL по шагам; при FAIL — краткая причина из логов.
