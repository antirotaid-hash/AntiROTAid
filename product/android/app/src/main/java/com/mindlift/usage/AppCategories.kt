package com.mindlift.usage

/**
 * Maps app package names to the engine's neutral [AppCategory] values. This is
 * a small local heuristic table — purposely non-moral ("high_pull" describes
 * pull on attention, not the owner). Unknown packages fall back to "unknown" so
 * they don't inflate risk.
 *
 * Categories must match `mindlift.core.enums.AppCategory` string values.
 */
object AppCategories {

    private val EXACT: Map<String, String> = mapOf(
        // Social / high-pull
        "com.instagram.android" to "social",
        "com.zhiliaoapp.musically" to "video",      // TikTok
        "com.google.android.youtube" to "video",
        "com.facebook.katana" to "social",
        "com.twitter.android" to "social",
        "com.snapchat.android" to "social",
        "com.reddit.frontpage" to "social",
        // Messaging
        "com.whatsapp" to "messaging",
        "org.telegram.messenger" to "messaging",
        "com.facebook.orca" to "messaging",
        // Games (common stores/launchers are utility, not games)
        // Study / productivity
        "com.google.android.apps.docs" to "productivity",
        "com.microsoft.office.onenote" to "productivity",
        "com.mindlift" to "study",
    )

    private val KEYWORDS: List<Pair<String, String>> = listOf(
        "tiktok" to "video", "youtube" to "video", "video" to "video",
        "insta" to "social", "facebook" to "social", "snap" to "social",
        "twitter" to "social", "reddit" to "social", "social" to "social",
        "whatsapp" to "messaging", "telegram" to "messaging", "messenger" to "messaging",
        "game" to "games", "play.games" to "games",
        "docs" to "productivity", "office" to "productivity", "notion" to "productivity",
        "study" to "study", "anki" to "study", "duolingo" to "study",
    )

    fun categoryFor(packageName: String): String {
        EXACT[packageName]?.let { return it }
        val lower = packageName.lowercase()
        KEYWORDS.firstOrNull { lower.contains(it.first) }?.let { return it.second }
        return "unknown"
    }
}
