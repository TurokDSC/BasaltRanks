package dev.turok_ss.basaltranks.modules.luckperms

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module
import dev.turok_ss.basaltranks.utils.FoliaCompat
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.event.EventSubscription
import net.luckperms.api.event.user.UserDataRecalculateEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Optional LuckPerms integration module.
 *
 * Automatically assigns badges to players based on their LuckPerms primary group.
 * The mapping from group name to badge ID is defined in config.yml under
 * `luckperms.group-badges`.
 *
 * This module is a soft dependency: it gracefully disables itself if LuckPerms
 * is not present on the server.
 */
class LuckPermsModule(private val plugin: BasaltRanks) : Module, Listener {

    override val name: String = "LuckPerms"
    override val configKey: String = "luckperms"

    private var luckPerms: LuckPerms? = null
    private var groupBadgeMap: Map<String, String> = emptyMap()
    private var eventSubscription: EventSubscription<UserDataRecalculateEvent>? = null

    // ─────────────────────────────────────────────
    // Module lifecycle
    // ─────────────────────────────────────────────

    override fun onEnable() {
        // Check if LuckPerms is present
        val luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms")
        if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled) {
            plugin.logger.warning("LuckPermsModule: LuckPerms not found, module will be inactive")
            plugin.logger.warning("LuckPermsModule: Install LuckPerms for automatic group-based badge assignment")
            return
        }

        // Get the LuckPerms API
        luckPerms = try {
            LuckPermsProvider.get()
        } catch (e: Exception) {
            plugin.logger.warning("LuckPermsModule: Failed to get LuckPerms API: ${e.message}")
            return
        }

        // Load group-badge mappings from config
        loadGroupBadgeMap()

        if (groupBadgeMap.isEmpty()) {
            plugin.logger.info("LuckPermsModule: No group-badge mappings configured, module inactive")
            return
        }

        // Register Bukkit event listener for PlayerJoinEvent
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Subscribe to LuckPerms rank change events for real-time updates
        subscribeToLuckPermsEvents()

