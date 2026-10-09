package chat.routes

import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.ktor.aiAgent
import ai.koog.prompt.llm.LLModel
import chat.storage.ChatHistoryStore
import chat.tools.ChatMessage
import chat.tools.SaveChatHistoryArgs
import chat.tools.SaveChatHistoryTool
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Serializable
public data class ChatRequest(
    val conversationId: String = "default",
    val message: String,
)

@Serializable
public data class ChatResponse(
    val conversationId: String,
    val reply: String,
    val history: List<ChatMessage>,
)

/**
 * Ktor routes:
 *  - POST /chat                      — send a message to the LLM (Koog agent with the save tool)
 *  - GET  /chat/{conversationId}     — view the in-memory chat history
 *  - POST /chat/{conversationId}/save — save the history directly (no LLM involved)
 */
public fun Route.chatRoutes(
    historyStore: ChatHistoryStore,
    saveChatHistoryTool: SaveChatHistoryTool,
    model: LLModel,
    llmEnabled: Boolean,
) {
    val logger: Logger = LoggerFactory.getLogger("CHAT")

    route("/chat") {

        post {
            val request = call.receive<ChatRequest>()
            val conversationId = request.conversationId.ifBlank { "default" }
            if (request.message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message must not be blank"))
                return@post
            }
            if (!llmEnabled) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "LLM is not configured: set the DEEPSEEK_API_KEY environment variable"),
                )
                return@post
            }

            // Add the user message to the history and pass the whole conversation to the LLM
            // so that the LLM can pass it to the save_chat_history tool when asked.
            historyStore.append(conversationId, ChatMessage("user", request.message))
            val history = historyStore.get(conversationId)
            val llmInput = history.joinToString("\n\n") { "${it.role}: ${it.content}" }

            logger.info(
                "[LLM] >>> Sending to LLM (conversationId={}, {} messages):\n{}",
                conversationId, history.size, llmInput,
            )

            // The post {} handler receiver is a RoutingContext, so the Koog aiAgent
            // extension is available unqualified.
            val reply = aiAgent(
                strategy = reActStrategy(),
                model = model,
                input = llmInput,
            )

            logger.info(
                "[LLM] <<< Received from LLM (conversationId={}):\n{}",
                conversationId, reply,
            )

            historyStore.append(conversationId, ChatMessage("assistant", reply))
            call.respond(HttpStatusCode.OK, ChatResponse(conversationId, reply, historyStore.get(conversationId)))
        }

        get("/{conversationId}") {
            val conversationId = call.parameters["conversationId"] ?: "default"
            val history = historyStore.get(conversationId)
            if (history.isEmpty()) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no chat history for conversationId=$conversationId"))
                return@get
            }
            call.respond(mapOf("conversationId" to conversationId, "history" to history))
        }

        post("/{conversationId}/save") {
            val conversationId = call.parameters["conversationId"] ?: "default"
            val fileName = call.request.queryParameters["fileName"] ?: "chat-$conversationId.txt"
            val history = historyStore.get(conversationId)
            if (history.isEmpty()) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no chat history for conversationId=$conversationId"))
                return@post
            }
            // Direct (non-LLM) invocation of the same tool the LLM uses.
            val result = saveChatHistoryTool.execute(SaveChatHistoryArgs(fileName, history))
            call.respond(HttpStatusCode.OK, mapOf("result" to result))
        }
    }
}
