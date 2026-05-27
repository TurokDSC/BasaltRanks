package dev.turok_ss.basaltranks.modules.display

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module
import dev.turok_ss.basaltranks.utils.FoliaCompat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Coordinates all badge display systems: chat, nametags, and tab list.
 *
 * Each display mode can be independently toggled via config:
 * - display.chat: Badge prefix in chat messages
 * - display.nametag: Badge prefix above player heads via Scoreboard teams
 * - display.tab: Badge prefix in the tab player list
 */
class DisplayModule(private val plugin: BasaltRanks) : Module, Listener {

    companion object {
        private const val MAX_NAMETAG_REFRESH_PLAYERS = 200
    }

    override val name: String = "Display"
    override val configKey: String = "display"

    private var chatEnabled = true
    private var nametagEnabled = true
    private var tabEnabled = true

    private var baseTickRate = 5L
    private var tabUpdateRate = 10L
    private var nametagUpdateRate = 10L

    private var chatListener: ChatListener? = null
    private var nametagManager: NametagManager? = null
    private var tabUpdater: TabUpdater? = null
    private var nametagTask: FoliaCompat.CancellableTask? = null
    private var tabTask: FoliaCompat.CancellableTask? = null
    var animationScheduler: AnimationScheduler? = null
        private set

    // ─────────────────────────────────────────────
    // Module lifecycle
    // ─────────────────────────────────────────────

    override fun onEnable() {
        loadConfig()

        val scheduler = AnimationScheduler(plugin)
        animationScheduler = scheduler
        scheduler.start(baseTickRate)

        if (chatEnabled) {
            val listener = ChatListener(plugin)
            Bukkit.getPluginManager().registerEvents(listener, plugin)
            chatListener = listener
            plugin.logger.info("Display: Chat badge prefix enabled")
        }

        if (nametagEnabled) {
            val manager = NametagManager(plugin, scheduler)
            nametagManager = manager
            Bukkit.getPluginManager().registerEvents(this, plugin)

            nametagTask = FoliaCompat.runTaskTimer(plugin, Runnable {
                val online = Bukkit.getOnlinePlayers()
                if (online.isEmpty() || online.size > MAX_NAMETAG_REFRESH_PLAYERS) return@Runnable
                manager.refreshTeams()
                for (player in online) {
                    manager.updatePlayer(player)
                }
            }, 20L, nametagUpdateRate)
            plugin.logger.info("Display: Nametag badge prefix enabled")
        }

        if (tabEnabled) {
            val updater = TabUpdater(plugin, scheduler)
            tabUpdater = updater
            tabTask = FoliaCompat.runTaskTimer(plugin, updater, 20L, tabUpdateRate)
            plugin.logger.info("Display: Tab badge prefix enabled")
        }

        plugin.logger.info("DisplayModule enabled (chat=$chatEnabled, nametag=$nametagEnabled, tab=$tabEnabled)")
    }

    override fun onDisable() {
        animationScheduler?.stop()
        animationScheduler = null

        tabTask?.cancel()
        tabTask = null
        tabUpdater = null

        nametagTask?.cancel()
        nametagTask = null
        nametagManager?.cleanup()
        nametagManager = null

        chatListener = null

        plugin.logger.info("DisplayModule disabled")
    }

    // ─────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────

    private fun loadConfig() {
        val config = plugin.config
        chatEnabled = config.getBoolean("display.chat", true)
        nametagEnabled = config.getBoolean("display.nametag", true)
        tabEnabled = config.getBoolean("display.tab", true)
        baseTickRate = config.getLong("animation.base-tick-rate", 5L).coerceAtLeast(1L)
        tabUpdateRate = config.getLong("animation.tab-update-rate", 10L).coerceAtLeast(1L)
        nametagUpdateRate = config.getLong("animation.nametag-update-rate", 10L).coerceAtLeast(1L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        FoliaCompat.runTaskLater(plugin, Runnable {
            nametagManager?.onPlayerJoin(event.player)
        }, 5L)
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * Returns the badge Unicode character as a Component prefix for the given player.
     * Returns null if the player has no badge assigned.
     *
     * @param player The player to get the badge prefix for
     * @return Component with badge character + space, or null
     */
    fun getBadgePrefix(player: Player): Component? {
        val badgeModule = plugin.getBadgeModule() ?: return null
        val badge = badgeModule.getPlayerBadge(player.uniqueId) ?: return null
        val currentChar = badge.getCurrentChar(animationScheduler?.getCurrentFrame(badge) ?: 0)
        return Component.text("$currentChar ")
    }

    /**
     * Returns the suffix badge Unicode character as a Component for the given player.
     * Returns null if the player has no suffix badge assigned.
     *
     * @param player The player to get the suffix badge for
     * @return Component with space + badge character, or null
     */
    fun getBadgeSuffix(player: Player): Component? {
        val badgeModule = plugin.getBadgeModule() ?: return null
        val badge = badgeModule.getPlayerSuffixBadge(player.uniqueId) ?: return null
        val currentChar = badge.getCurrentChar(animationScheduler?.getCurrentFrame(badge) ?: 0)
        return Component.text(" $currentChar")
    }

    /**
     * Updates all display systems for a specific player.
     * Call this when a player's badge assignment changes.
     *
     * @param player The player to update displays for
     */
    fun updatePlayerDisplay(player: Player) {
        if (nametagEnabled) {
            nametagManager?.updatePlayer(player)
        }
        if (tabEnabled) {
            tabUpdater?.updateSingle(player)
        }
    }

    /**
     * Updates all display systems for all online players.
     * Call this after bulk badge changes or reload.
     */
    fun updateAllDisplays() {
        if (nametagEnabled) {
            nametagManager?.refreshTeams()
        }

        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerDisplay(player)
        }
    }

    /**
     * Removes a player from all display systems.
     * Call this when a player's badge is removed.
     *
     * @param player The player to remove from displays
     */
    fun removePlayerDisplay(player: Player) {
        if (nametagEnabled) {
            nametagManager?.removePlayer(player)
        }
        if (tabEnabled) {
            // Reset tab name to default
            player.playerListName(player.displayName())
        }
    }
}
