package com.fxMedia.patientDataAssistant.data

/**
 * AI Provider type for model categorization
 */
enum class Provider {
    GEMINI, CLAUDE, OPENAI, GROK
}

/**
 * Type-safe AI Model registry with provider-specific subclasses.
 * Each model entry includes metadata for display, filtering, and runtime decisions.
 *
 * @property modelId The exact string ID used in API requests
 * @property displayName Human-readable name for UI display
 * @property contextWindow Approximate maximum context window in tokens
 * @property provider The AI provider this model belongs to
 * @property isPreview Whether this model is in preview/experimental state
 * @property isDeprecated Whether this model is deprecated and should not be used
 */
sealed class AIModel(
    val modelId: String,
    val displayName: String,
    val contextWindow: Long,
    val provider: Provider,
    val isPreview: Boolean = false,
    val isDeprecated: Boolean = false
) {
    // ==================== Google Gemini ====================

    sealed class Gemini(
        modelId: String,
        displayName: String,
        contextWindow: Long,
        isPreview: Boolean = false,
        isDeprecated: Boolean = false
    ) : AIModel(modelId, displayName, contextWindow, Provider.GEMINI, isPreview, isDeprecated) {

        data object Gemini31ProPreview : Gemini(
            modelId = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro (Preview)",
            contextWindow = -1L, // TBD
            isPreview = true
        )

        data object Gemini3FlashPreview : Gemini(
            modelId = "gemini-3-flash-preview",
            displayName = "Gemini 3 Flash (Preview)",
            contextWindow = -1L, // TBD
            isPreview = true
        )

        data object Gemini25Pro : Gemini(
            modelId = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            contextWindow = 1_000_000L
        )

        data object Gemini25Flash : Gemini(
            modelId = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            contextWindow = 1_000_000L
        )

        data object Gemini25FlashLite : Gemini(
            modelId = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash-Lite",
            contextWindow = 1_000_000L
        )

        data object GeminiNano : Gemini(
            modelId = "gemini-nano",
            displayName = "Gemini Nano (On-Device)",
            contextWindow = 0L // Device-limited
        )

        companion object {
            fun all(): List<Gemini> = listOf(
                Gemini31ProPreview,
                Gemini3FlashPreview,
                Gemini25Pro,
                Gemini25Flash,
                Gemini25FlashLite,
                GeminiNano
            )
        }
    }

    // ==================== Anthropic Claude ====================

    sealed class Claude(
        modelId: String,
        displayName: String,
        contextWindow: Long,
        isPreview: Boolean = false,
        isDeprecated: Boolean = false
    ) : AIModel(modelId, displayName, contextWindow, Provider.CLAUDE, isPreview, isDeprecated) {

        data object Opus46 : Claude(
            modelId = "claude-opus-4-6",
            displayName = "Claude Opus 4.6",
            contextWindow = 200_000L // 1M via beta header
        )

        data object Sonnet46 : Claude(
            modelId = "claude-sonnet-4-6",
            displayName = "Claude Sonnet 4.6",
            contextWindow = 200_000L // 1M via beta header
        )

        data object Haiku45 : Claude(
            modelId = "claude-haiku-4-5-20251001",
            displayName = "Claude Haiku 4.5",
            contextWindow = 200_000L
        )

        companion object {
            fun all(): List<Claude> = listOf(Opus46, Sonnet46, Haiku45)

            /**
             * Whether this model supports 1M context via beta header.
             */
            fun supports1MContext(modelId: String): Boolean =
                modelId == Opus46.modelId || modelId == Sonnet46.modelId
        }
    }

    // ==================== OpenAI ====================

    sealed class OpenAI(
        modelId: String,
        displayName: String,
        contextWindow: Long,
        isPreview: Boolean = false,
        isDeprecated: Boolean = false
    ) : AIModel(modelId, displayName, contextWindow, Provider.OPENAI, isPreview, isDeprecated) {

        data object Gpt52 : OpenAI(
            modelId = "gpt-5.2",
            displayName = "GPT-5.2",
            contextWindow = 400_000L
        )

        data object Gpt52Codex : OpenAI(
            modelId = "gpt-5.2-codex",
            displayName = "GPT-5.2 Codex",
            contextWindow = 400_000L
        )

        data object Gpt51 : OpenAI(
            modelId = "gpt-5.1",
            displayName = "GPT-5.1",
            contextWindow = 400_000L
        )

        data object Gpt5Mini : OpenAI(
            modelId = "gpt-5-mini",
            displayName = "GPT-5 Mini",
            contextWindow = 128_000L
        )

        data object Gpt5Nano : OpenAI(
            modelId = "gpt-5-nano",
            displayName = "GPT-5 Nano",
            contextWindow = 128_000L
        )

        data object O3 : OpenAI(
            modelId = "o3",
            displayName = "o3 (Reasoning)",
            contextWindow = 200_000L
        )

        companion object {
            fun all(): List<OpenAI> = listOf(Gpt52, Gpt52Codex, Gpt51, Gpt5Mini, Gpt5Nano, O3)
        }
    }

    // ==================== xAI Grok ====================

    sealed class Grok(
        modelId: String,
        displayName: String,
        contextWindow: Long,
        isPreview: Boolean = false,
        isDeprecated: Boolean = false,
        val isReasoningOnly: Boolean = false
    ) : AIModel(modelId, displayName, contextWindow, Provider.GROK, isPreview, isDeprecated) {

        data object Grok420 : Grok(
            modelId = "grok-4.20",
            displayName = "Grok 4.20 (Early Access)",
            contextWindow = -1L, // TBD
            isPreview = true
        )

        data object Grok4 : Grok(
            modelId = "grok-4",
            displayName = "Grok 4 (Reasoning)",
            contextWindow = 256_000L,
            isReasoningOnly = true
        )

        data object Grok41Fast : Grok(
            modelId = "grok-4.1-fast",
            displayName = "Grok 4.1 Fast",
            contextWindow = 2_000_000L
        )

        data object Grok3 : Grok(
            modelId = "grok-3",
            displayName = "Grok 3",
            contextWindow = 128_000L
        )

        data object Grok3Mini : Grok(
            modelId = "grok-3-mini",
            displayName = "Grok 3 Mini",
            contextWindow = 128_000L
        )

        data object GrokImage : Grok(
            modelId = "grok-2-image-1212",
            displayName = "Grok Imagine (Image Gen)",
            contextWindow = 0L // N/A — image generation
        )

        companion object {
            fun all(): List<Grok> = listOf(Grok420, Grok4, Grok41Fast, Grok3, Grok3Mini, GrokImage)

            /**
             * Whether this model is a pure reasoning model that
             * rejects penalty, stop, and reasoning_effort params.
             */
            fun isReasoningOnly(modelId: String): Boolean =
                all().find { it.modelId == modelId }?.isReasoningOnly == true
        }
    }

    companion object {
        /**
         * Returns all registered models across all providers.
         */
        fun allModels(): List<AIModel> = Gemini.all() + Claude.all() + OpenAI.all() + Grok.all()
    }
}

/**
 * Singleton repository for querying available AI models.
 * Filters out deprecated models by default.
 */
object ModelRepository {

    /**
     * Returns all non-deprecated models.
     */
    fun getAvailableModels(): List<AIModel> =
        AIModel.allModels().filter { !it.isDeprecated }

    /**
     * Returns available models grouped by provider.
     */
    fun getModelsByProvider(): Map<Provider, List<AIModel>> =
        getAvailableModels().groupBy { it.provider }

    /**
     * Returns available models for a specific provider.
     */
    fun getModelsForProvider(provider: Provider): List<AIModel> =
        getAvailableModels().filter { it.provider == provider }

    /**
     * Find a model by its exact model ID string.
     */
    fun findByModelId(modelId: String): AIModel? =
        AIModel.allModels().find { it.modelId == modelId }

    /**
     * Returns only preview models.
     */
    fun getPreviewModels(): List<AIModel> =
        getAvailableModels().filter { it.isPreview }

    /**
     * Returns only stable (non-preview, non-deprecated) models.
     */
    fun getStableModels(): List<AIModel> =
        getAvailableModels().filter { !it.isPreview }
}
