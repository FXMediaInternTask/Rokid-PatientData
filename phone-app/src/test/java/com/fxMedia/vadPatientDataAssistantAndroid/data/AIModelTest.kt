package com.fxMedia.vadPatientDataAssistantAndroid.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for AIModel sealed class hierarchy and ModelRepository.
 * Validates model IDs, provider categorization, and repository filtering.
 */
class AIModelTest {

    // ==================== Gemini Model ID Tests ====================

    @Test
    fun `Gemini 3_1 Pro Preview has correct model ID`() {
        assertThat(AIModel.Gemini.Gemini31ProPreview.modelId).isEqualTo("gemini-3.1-pro-preview")
    }

    @Test
    fun `Gemini 3 Flash Preview has correct model ID`() {
        assertThat(AIModel.Gemini.Gemini3FlashPreview.modelId).isEqualTo("gemini-3-flash-preview")
    }

    @Test
    fun `Gemini 2_5 Pro has correct model ID`() {
        assertThat(AIModel.Gemini.Gemini25Pro.modelId).isEqualTo("gemini-2.5-pro")
    }

    @Test
    fun `Gemini 2_5 Flash has correct model ID`() {
        assertThat(AIModel.Gemini.Gemini25Flash.modelId).isEqualTo("gemini-2.5-flash")
    }

    @Test
    fun `Gemini 2_5 Flash-Lite has correct model ID`() {
        assertThat(AIModel.Gemini.Gemini25FlashLite.modelId).isEqualTo("gemini-2.5-flash-lite")
    }

    @Test
    fun `Gemini Nano has correct model ID`() {
        assertThat(AIModel.Gemini.GeminiNano.modelId).isEqualTo("gemini-nano")
    }

    @Test
    fun `all Gemini models belong to GEMINI provider`() {
        AIModel.Gemini.all().forEach { model ->
            assertThat(model.provider).isEqualTo(Provider.GEMINI)
        }
    }

    @Test
    fun `Gemini preview models are marked correctly`() {
        assertThat(AIModel.Gemini.Gemini31ProPreview.isPreview).isTrue()
        assertThat(AIModel.Gemini.Gemini3FlashPreview.isPreview).isTrue()
        assertThat(AIModel.Gemini.Gemini25Pro.isPreview).isFalse()
        assertThat(AIModel.Gemini.Gemini25Flash.isPreview).isFalse()
    }

    // ==================== Claude Model ID Tests ====================

    @Test
    fun `Claude Opus 4_6 has correct model ID`() {
        assertThat(AIModel.Claude.Opus46.modelId).isEqualTo("claude-opus-4-6")
    }

    @Test
    fun `Claude Sonnet 4_6 has correct model ID`() {
        assertThat(AIModel.Claude.Sonnet46.modelId).isEqualTo("claude-sonnet-4-6")
    }

    @Test
    fun `Claude Haiku 4_5 has correct model ID`() {
        assertThat(AIModel.Claude.Haiku45.modelId).isEqualTo("claude-haiku-4-5-20251001")
    }

    @Test
    fun `all Claude models belong to CLAUDE provider`() {
        AIModel.Claude.all().forEach { model ->
            assertThat(model.provider).isEqualTo(Provider.CLAUDE)
        }
    }

    @Test
    fun `Claude 1M context support is correct for Opus and Sonnet 4_6`() {
        assertThat(AIModel.Claude.supports1MContext("claude-opus-4-6")).isTrue()
        assertThat(AIModel.Claude.supports1MContext("claude-sonnet-4-6")).isTrue()
        assertThat(AIModel.Claude.supports1MContext("claude-haiku-4-5-20251001")).isFalse()
        assertThat(AIModel.Claude.supports1MContext("claude-3-opus-20240229")).isFalse()
    }

    @Test
    fun `no Claude models are deprecated`() {
        AIModel.Claude.all().forEach { model ->
            assertThat(model.isDeprecated).isFalse()
        }
    }

    // ==================== OpenAI Model ID Tests ====================

    @Test
    fun `GPT-5_2 has correct model ID`() {
        assertThat(AIModel.OpenAI.Gpt52.modelId).isEqualTo("gpt-5.2")
    }

    @Test
    fun `GPT-5_2 Codex has correct model ID`() {
        assertThat(AIModel.OpenAI.Gpt52Codex.modelId).isEqualTo("gpt-5.2-codex")
    }

    @Test
    fun `GPT-5_1 has correct model ID`() {
        assertThat(AIModel.OpenAI.Gpt51.modelId).isEqualTo("gpt-5.1")
    }

