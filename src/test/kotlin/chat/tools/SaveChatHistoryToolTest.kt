package chat.tools

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveChatHistoryToolTest {

    @Test
    fun `saves chat history to a text file`() {
        val dir = Files.createTempDirectory("chat-history-test")
        val tool = SaveChatHistoryTool(dir)

        val result = runBlocking {
            tool.execute(
                SaveChatHistoryArgs(
                    fileName = "conversation.txt",
                    messages = listOf(
                        ChatMessage("user", "Привет!"),
                        ChatMessage("assistant", "Здравствуйте!"),
                        ChatMessage("user", "Сохрани историю чата."),
                    ),
                ),
            )
        }

        val file = dir.resolve("conversation.txt")
        assertTrue(Files.exists(file), "tool result file must exist: $file")
        val content = Files.readString(file)
        assertContains(content, "=== Chat history ===")
        assertContains(content, "[user]")
        assertContains(content, "Привет!")
        assertContains(content, "[assistant]")
        assertContains(content, "Здравствуйте!")
        assertContains(result, file.toAbsolutePath().toString())
    }

    @Test
    fun `neutralizes file names escaping the history directory`() {
        val dir = Files.createTempDirectory("chat-history-test").toAbsolutePath().normalize()
        val tool = SaveChatHistoryTool(dir)

        val result = runBlocking {
            tool.execute(
                SaveChatHistoryArgs(
                    fileName = "../outside.txt",
                    messages = listOf(ChatMessage("user", "hello")),
                ),
            )
        }

        // ".." and separators are stripped, so the traversal attempt is saved
        // as a harmless file inside the history directory.
        val saved = dir.resolve("__outside.txt")
        assertTrue(Files.exists(saved), "expected sanitized file $saved, got: $result")
        assertTrue(saved.startsWith(dir), "file must stay inside the history directory")
    }

    @Test
    fun `rejects empty messages`() {
        val dir = Files.createTempDirectory("chat-history-test")
        val tool = SaveChatHistoryTool(dir)

        assertFailsWith<IllegalArgumentException> {
            runBlocking { tool.execute(SaveChatHistoryArgs("x.txt", emptyList())) }
        }
    }

    @Test
    fun `rejects blank file name`() {
        val dir = Files.createTempDirectory("chat-history-test")
        val tool = SaveChatHistoryTool(dir)

        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                tool.execute(
                    SaveChatHistoryArgs(
                        fileName = "   ",
                        messages = listOf(ChatMessage("user", "hello")),
                    ),
                )
            }
        }
    }

    @Test
    fun `sanitizes unsafe characters in the file name`() {
        val dir = Files.createTempDirectory("chat-history-test")
        val tool = SaveChatHistoryTool(dir)

        val result = runBlocking {
            tool.execute(
                SaveChatHistoryArgs(
                    fileName = "my:weird/name?.txt",
                    messages = listOf(ChatMessage("user", "hello")),
                ),
            )
        }

        assertTrue(Files.exists(dir.resolve("my_weird_name_.txt")), "result: $result")
        assertTrue(dir.toFile().listFiles()!!.none { it.name.contains(':') || it.name.contains('/') })
    }
}
