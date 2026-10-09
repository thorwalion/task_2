package chat.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A single chat message. [role] is "user" or "assistant".
 */
@Serializable
public data class ChatMessage(
    val role: String,
    val content: String,
)

/**
 * Arguments of the [SaveChatHistoryTool] as they are generated (as a JSON schema)
 * for the LLM.
 */
@Serializable
public data class SaveChatHistoryArgs(
    /** Name of the text file to write, e.g. "chat-with-user.txt". */
    val fileName: String,
    /** The full conversation in chronological order. */
    val messages: List<ChatMessage>,
)

/**
 * A Koog tool that the LLM can call to save a chat history to a text file.
 *
 * The tool receives the full conversation (list of user/assistant messages) and a file
 * name from the LLM, writes a human-readable text file under [historyDir], and returns
 * a short confirmation string that is sent back to the LLM.
 */
public class SaveChatHistoryTool(
    private val historyDir: Path,
) : SimpleTool<SaveChatHistoryArgs>(
    argsType = typeToken<SaveChatHistoryArgs>(),
    name = NAME,
    description = DESCRIPTION,
) {

    private val logger: Logger = LoggerFactory.getLogger("TOOL")

    override suspend fun execute(args: SaveChatHistoryArgs): String {
        logger.info(
            "[TOOL] {} invoked: fileName={}, messages={}",
            NAME, args.fileName, args.messages.size,
        )

        require(args.fileName.isNotBlank()) { "fileName must not be blank" }
        require(args.messages.isNotEmpty()) { "messages must not be empty" }

        val target = historyDir.resolve(sanitizeFileName(args.fileName)).normalize()
        require(target.startsWith(historyDir)) {
            "fileName must resolve inside the history directory: $historyDir"
        }

        Files.createDirectories(historyDir)

        val content = buildString {
            appendLine("=== Chat history ===")
            appendLine("Saved at: ${SAVED_AT_FORMATTER.format(Instant.now())}")
            appendLine("Messages: ${args.messages.size}")
            appendLine()
            args.messages.forEach { message ->
                appendLine("[${message.role}]")
                appendLine(message.content)
                appendLine()
            }
        }

        Files.writeString(
            target, content,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        )

        logger.info(
            "[TOOL] {} saved {} messages to {}",
            NAME, args.messages.size, target.toAbsolutePath(),
        )
        return "Chat history (${args.messages.size} messages) saved to ${target.toAbsolutePath()}"
    }

    /** Strips path separators and other filesystem-hostile characters from the file name. */
    private fun sanitizeFileName(fileName: String): String =
        fileName.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").replace("..", "_")

    public companion object {
        public const val NAME: String = "save_chat_history"

        public const val DESCRIPTION: String =
            "Saves the complete chat history to a text file on the server. " +
                "Use this tool when the user asks to save, export, or write down the conversation. " +
                "Arguments: fileName - the name of the text file to create (for example chat-history.txt); " +
                "messages - the full list of conversation messages in chronological order, " +
                "each with role \"user\" or \"assistant\" and the message content. " +
                "Include every message of the current conversation, including the user's latest request."

        private val SAVED_AT_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}
