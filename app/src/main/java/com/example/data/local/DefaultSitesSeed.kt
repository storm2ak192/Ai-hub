package com.example.data.local

object DefaultSitesSeed {

    const val GOOGLE_ACCOUNTS_SITE_ID = "site_google_accounts"

    fun getDefaultSites(): List<SiteEntity> = listOf(
        SiteEntity(
            id = "site_chatgpt",
            name = "ChatGPT",
            url = "https://chatgpt.com",
            description = "OpenAI's conversational assistant for reasoning, coding, and creativity.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://chatgpt.com&sz=128",
            iconPreset = "chatgpt",
            accentColorHex = 0xFF10A37FL,
            isFavorite = true,
            isEnabled = true,
            sortOrder = 0,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_gemini",
            name = "Gemini",
            url = "https://gemini.google.com",
            description = "Google's multimodal AI workspace for research, writing, and analysis.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://gemini.google.com&sz=128",
            iconPreset = "gemini",
            accentColorHex = 0xFF4F46E5L,
            isFavorite = true,
            isEnabled = true,
            sortOrder = 1,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_grok",
            name = "Grok",
            url = "https://grok.com",
            description = "xAI real-time assistant with deep search and unfiltered reasoning.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://grok.com&sz=128",
            iconPreset = "grok",
            accentColorHex = 0xFF334155L,
            isFavorite = false,
            isEnabled = true,
            sortOrder = 2,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_qwen",
            name = "Qwen",
            url = "https://chat.qwen.ai",
            description = "Alibaba Cloud's multilingual reasoning, coding, and vision assistant.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://chat.qwen.ai&sz=128",
            iconPreset = "qwen",
            accentColorHex = 0xFF6366F1L,
            isFavorite = false,
            isEnabled = true,
            sortOrder = 3,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_deepseek",
            name = "DeepSeek",
            url = "https://chat.deepseek.com",
            description = "DeepSeek R1 & V3 deep thinking model for math, code, and logic.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://chat.deepseek.com&sz=128",
            iconPreset = "deepseek",
            accentColorHex = 0xFF2563EBL,
            isFavorite = true,
            isEnabled = true,
            sortOrder = 4,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_claude",
            name = "Claude",
            url = "https://claude.ai",
            description = "Anthropic's thoughtful AI assistant for nuanced writing and engineering.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://claude.ai&sz=128",
            iconPreset = "claude",
            accentColorHex = 0xFFD97706L,
            isFavorite = true,
            isEnabled = true,
            sortOrder = 5,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = "site_perplexity",
            name = "Perplexity",
            url = "https://www.perplexity.ai",
            description = "AI-powered answer engine with live web citations and deep research.",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://www.perplexity.ai&sz=128",
            iconPreset = "perplexity",
            accentColorHex = 0xFF0EA5E9L,
            isFavorite = false,
            isEnabled = true,
            sortOrder = 6,
            isDefault = true,
            profileKey = "aihub_shared_session"
        ),
        SiteEntity(
            id = GOOGLE_ACCOUNTS_SITE_ID,
            name = "Google Account",
            url = "https://accounts.google.com",
            description = "قم بتسجيل حساب جوجل هنا اولا ثم التسجيل به في المواقع",
            iconUrl = "https://www.google.com/s2/favicons?domain_url=https://accounts.google.com&sz=128",
            iconPreset = "google",
            accentColorHex = 0xFFEA4335L,
            isFavorite = false,
            isEnabled = true,
            sortOrder = 7,
            isDefault = true,
            profileKey = "aihub_shared_session"
        )
    )
}
