package dev.turok_ss.basaltranks.hooks

import dev.turok_ss.basaltranks.BasaltRanks
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

class BasaltRanksExpansion(private val plugin: BasaltRanks) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "basaltranks"
    override fun getAuthor(): String = "Turok_SS"
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null
        val badgeModule = plugin.getBadgeModule() ?: return null
        val badge = badgeModule.getPlayerBadge(player.uniqueId)
        val suffixBadge = badgeModule.getPlayerSuffixBadge(player.uniqueId)

        return when (params.lowercase()) {
            "badge", "id" -> badge?.id ?: ""
            "text", "badge_text" -> badge?.displayText ?: ""
            "style", "badge_style" -> badge?.style ?: ""
            "unicode", "badge_unicode" -> badge?.unicodeChar?.toString() ?: ""
            "has_badge" -> (badge != null).toString()
            "priority", "badge_priority" -> badge?.priority?.toString() ?: "0"
            "color", "badge_color" -> badge?.bgColor ?: ""
            "text_color" -> badge?.textColor ?: ""
            "animated" -> (badge?.animated ?: false).toString()
            "animation_type" -> badge?.animationType ?: ""
            "suffix_badge", "suffix_id" -> suffixBadge?.id ?: ""
            "suffix_text" -> suffixBadge?.displayText ?: ""
            "suffix_unicode" -> suffixBadge?.unicodeChar?.toString() ?: ""
            "has_suffix" -> (suffixBadge != null).toString()
            else -> null
        }
    }
}
