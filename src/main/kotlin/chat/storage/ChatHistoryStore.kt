package chat.storage

import chat.tools.ChatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple thread-safe in-memory chat history, one list of messages per conversation id.
 * Used to keep the conversation context between HTTP calls and to let the user save the
 * exact history via the tool.
 */
public class ChatHistoryStore {

    private val histories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    public fun append(conversationId: String, message: ChatMessage): List<ChatMessage> {
        val history = histories.computeIfAbsent(conversationId) { mutableListOf() }
        synchronized(history) { history.add(message) }
        return history.toList()
    }

    public fun get(conversationId: String): List<ChatMessage> =
        histories[conversationId]?.toList() ?: emptyList()
}
