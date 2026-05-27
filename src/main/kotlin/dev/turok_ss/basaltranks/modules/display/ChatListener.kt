package dev.turok_ss.basaltranks.modules.display

import dev.turok_ss.basaltranks.BasaltRanks
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Listens to AsyncChatEvent at HIGH priority (after other plugins like BasaltSMP
 * which typically operate at NORMAL priority) and prepends the player's badge
 * Unicode character to the chat message renderer output.
 */
class ChatListener(private val plugin: BasaltRanks) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val badgeModule = plugin.getBadgeModule() ?: return
        val prefixBadge = badgeModule.getPlayerBadge(event.player.uniqueId)
        val suffixBadge = badgeModule.getPlayerSuffixBadge(event.player.uniqueId)

        if (prefixBadge == null && suffixBadge == null) return

        val previousRenderer = event.renderer()

        val badgeRenderer = ChatRenderer { source: Player, sourceDisplayName: Component, message: Component, viewer: Audience ->
            val original = previousRenderer.render(source, sourceDisplayName, message, viewer)
            var result = original
            if (prefixBadge != null) {
                result = Component.text("${prefixBadge.unicodeChar} ").append(result)
            }
            if (suffixBadge != null) {
                result = result.append(Component.text(" ${suffixBadge.unicodeChar}"))
            }
            result
        }

        event.renderer(badgeRenderer)
    }
}
