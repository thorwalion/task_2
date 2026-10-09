package chat.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A decorator over any [LLMClient] that logs every request sent to the LLM and every
 * response received from it to the console (via the Logback console appender).
 *
 * Because the ReAct agent strategy performs several internal LLM calls per user request
 * (reasoning, tool planning, post-tool continuation), this decorator guarantees that
 * the console shows the full LLM traffic, not just the final answer.
 */
public class LoggingLLMClient(
    private val delegate: LLMClient,
) : LLMClient() {

    private val logger: Logger = LoggerFactory.getLogger("LLM")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        logRequest(prompt, model, tools)
        val start = System.currentTimeMillis()
        return try {
            val response = delegate.execute(prompt, model, tools)
            logResponse(model, response, System.currentTimeMillis() - start)
            response
        } catch (e: Throwable) {
            logger.error(
                "[LLM] request FAILED after {} ms (model={}): {}",
                System.currentTimeMillis() - start,
                model.id,
                e.message,
            )
            throw e
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        logRequest(prompt, model, tools)
        val start = System.currentTimeMillis()
        val text = StringBuilder()
        return delegate.executeStreaming(prompt, model, tools)
            .onEach { frame ->
                (frame as? StreamFrame.TextDelta)?.let { text.append(it.text) }
            }
            .onCompletion { cause ->
                val elapsed = System.currentTimeMillis() - start
                if (cause != null) {
                    logger.error("[LLM] streaming request FAILED after {} ms: {}", elapsed, cause.message)
                } else {
                    logger.info(
                        "[LLM] <<< streaming response (model={}, {} ms):\n{}",
                        model.id, elapsed, text,
                    )
                }
            }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Assistant> {
        logRequest(prompt, model, tools)
        val choices = delegate.executeMultipleChoices(prompt, model, tools)
        choices.forEach { logResponse(model, it, 0) }
        return choices
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.info("[LLM] >>> moderation request (model={}):\n{}", model.id, prompt.format())
        return delegate.moderate(prompt, model)
    }

    override suspend fun models(): List<LLModel> = delegate.models()

    override suspend fun embed(text: String, model: LLModel): List<Double> =
        delegate.embed(text, model)

    override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> =
        delegate.embed(inputs, model)

    override fun llmProvider(): LLMProvider = delegate.llmProvider()

    override fun getStandardJsonSchemaGenerator() = delegate.getStandardJsonSchemaGenerator()

    override fun getBasicJsonSchemaGenerator() = delegate.getBasicJsonSchemaGenerator()

    override val clientName: String = "LoggingLLMClient(${delegate.clientName})"

    override fun close() = delegate.close()

    // --- formatting helpers -------------------------------------------------

    private fun logRequest(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val toolNames = tools.joinToString { it.name }.ifEmpty { "none" }
        logger.info(
            "[LLM] >>> request (model={}, tools=[{}]):\n{}",
            model.id, toolNames, prompt.format(),
        )
    }

    private fun logResponse(model: LLModel, response: Message.Assistant, elapsedMs: Long) {
        logger.info(
            "[LLM] <<< response (model={}, {} ms, finishReason={}):\n{}",
            model.id, elapsedMs, response.finishReason, response.format(),
        )
    }

    private fun Prompt.format(): String = buildString {
        if (messages.isEmpty()) appendLine("(empty prompt)")
        messages.forEach { appendLine(it.format()) }
    }.trimEnd()

    private fun Message.format(): String = buildString {
        appendLine("[${role.name.lowercase()}]")
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> appendLine(part.text)
                else -> appendLine("<${part::class.simpleName}>: $part")
            }
        }
    }.trimEnd()
}