    @Test
    fun `GPT-5 Mini has correct model ID`() {
        assertThat(AIModel.OpenAI.Gpt5Mini.modelId).isEqualTo("gpt-5-mini")
    }

    @Test
    fun `GPT-5 Nano has correct model ID`() {
        assertThat(AIModel.OpenAI.Gpt5Nano.modelId).isEqualTo("gpt-5-nano")
    }

    @Test
    fun `o3 has correct model ID`() {
        assertThat(AIModel.OpenAI.O3.modelId).isEqualTo("o3")
    }

    @Test
    fun `all OpenAI models belong to OPENAI provider`() {
        AIModel.OpenAI.all().forEach { model ->
            assertThat(model.provider).isEqualTo(Provider.OPENAI)
        }
    }

    @Test
    fun `no OpenAI models are deprecated`() {
        AIModel.OpenAI.all().forEach { model ->
            assertThat(model.isDeprecated).isFalse()
        }
    }

    // ==================== Cross-Provider Tests ====================

    @Test
    fun `allModels returns models from all three providers`() {
        val all = AIModel.allModels()
        val providers = all.map { it.provider }.toSet()
        assertThat(providers).containsExactly(Provider.GEMINI, Provider.CLAUDE, Provider.OPENAI, Provider.GROK)
    }

    @Test
    fun `all model IDs are unique`() {
        val allIds = AIModel.allModels().map { it.modelId }
        assertThat(allIds).containsNoDuplicates()
    }

    @Test
    fun `no deprecated model IDs in registry`() {
        // Verify none of the old deprecated model IDs are present
        val allIds = AIModel.allModels().map { it.modelId }.toSet()
        val deprecatedIds = listOf(
            "gemini-2.0-flash", "gemini-2.0-flash-lite",
            "gemini-1.5-pro", "gemini-1.5-flash",
            "claude-sonnet-4-5-20250929", "claude-opus-4-1-20250805",
            "claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022",
            "gpt-4o", "gpt-4o-mini", "gpt-4.1", "o4-mini"
        )
        deprecatedIds.forEach { id ->
            assertThat(allIds).doesNotContain(id)
        }
    }

    // ==================== ModelRepository Tests ====================

    @Test
    fun `getAvailableModels excludes deprecated models`() {
        val available = ModelRepository.getAvailableModels()
        available.forEach { model ->
            assertThat(model.isDeprecated).isFalse()
        }
    }

    @Test
    fun `getAvailableModels returns non-empty list`() {
        assertThat(ModelRepository.getAvailableModels()).isNotEmpty()
    }

    @Test
    fun `getModelsByProvider groups correctly`() {
        val grouped = ModelRepository.getModelsByProvider()
        assertThat(grouped).containsKey(Provider.GEMINI)
        assertThat(grouped).containsKey(Provider.CLAUDE)
        assertThat(grouped).containsKey(Provider.OPENAI)
        assertThat(grouped).containsKey(Provider.GROK)
    }

    @Test
    fun `getModelsForProvider returns only matching provider`() {
        val claudeModels = ModelRepository.getModelsForProvider(Provider.CLAUDE)
        claudeModels.forEach { model ->
            assertThat(model.provider).isEqualTo(Provider.CLAUDE)
        }
        assertThat(claudeModels).hasSize(3) // Opus 4.6, Sonnet 4.6, Haiku 4.5
    }

    @Test
    fun `findByModelId returns correct model`() {
        val model = ModelRepository.findByModelId("claude-sonnet-4-6")
        assertThat(model).isNotNull()
        assertThat(model).isInstanceOf(AIModel.Claude.Sonnet46::class.java)
        assertThat(model!!.displayName).isEqualTo("Claude Sonnet 4.6")
    }

    @Test
    fun `findByModelId returns null for unknown ID`() {
        assertThat(ModelRepository.findByModelId("nonexistent-model")).isNull()
    }

    @Test
    fun `findByModelId returns null for deprecated old IDs`() {
        assertThat(ModelRepository.findByModelId("gpt-4o")).isNull()
        assertThat(ModelRepository.findByModelId("claude-3-opus-20240229")).isNull()
    }

    @Test
    fun `getPreviewModels returns only preview models`() {
        val previews = ModelRepository.getPreviewModels()
        previews.forEach { model ->
            assertThat(model.isPreview).isTrue()
        }
        // Should include Gemini 3.1 Pro Preview, Gemini 3 Flash Preview, and Grok 4.20
        assertThat(previews.map { it.modelId }).containsAtLeast(
            "gemini-3.1-pro-preview",
            "gemini-3-flash-preview",
            "grok-4.20"
        )
    }

