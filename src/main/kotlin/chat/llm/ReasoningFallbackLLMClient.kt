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

/**
 * A decorator over any [LLMClient] that guards the Koog agent graph against reasoning-only
 * assistant responses.
 *
 * `deepseek-v4-pro` runs in DeepSeek's "thinking mode": every response carries a
 * [MessagePart.Reasoning] part (chain of thought), and the DeepSeek API requires this
 * reasoning_content to be passed back verbatim in subsequent requests (the API answers 400
 * otherwise). Reasoning parts therefore cannot simply be dropped. However, when the model
 * answers with reasoning only — no text, no tool call — Koog's ReAct graph has no matching
 * edge and the agent gets stuck (AIAgentStuckInTheNodeException -> HTTP 500).
 *
 * This decorator returns every response byte-identical when it contains text or a tool call.
 * Only when a response contains nothing but reasoning does it retry the request once (same
 * prompt, same tools); if the retry also yields nothing usable, an empty [MessagePart.Text]
 * part is appended (reasoning parts stay in place) so the agent graph has an edge to finish
 * on. The chat reply is then empty.
 */
public class ReasoningFallbackLLMClient(
    private val delegate: LLMClient,
) : LLMClient() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        var response = delegate.execute(prompt, model, tools)
        if (response.hasNoActionableParts()) {
            // The model produced only its chain of thought and no answer — ask it once more.
            response = delegate.execute(prompt, model, tools)
            if (response.hasNoActionableParts()) {
                // Append an empty text part so the agent graph has a valid edge to finish on
                // instead of getting stuck. Reasoning parts stay: the DeepSeek API requires
                // reasoning_content to be passed back in subsequent requests.
                response = response.copy(parts = response.parts + MessagePart.Text(""))
            }
        }
        return response
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Assistant> = delegate.executeMultipleChoices(prompt, model, tools)

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = delegate.executeStreaming(prompt, model, tools)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        delegate.moderate(prompt, model)

    override suspend fun models(): List<LLModel> = delegate.models()

    override suspend fun embed(text: String, model: LLModel): List<Double> =
        delegate.embed(text, model)

    override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> =
        delegate.embed(inputs, model)

    override fun llmProvider(): LLMProvider = delegate.llmProvider()

    override fun getStandardJsonSchemaGenerator() = delegate.getStandardJsonSchemaGenerator()

    override fun getBasicJsonSchemaGenerator() = delegate.getBasicJsonSchemaGenerator()

    override val clientName: String = "ReasoningFallbackLLMClient(${delegate.clientName})"

    override fun close() = delegate.close()

    private fun Message.Assistant.hasNoActionableParts(): Boolean =
        parts.none { it is MessagePart.Text || it is MessagePart.Tool.Call }
}
