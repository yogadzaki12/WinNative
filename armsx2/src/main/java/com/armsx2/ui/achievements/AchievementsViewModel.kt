package com.armsx2.ui.achievements

import org.json.JSONObject

/**
 * Achievement data model + JSON parser for NativeApp.getAchievementsJSON().
 * The armsx2 achievements UI has been removed (the WinNative host renders its
 * own achievements screen), but the host app consumes [AchievementItem] and
 * [parseAchievementItems] directly, so they stay here with unchanged signatures.
 */
data class AchievementItem(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val progress: String,
    val iconUrl: String,
    // Which RA subset this achievement belongs to (0 = base/shared set).
    val subsetId: Int = 0,
)

/**
 * Parse the achievement list out of NativeApp.getAchievementsJSON(). The native
 * side (Achievements::GetAchievementsAsJSON) emits the list under the "items"
 * key — NOT "achievements". Keeps RetroAchievements' native list order (its
 * display/progression order).
 */
fun parseAchievementItems(json: String): List<AchievementItem> {
    if (json.isBlank()) return emptyList()
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val array = root.optJSONArray("items") ?: return emptyList()
    return buildList {
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            add(
                AchievementItem(
                    id = item.optInt("id"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    points = item.optInt("points"),
                    unlocked = item.optBoolean("unlocked"),
                    progress = item.optString("measuredProgress"),
                    iconUrl = item.optString("iconUrl", item.optString("badgeUrl")),
                    subsetId = item.optInt("subsetId"),
                ),
            )
        }
    }
}