        // Update all currently online players
        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerBadge(player)
        }

        plugin.logger.info("LuckPermsModule enabled with ${groupBadgeMap.size} group-badge mappings")
    }

    override fun onDisable() {
        // Unsubscribe from LuckPerms events
        eventSubscription?.close()
        eventSubscription = null
        luckPerms = null
        groupBadgeMap = emptyMap()

        plugin.logger.info("LuckPermsModule disabled")
    }

    // ─────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────

    private fun loadGroupBadgeMap() {
        val config = plugin.config
        val section = config.getConfigurationSection("luckperms.group-badges")

        if (section == null) {
            groupBadgeMap = emptyMap()
            return
        }

        val map = mutableMapOf<String, String>()
        for (group in section.getKeys(false)) {
            val badgeId = section.getString(group)
            if (!badgeId.isNullOrBlank()) {
                map[group.lowercase()] = badgeId.lowercase()
            }
        }
        groupBadgeMap = map
    }

    // ─────────────────────────────────────────────
    // Public API (used by WebApiHandler)
    // ─────────────────────────────────────────────

    /**
     * Returns true if LuckPerms is available and the API was loaded.
     */
    fun isLuckPermsAvailable(): Boolean = luckPerms != null

    /**
     * Returns the current group-to-badge mapping (lowercase keys and values).
     */
    fun getGroupBadgeMappings(): Map<String, String> = groupBadgeMap.toMap()

    /**
     * Returns all LuckPerms group names. If LP is available, fetches from the
     * LP group manager. Otherwise falls back to the groups defined in config.
     */
    fun getAllGroups(): List<String> {
        val lp = luckPerms
        if (lp != null) {
            return try {
                lp.groupManager.loadedGroups.map { it.name.lowercase() }.sorted()
            } catch (e: Exception) {
                plugin.logger.warning("LuckPermsModule: Failed to fetch groups from LP API: ${e.message}")
                getGroupsFromConfig()
            }
        }
        return getGroupsFromConfig()
    }

    private fun getGroupsFromConfig(): List<String> {
        val section = plugin.config.getConfigurationSection("luckperms.group-badges")
            ?: return emptyList()
        return section.getKeys(false).map { it.lowercase() }.sorted()
    }

    /**
     * Updates the group-to-badge mappings in config, reloads the internal map,
     * and saves the config file.
     *
     * @param newMappings Map of groupName (lowercase) to badgeId (lowercase).
     *                    An empty string value means "no badge" for that group.
     */
    fun updateMappings(newMappings: Map<String, String>) {
        // Clear the existing section
        plugin.config.set("luckperms.group-badges", null)

        // Write new mappings
        for ((group, badgeId) in newMappings) {
            plugin.config.set("luckperms.group-badges.${group.lowercase()}", badgeId)
        }

        plugin.saveConfig()
        loadGroupBadgeMap()

        plugin.logger.info("LuckPermsModule: Updated ${newMappings.size} group-badge mappings")
    }

    /**
     * Re-evaluates all online players' badges based on current mappings.
     * Should be called after mappings are updated.
     */
    fun refreshAllPlayers() {
        if (luckPerms == null) return
        for (player in Bukkit.getOnlinePlayers()) {
            updatePlayerBadge(player)
        }
        plugin.logger.info("LuckPermsModule: Refreshed badges for ${Bukkit.getOnlinePlayers().size} online players")
    }

    // ─────────────────────────────────────────────
    // Event handling
    // ─────────────────────────────────────────────

    /**
     * When a player joins, check their LuckPerms group and assign the
     * corresponding badge if one is configured.
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        FoliaCompat.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                updatePlayerBadge(event.player)
            }
        }, 2L)
    }

    private fun subscribeToLuckPermsEvents() {
        val lp = luckPerms ?: return

        eventSubscription = lp.eventBus.subscribe(plugin, UserDataRecalculateEvent::class.java) { event ->
            val uuid = event.user.uniqueId
            FoliaCompat.runTask(plugin, Runnable {
                val player = Bukkit.getPlayer(uuid) ?: return@Runnable
                updatePlayerBadge(player)
            })
        }

        plugin.logger.info("LuckPermsModule: Subscribed to real-time rank change events")
    }

    // ─────────────────────────────────────────────
    // Badge assignment logic
    // ─────────────────────────────────────────────

    /**
     * Checks the player's LuckPerms primary group and assigns the
     * corresponding badge, if one is mapped in config.
     *
     * @param player The player to update
     */
    private fun updatePlayerBadge(player: Player) {
        val lp = luckPerms ?: return
        val badgeModule = plugin.getBadgeModule() ?: return

        val user = lp.userManager.getUser(player.uniqueId) ?: return
        val primaryGroup = user.primaryGroup.lowercase()

        val badgeId = groupBadgeMap[primaryGroup]

        if (badgeId == null) {
            // No mapping for this group -- do not remove any manually assigned badge
            return
        }

        // Check if the badge actually exists
        val badge = badgeModule.getBadge(badgeId)
        if (badge == null) {
            plugin.logger.warning(
                "LuckPermsModule: Badge '$badgeId' mapped to group '$primaryGroup' does not exist"
            )
            return
        }

        // Check if the player already has this badge to avoid unnecessary writes
        val currentBadgeId = badgeModule.getPlayerBadgeId(player.uniqueId)
        if (currentBadgeId == badgeId) {
            return // Already has the correct badge
        }

        // Assign the badge
        badgeModule.assignBadge(player.uniqueId, badgeId)

        // Update display if the DisplayModule is active
        val displayModule = plugin.getDisplayModule()
        displayModule?.updatePlayerDisplay(player)

        plugin.logger.info(
            "LuckPermsModule: Assigned badge '$badgeId' to ${player.name} (group: $primaryGroup)"
        )
    }
}
