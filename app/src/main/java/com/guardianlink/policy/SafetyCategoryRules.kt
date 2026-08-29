package com.guardianlink.policy

import com.guardianlink.model.EnforcementDecision
import com.guardianlink.model.SafetyCategory

/**
 * Small, reviewable browser filter that runs entirely on the child device.
 * It is deliberately not presented as a complete content-classification service:
 * it checks known domains and clear page/search terms, needs no account or paid API,
 * and can be extended by parents with their own browser keyword rules.
 */
object SafetyCategoryRules {
    private data class Rule(val domains: Set<String>, val terms: Set<String>)

    private val rules = mapOf(
        SafetyCategory.ADULT to Rule(
            domains = setOf("pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com", "onlyfans.com"),
            terms = setOf("pornography", "porn video", "adult video", "xxx video", "nude videos", "onlyfans")
        ),
        SafetyCategory.VIOLENCE to Rule(
            domains = emptySet(),
            terms = setOf("graphic violence", "graphic gore", "gore video", "beheading video", "snuff video")
        ),
        SafetyCategory.GAMBLING to Rule(
            domains = setOf("bet365.com", "pokerstars.com", "draftkings.com", "fanduel.com", "stake.com", "1xbet.com"),
            terms = setOf("online casino", "sports betting", "betting odds", "slot machine", "poker for money", "real money gambling")
        ),
        SafetyCategory.SOCIAL_MEDIA to Rule(
            domains = setOf("facebook.com", "instagram.com", "tiktok.com", "snapchat.com", "x.com", "twitter.com", "reddit.com", "discord.com", "threads.net", "tumblr.com"),
            terms = emptySet()
        )
    )

    fun decision(categories: Set<SafetyCategory>, url: String, titleOrVisibleText: String): EnforcementDecision? =
        matchingCategory(categories, url, titleOrVisibleText)?.let { category ->
            EnforcementDecision(true, "${category.displayName} is blocked by family safety filters")
        }

    fun matchingCategory(categories: Set<SafetyCategory>, url: String, titleOrVisibleText: String): SafetyCategory? {
        if (categories.isEmpty()) return null
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        // Search engines encode spaces as + or %20. Decode the URL before checking terms so a
        // protected search is stopped before its results page is rendered.
        val readableUrl = runCatching { java.net.URLDecoder.decode(url, "UTF-8") }.getOrDefault(url).replace('+', ' ')
        val page = "$readableUrl $titleOrVisibleText".lowercase()
        return categories.sortedBy { it.ordinal }.firstOrNull { category ->
            val rule = rules.getValue(category)
            rule.domains.any { domain -> host == domain || host.endsWith(".$domain") } ||
                rule.terms.any { term -> page.contains(term) }
        }
    }
}