    @Test
    fun `getStableModels excludes preview models`() {
        val stable = ModelRepository.getStableModels()
        stable.forEach { model ->
            assertThat(model.isPreview).isFalse()
        }
    }

    // ==================== Context Window Tests ====================

    @Test
    fun `Gemini 2_5 models have 1M context window`() {
        assertThat(AIModel.Gemini.Gemini25Pro.contextWindow).isEqualTo(1_000_000L)
        assertThat(AIModel.Gemini.Gemini25Flash.contextWindow).isEqualTo(1_000_000L)
        assertThat(AIModel.Gemini.Gemini25FlashLite.contextWindow).isEqualTo(1_000_000L)
    }

    @Test
    fun `Claude models have 200K context window`() {
        assertThat(AIModel.Claude.Opus46.contextWindow).isEqualTo(200_000L)
        assertThat(AIModel.Claude.Sonnet46.contextWindow).isEqualTo(200_000L)
        assertThat(AIModel.Claude.Haiku45.contextWindow).isEqualTo(200_000L)
    }

    @Test
    fun `GPT-5_x flagship models have ~400K context window`() {
        assertThat(AIModel.OpenAI.Gpt52.contextWindow).isEqualTo(400_000L)
        assertThat(AIModel.OpenAI.Gpt51.contextWindow).isEqualTo(400_000L)
        assertThat(AIModel.OpenAI.Gpt52Codex.contextWindow).isEqualTo(400_000L)
    }

    @Test
    fun `GPT-5 mini models have ~128K context window`() {
        assertThat(AIModel.OpenAI.Gpt5Mini.contextWindow).isEqualTo(128_000L)
        assertThat(AIModel.OpenAI.Gpt5Nano.contextWindow).isEqualTo(128_000L)
    }

    @Test
    fun `o3 has 200K context window`() {
        assertThat(AIModel.OpenAI.O3.contextWindow).isEqualTo(200_000L)
    }

    // ==================== Grok Model ID Tests ====================

    @Test
    fun `Grok 4_20 has correct model ID and is preview`() {
        assertThat(AIModel.Grok.Grok420.modelId).isEqualTo("grok-4.20")
        assertThat(AIModel.Grok.Grok420.isPreview).isTrue()
    }

    @Test
    fun `Grok 4 has correct model ID and is reasoning-only`() {
        assertThat(AIModel.Grok.Grok4.modelId).isEqualTo("grok-4")
        assertThat(AIModel.Grok.Grok4.isReasoningOnly).isTrue()
    }

    @Test
    fun `Grok 4_1 Fast has correct model ID and 2M context`() {
        assertThat(AIModel.Grok.Grok41Fast.modelId).isEqualTo("grok-4.1-fast")
        assertThat(AIModel.Grok.Grok41Fast.contextWindow).isEqualTo(2_000_000L)
        assertThat(AIModel.Grok.Grok41Fast.isReasoningOnly).isFalse()
    }

    @Test
    fun `Grok 3 has correct model ID`() {
        assertThat(AIModel.Grok.Grok3.modelId).isEqualTo("grok-3")
    }

    @Test
    fun `Grok 3 Mini has correct model ID`() {
        assertThat(AIModel.Grok.Grok3Mini.modelId).isEqualTo("grok-3-mini")
    }

    @Test
    fun `Grok Image has correct model ID`() {
        assertThat(AIModel.Grok.GrokImage.modelId).isEqualTo("grok-2-image-1212")
    }

    @Test
    fun `all Grok models belong to GROK provider`() {
        AIModel.Grok.all().forEach { model ->
            assertThat(model.provider).isEqualTo(Provider.GROK)
        }
    }

    @Test
    fun `Grok isReasoningOnly identifies grok-4 correctly`() {
        assertThat(AIModel.Grok.isReasoningOnly("grok-4")).isTrue()
        assertThat(AIModel.Grok.isReasoningOnly("grok-4.1-fast")).isFalse()
        assertThat(AIModel.Grok.isReasoningOnly("grok-3")).isFalse()
        assertThat(AIModel.Grok.isReasoningOnly("grok-3-mini")).isFalse()
    }

    @Test
    fun `Grok 4 has 256K context window`() {
        assertThat(AIModel.Grok.Grok4.contextWindow).isEqualTo(256_000L)
    }

    @Test
    fun `Grok 3 models have 128K context window`() {
        assertThat(AIModel.Grok.Grok3.contextWindow).isEqualTo(128_000L)
        assertThat(AIModel.Grok.Grok3Mini.contextWindow).isEqualTo(128_000L)
    }
}
