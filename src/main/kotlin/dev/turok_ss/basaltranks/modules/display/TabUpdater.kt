package dev.turok_ss.basaltranks.modules.display

import dev.turok_ss.basaltranks.BasaltRanks
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TabUpdater(
    private val plugin: BasaltRanks,
    private val animationScheduler: AnimationScheduler
) : Runnable {

    private val lastBadge = ConcurrentHashMap<UUID, String?>()
    private val lastFrame = ConcurrentHashMap<UUID, Int>()
    private val lastSuffix = ConcurrentHashMap<UUID, String?>()
    private val lastSuffixFrame = ConcurrentHashMap<UUID, Int>()

    override fun run() {
        val badgeModule = plugin.getBadgeModule() ?: return
        val online = Bukkit.getOnlinePlayers()
        if (online.isEmpty()) return

        val activeUuids = mutableSetOf<UUID>()
        for (player in online) {
            activeUuids.add(player.uniqueId)
            val prefixBadge = badgeModule.getPlayerBadge(player.uniqueId)
            val suffixBadge = badgeModule.getPlayerSuffixBadge(player.uniqueId)
            val prefixId = prefixBadge?.id
            val suffixId = suffixBadge?.id
            val prefixFrame = prefixBadge?.let { animationScheduler.getCurrentFrame(it) } ?: 0
            val suffixFrame = suffixBadge?.let { animationScheduler.getCurrentFrame(it) } ?: 0

            val prefixChanged = prefixId != lastBadge[player.uniqueId]
            val prefixFrameChanged = prefixBadge?.animated == true && prefixFrame != lastFrame[player.uniqueId]
            val suffixChanged = suffixId != lastSuffix[player.uniqueId]
            val suffixFrameChanged = suffixBadge?.animated == true && suffixFrame != lastSuffixFrame[player.uniqueId]

            if (!prefixChanged && !prefixFrameChanged && !suffixChanged && !suffixFrameChanged) continue

            if (prefixId != null) lastBadge[player.uniqueId] = prefixId else lastBadge.remove(player.uniqueId)
            lastFrame[player.uniqueId] = prefixFrame
            if (suffixId != null) lastSuffix[player.uniqueId] = suffixId else lastSuffix.remove(player.uniqueId)
            lastSuffixFrame[player.uniqueId] = suffixFrame

            if (prefixBadge != null || suffixBadge != null) {
                var name: Component = player.displayName()
                if (prefixBadge != null) {
                    val prefixChar = prefixBadge.getCurrentChar(prefixFrame)
                    name = Component.text("$prefixChar ").append(name)
                }
                if (suffixBadge != null) {
                    val suffixChar = suffixBadge.getCurrentChar(suffixFrame)
                    name = name.append(Component.text(" $suffixChar"))
                }
                player.playerListName(name)
            } else {
                player.playerListName(player.displayName())
            }
        }
        lastBadge.keys.retainAll(activeUuids)
        lastFrame.keys.retainAll(activeUuids)
        lastSuffix.keys.retainAll(activeUuids)
        lastSuffixFrame.keys.retainAll(activeUuids)
    }

    fun updateSingle(player: org.bukkit.entity.Player) {
        val badgeModule = plugin.getBadgeModule() ?: return
        val prefixBadge = badgeModule.getPlayerBadge(player.uniqueId)
        val suffixBadge = badgeModule.getPlayerSuffixBadge(player.uniqueId)

        if (prefixBadge != null || suffixBadge != null) {
            var name: Component = player.displayName()
            if (prefixBadge != null) {
                val frame = animationScheduler.getCurrentFrame(prefixBadge)
                val prefixChar = prefixBadge.getCurrentChar(frame)
                name = Component.text("$prefixChar ").append(name)
            }
            if (suffixBadge != null) {
                val frame = animationScheduler.getCurrentFrame(suffixBadge)
                val suffixChar = suffixBadge.getCurrentChar(frame)
                name = name.append(Component.text(" $suffixChar"))
            }
            player.playerListName(name)
        } else {
            player.playerListName(player.displayName())
        }
    }
}
