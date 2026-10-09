package chat.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReasoningFallbackLLMClientTest {

    private val model = DeepSeekModels.DeepSeekV4Pro
    // The fake ignores the prompt, so the shared Empty instance is enough.
    private val prompt = Prompt.Empty

    @Test
    fun `returns a response with text unchanged, keeping all parts`() {
        val raw = assistant(
            MessagePart.Text("Привет!"),
            MessagePart.Reasoning(listOf("chain of thought")),
            MessagePart.Tool.Call("t1", "save_chat_history", "{}"),
        )
        val client = ReasoningFallbackLLMClient(FakeLLMClient(raw))

        val result = runBlocking { client.execute(prompt, model, emptyList()) }

        assertSame(raw, result, "responses with text must pass through untouched")
        assertEquals(3, result.parts.size, "all parts must be kept: ${result.parts}")
    }

    @Test
    fun `returns a text-only response unchanged`() {
        val raw = assistant(MessagePart.Text("ok"))
        val client = ReasoningFallbackLLMClient(FakeLLMClient(raw))

        val result = runBlocking { client.execute(prompt, model, emptyList()) }

        assertSame(raw, result, "no copy should be made when the response is usable")
    }

    @Test
    fun `retries once when the model answers with reasoning only`() {
        val reasoningOnly = assistant(MessagePart.Reasoning(listOf("thinking...")))
        val fake = FakeLLMClient(reasoningOnly, assistant(MessagePart.Text("second attempt")))
        val client = ReasoningFallbackLLMClient(fake)

        val result = runBlocking { client.execute(prompt, model, emptyList()) }

        assertEquals("second attempt", result.textContent())
        assertEquals(2, fake.calls, "the request must be retried exactly once")
    }

    @Test
    fun `appends an empty text part when both attempts yield only reasoning`() {
        val reasoningOnly = assistant(MessagePart.Reasoning(listOf("thinking...")))
        val fake = FakeLLMClient(reasoningOnly, reasoningOnly)
        val client = ReasoningFallbackLLMClient(fake)

        val result = runBlocking { client.execute(prompt, model, emptyList()) }

        assertEquals(2, fake.calls)
        // Reasoning parts must survive: the DeepSeek API requires reasoning_content to be
        // passed back in subsequent requests.
        assertTrue(result.parts.any { it is MessagePart.Reasoning }, "reasoning must be kept: ${result.parts}")
        val text = assertIs<MessagePart.Text>(result.parts.last())
        assertEquals("", text.text, "the fallback text part must be empty")
    }

    private fun assistant(vararg parts: MessagePart.ResponsePart): Message.Assistant =
        Message.Assistant(
            parts.toList(),
            ResponseMetaInfo(
                timestamp = Instant.fromEpochMilliseconds(0L),
                totalTokensCount = null,
                inputTokensCount = null,
                outputTokensCount = null,
                modelId = null,
                metadata = null,
            ),
        )

    /**
     * Returns the given responses one by one from [execute]; the last one is repeated.
     * All other [LLMClient] members are not used by the decorator under test.
     */
    private class FakeLLMClient(
        private vararg val responses: Message.Assistant,
    ) : LLMClient() {

        var calls = 0
            private set

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Message.Assistant {
            val index = minOf(calls, responses.lastIndex)
            calls++
            return responses[index]
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = error("not used in tests")

        override suspend fun executeMultipleChoices(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Assistant> = error("not used in tests")

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            error("not used in tests")

        override suspend fun models(): List<LLModel> = error("not used in tests")

        override suspend fun embed(text: String, model: LLModel): List<Double> =
            error("not used in tests")

        override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> =
            error("not used in tests")

        override fun llmProvider(): LLMProvider = error("not used in tests")

        override fun getStandardJsonSchemaGenerator() = error("not used in tests")

        override fun getBasicJsonSchemaGenerator() = error("not used in tests")

        override val clientName: String = "FakeLLMClient"

        override fun close() = Unit
    }
}
