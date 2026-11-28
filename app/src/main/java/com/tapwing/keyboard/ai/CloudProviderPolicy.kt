// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.AiProviderType

data class CloudProviderDisclosure(
    val provider: AiProviderType,
    val displayName: String,
    val shortBadgeLabel: String,
    val privacyPolicyUrl: String,
    val summaryDe: String,
    val summaryEn: String
)

/**
 * Explicit user consent disclosures and privacy links for optional cloud AI & cloud dictation providers.
 * No text or audio may be transmitted to a cloud provider until the user has consented to that provider.
 */
object CloudProviderPolicy {

    val disclosures: Map<AiProviderType, CloudProviderDisclosure> = mapOf(
        AiProviderType.GEMINI_API to CloudProviderDisclosure(
            provider = AiProviderType.GEMINI_API,
            displayName = "Google Gemini API",
            shortBadgeLabel = "Cloud · Gemini",
            privacyPolicyUrl = "https://ai.google.dev/gemini-api/terms",
            summaryDe = "Sendet ausschließlich den aktiv ausgewählten Text (Schreibhilfe) bzw. die aktive Sprachaufnahme (Cloud-Diktat / Gemini Live) verschlüsselt direkt an Google Gemini.",
            summaryEn = "Sends only the actively selected text (Writing Aid) or active voice audio (Cloud Dictation / Gemini Live) over HTTPS/WSS directly to Google Gemini."
        ),
        AiProviderType.GROQ_API to CloudProviderDisclosure(
            provider = AiProviderType.GROQ_API,
            displayName = "Groq Cloud API",
            shortBadgeLabel = "Cloud · Groq",
            privacyPolicyUrl = "https://groq.com/privacy-policy/",
            summaryDe = "Sendet ausschließlich den aktiv bearbeiteten Text oder die aktive Sprachaufnahme verschlüsselt direkt an Groq.",
            summaryEn = "Sends only the actively processed text or voice recording over HTTPS directly to Groq."
        ),
        AiProviderType.OPENROUTER_API to CloudProviderDisclosure(
            provider = AiProviderType.OPENROUTER_API,
            displayName = "OpenRouter API",
            shortBadgeLabel = "Cloud · OpenRouter",
            privacyPolicyUrl = "https://openrouter.ai/privacy",
            summaryDe = "Sendet ausschließlich den aktiv ausgewählten Text für die Schreibhilfe verschlüsselt direkt an OpenRouter und das dort gewählte Modell.",
            summaryEn = "Sends only the actively selected text for writing assistance over HTTPS directly to OpenRouter and your chosen model."
        ),
        AiProviderType.XAI_API to CloudProviderDisclosure(
            provider = AiProviderType.XAI_API,
            displayName = "xAI Grok API",
            shortBadgeLabel = "Cloud · Grok",
            privacyPolicyUrl = "https://x.ai/legal/privacy-policy",
            summaryDe = "Sendet ausschließlich den aktiv ausgewählten Text für die Schreibhilfe verschlüsselt direkt an xAI.",
            summaryEn = "Sends only the actively selected text for writing assistance over HTTPS directly to xAI."
        ),
        AiProviderType.OPENAI_COMPATIBLE to CloudProviderDisclosure(
            provider = AiProviderType.OPENAI_COMPATIBLE,
            displayName = "OpenAI / Custom HTTPS Endpoint",
            shortBadgeLabel = "Cloud · OpenAI",
            privacyPolicyUrl = "https://openai.com/policies/privacy-policy",
            summaryDe = "Sendet ausschließlich den aktiv ausgewählten Text bzw. die aktive Sprachaufnahme an OpenAI oder deinen eingetragenen HTTPS-Endpunkt.",
            summaryEn = "Sends only the actively selected text or voice recording to OpenAI or your configured HTTPS endpoint."
        )
    )

    fun isCloudProvider(provider: AiProviderType): Boolean = provider in disclosures

    fun disclosureFor(provider: AiProviderType): CloudProviderDisclosure? = disclosures[provider]

    fun badgeLabelFor(provider: AiProviderType, isLive: Boolean = false): String {
        if (isLive && provider == AiProviderType.GEMINI_API) return "Cloud · Gemini Live"
        return disclosures[provider]?.shortBadgeLabel ?: "Cloud"
    }

    fun hasConsent(consentedProviders: Set<String>, provider: AiProviderType): Boolean {
        if (!isCloudProvider(provider)) return true
        return provider.name in consentedProviders
    }
}
