package chat

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.ktor.Koog
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLMProvider
import chat.llm.LoggingLLMClient
import chat.llm.ReasoningFallbackLLMClient
import chat.routes.chatRoutes
import chat.storage.ChatHistoryStore
import chat.tools.SaveChatHistoryTool
import io.ktor.client.HttpClient
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.file.Paths

@Serializable
private data class HealthResponse(val status: String, val llmEnabled: Boolean)

private const val SYSTEM_PROMPT: String = """
    You are a helpful chat assistant running on a Ktor server (Koog framework).
    The user message contains the full conversation so far (user and assistant messages).

    You have access to one tool:
    - save_chat_history: saves the chat history to a text file on the server.

    Rules:
    - Answer the user normally and concisely.
    - When the user asks to save, export, or write the chat history to a file, call the
      save_chat_history tool with:
      * fileName: a safe file name, e.g. "chat-history.txt";
      * messages: the complete list of this conversation's messages in chronological order,
        including the user's latest request. Use roles "user" and "assistant".
    - After the tool confirms the save, tell the user where the file was written.
    - Do not call the tool unless the user asks to save the conversation.
"""

public fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

public fun Application.module() {
    val logger = LoggerFactory.getLogger("SERVER")
    val llmLogger = LoggerFactory.getLogger("LLM")

    val historyDir = Paths.get(System.getenv("CHAT_HISTORY_DIR") ?: "chat-history").toAbsolutePath().normalize()
    val saveChatHistoryTool = SaveChatHistoryTool(historyDir)

    val deepSeekApiKey = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
    val llmEnabled = deepSeekApiKey != null
    if (!llmEnabled) {
        logger.warn(
            "DEEPSEEK_API_KEY is not set: LLM calls are disabled. " +
                "The history endpoints and the save_chat_history tool still work.",
        )
    } else {
        logger.info("Chat history files will be saved to {}", historyDir)
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/chat") }
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    // Koog framework: LLM connections + agent configuration (prompt, tools, limits).
    install(Koog) {
        llm {
            if (deepSeekApiKey != null) {
                val baseUrl = System.getenv("DEEPSEEK_BASE_URL")
                val settings = DeepSeekClientSettings().let {
                    if (baseUrl.isNullOrBlank()) it
                    else DeepSeekClientSettings(baseUrl = baseUrl)
                }
                val realClient = DeepSeekLLMClient(
                    apiKey = deepSeekApiKey,
                    settings = settings,
                    httpClientFactory = KtorKoogHttpClient.Factory(HttpClient()),
                )
                // Wrap the real client: every LLM request and response is logged to the console
                // (raw, including reasoning). The outermost decorator guards the ReAct graph
                // against reasoning-only responses (no matching edge in the graph -> agent
                // stuck, HTTP 500) by retrying once and appending an empty text part as a
                // last resort. Reasoning parts themselves are kept: the DeepSeek API requires
                // reasoning_content to be passed back in subsequent requests (400 otherwise).
                addClient(LLMProvider.DeepSeek, ReasoningFallbackLLMClient(LoggingLLMClient(realClient)))
                llmLogger.info("DeepSeek LLM client registered (baseUrl={})", settings.baseUrl)
            }
        }
        agentConfig {
            prompt {
                system(SYSTEM_PROMPT)
            }
            maxAgentIterations = 10
            registerTools {
                tool(saveChatHistoryTool)
            }
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse("ok", llmEnabled))
        }
        chatRoutes(
            historyStore = ChatHistoryStore(),
            saveChatHistoryTool = saveChatHistoryTool,
            model = DeepSeekModels.DeepSeekV4Pro,
            llmEnabled = llmEnabled,
        )
    }
}
