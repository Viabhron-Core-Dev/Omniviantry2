package com.example.engine.db

object ProviderPrepopulator {
    val defaultProviders = listOf(
        ApiProviderEntity(
            id = "google_ai_studio",
            name = "Google AI Studio",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            loginUrl = "https://aistudio.google.com/app/apikey",
            isFreeTierAvailable = true,
            description = "Access to Gemini models with a generous free tier."
        ),
        ApiProviderEntity(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            loginUrl = "https://platform.openai.com/api-keys",
            isFreeTierAvailable = false,
            description = "Access to GPT-4o, GPT-3.5, and more."
        ),
        ApiProviderEntity(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            loginUrl = "https://console.anthropic.com/settings/keys",
            isFreeTierAvailable = false,
            description = "Access to Claude 3 models."
        ),
        ApiProviderEntity(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            loginUrl = "https://openrouter.ai/keys",
            isFreeTierAvailable = true,
            description = "Aggregator for many models with free tier options."
        ),
        ApiProviderEntity(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            loginUrl = "https://console.groq.com/keys",
            isFreeTierAvailable = true,
            description = "Ultra-fast inference for Llama, Mixtral, and Gemma models."
        ),
        ApiProviderEntity(
            id = "together_ai",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            loginUrl = "https://api.together.xyz/settings/api-keys",
            isFreeTierAvailable = true,
            description = "Open source models with fast inference."
        ),
        ApiProviderEntity(
            id = "github_models",
            name = "GitHub Models",
            baseUrl = "https://models.inference.ai.azure.com",
            loginUrl = "https://github.com/settings/tokens/new?description=OmniRoot&scopes=",
            isFreeTierAvailable = true,
            description = "Free access to GPT-4o, Llama 3.3, Phi-4, and DeepSeek-R1 with a GitHub token."
        ),
        ApiProviderEntity(
            id = "cloudflare_ai",
            name = "Cloudflare Workers AI",
            baseUrl = "https://api.cloudflare.com/client/v4/accounts",
            loginUrl = "https://dash.cloudflare.com/?to=/:account/ai/workers-ai",
            isFreeTierAvailable = true,
            description = "Free daily GPU inference for open models. Key format: account_id:api_token."
        ),
        ApiProviderEntity(
            id = "cerebras",
            name = "Cerebras Inference",
            baseUrl = "https://api.cerebras.ai/v1",
            loginUrl = "https://cloud.cerebras.ai/platform/",
            isFreeTierAvailable = true,
            description = "Ultra-fast wafer-scale inference for Llama 3.3 and 3.1 (1M free tokens/day, 0 card)."
        ),
        ApiProviderEntity(
            id = "mistral",
            name = "Mistral AI",
            baseUrl = "https://api.mistral.ai/v1",
            loginUrl = "https://console.mistral.ai/api-keys/",
            isFreeTierAvailable = true,
            description = "Experiment tier: Codestral, Mistral Large, and Small with no credit card required."
        ),
        ApiProviderEntity(
            id = "sambanova",
            name = "SambaNova Cloud",
            baseUrl = "https://api.sambanova.ai/v1",
            loginUrl = "https://cloud.sambanova.ai/apis",
            isFreeTierAvailable = true,
            description = "Enterprise SN40L chips serving Llama 3.3 70B & Qwen 2.5 (200k tokens/day free, 0 card)."
        ),
        ApiProviderEntity(
            id = "huggingface",
            name = "Hugging Face Inference",
            baseUrl = "https://api-inference.huggingface.co/v1",
            loginUrl = "https://huggingface.co/settings/tokens",
            isFreeTierAvailable = true,
            description = "Serverless inference for thousands of open models using your free HF personal access token."
        ),
        ApiProviderEntity(
            id = "cohere",
            name = "Cohere",
            baseUrl = "https://api.cohere.ai/v1",
            loginUrl = "https://dashboard.cohere.com/api-keys",
            isFreeTierAvailable = true,
            description = "Trial tier for Command R+, Command R, and Aya models (1,000 free calls/month, 0 card)."
        ),
        ApiProviderEntity(
            id = "glhf",
            name = "GLHF AI",
            baseUrl = "https://glhf.chat/api/openai/v1",
            loginUrl = "https://glhf.chat/users/settings/api",
            isFreeTierAvailable = true,
            description = "Zero-card free access for Llama 3.3 70B and Qwen 2.5 72B in active developer beta."
        ),
        ApiProviderEntity(
            id = "local_gguf",
            name = "Local GGUF (llama.cpp)",
            baseUrl = "localhost:8080",
            loginUrl = "",
            isFreeTierAvailable = true,
            description = "Run models directly on your device using llama.cpp."
        )
    )
}
