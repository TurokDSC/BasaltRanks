package dev.turok_ss.basaltranks.modules.resourcepack

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module
import dev.turok_ss.basaltranks.utils.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.File
import java.util.UUID

/**
 * Orchestrates resource pack generation and serving.
 *
 * On enable: generates the pack if needed, starts the embedded HTTP server.
 * The server always starts (even without badges) so the web editor can function.
 * On PlayerJoin: sends the resource pack to the joining player with a 1-tick delay.
 * Provides regeneratePack() for on-the-fly regeneration after badge changes.
 */
class ResourcePackModule(private val plugin: BasaltRanks) : Module, Listener {

    override val name: String = "ResourcePack"
    override val configKey: String = "resourcepack"

    private val packServer = PackServer(plugin.logger)
    private var currentPackResult: PackGenerator.PackResult? = null
    private var packUrl: String = ""
    private var forcePack: Boolean = true
    private var promptMessage: String = ""
    private var serverIp: String = "localhost"
    private var port: Int = 8080

    // -----------------------------------------------
    // Module lifecycle
    // -----------------------------------------------

    override fun onEnable() {
        loadConfig()

        if (serverIp == "localhost" || serverIp == "127.0.0.1") {
            plugin.logger.warning("======================================================")
            plugin.logger.warning("  ResourcePack server-ip is set to '$serverIp'!")
            plugin.logger.warning("  Players won't be able to download the pack remotely.")
            plugin.logger.warning("  Set 'resourcepack.server-ip' to your public IP/domain.")
            plugin.logger.warning("======================================================")
        }

        // Always start the HTTP server so the web editor can function
        packServer.start(port)

        // Generate pack if badges exist
        val badgeModule = plugin.getBadgeModule()
        val badges = badgeModule?.getAllBadges() ?: emptyList()

        if (badges.isEmpty()) {
            plugin.logger.info("ResourcePackModule: No badges found, skipping pack generation")
            plugin.logger.info("ResourcePackModule: HTTP server running on port $port (web editor ready)")
        } else {
            generateAndServe()
        }

        // Register join listener
        Bukkit.getPluginManager().registerEvents(this, plugin)

        plugin.logger.info("ResourcePackModule enabled (port: $port)")
    }

    override fun onDisable() {
        packServer.stop()
        currentPackResult = null
        plugin.logger.info("ResourcePackModule disabled")
    }

    // -----------------------------------------------
    // Configuration
    // -----------------------------------------------

    private fun loadConfig() {
        val config = plugin.config
        val envIp = System.getenv("BASALTRANKS_IP") ?: System.getenv("SERVER_IP")
        serverIp = envIp ?: config.getString("resourcepack.server-ip", "localhost") ?: "localhost"
        val envPort = System.getenv("BASALTRANKS_PORT")?.toIntOrNull()
        port = envPort ?: config.getInt("resourcepack.port", 8080)
        if (envIp != null || envPort != null) {
            plugin.logger.info("ResourcePack: Using $serverIp:$port from environment variables")
        }
        forcePack = config.getBoolean("resourcepack.force-pack", true)
        promptMessage = config.getString(
            "resourcepack.prompt-message",
            "<gold>This server uses custom rank badges. Accept the resource pack to see them!"
        ) ?: ""
    }

    // -----------------------------------------------
    // Pack generation & serving
    // -----------------------------------------------

    /**
     * Generates the resource pack and starts (or restarts) the HTTP server.
     * Called on enable and when badges are modified.
     */
    private fun generateAndServe() {
        val badgeModule = plugin.getBadgeModule() ?: return
        val badges = badgeModule.getAllBadges()

        if (badges.isEmpty()) {
            plugin.logger.info("No badges to include in resource pack")
            return
        }

        // Generate the pack ZIP
        val result = PackGenerator.generate(plugin, badges)
        if (result == null) {
            plugin.logger.severe("Failed to generate resource pack!")
            return
        }

        currentPackResult = result
        packUrl = "http://$serverIp:$port/pack"

        // Reload pack data on the already-running server
        if (packServer.isRunning) {
            packServer.reload(result.file, port)
        } else {
            packServer.start(port, result.file)
        }

        plugin.logger.info("Resource pack ready: $packUrl (${result.badgeCount} badges, SHA-1: ${result.sha1Hash})")
    }

    /**
     * Regenerates the resource pack and sends it to all online players.
     * Call this after badges are created, deleted, or modified.
     */
    fun regeneratePack() {
        generateAndServe()

        // Send updated pack to all online players
        val result = currentPackResult ?: return
        for (player in Bukkit.getOnlinePlayers()) {
            sendPackToPlayer(player, result)
        }
    }

    // -----------------------------------------------
    // Player join handling
    // -----------------------------------------------

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val result = currentPackResult ?: return

        FoliaCompat.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                sendPackToPlayer(event.player, result)
            }
        }, 1L)
    }

    private fun sendPackToPlayer(player: Player, result: PackGenerator.PackResult) {
        if (packUrl.isBlank()) return

        try {
            val packUuid = UUID.nameUUIDFromBytes(result.sha1Bytes)
            val promptText = if (promptMessage.isNotBlank()) promptMessage else null

            player.addResourcePack(
                packUuid,
                packUrl,
                result.sha1Bytes,
                promptText,
                forcePack
            )
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send resource pack to ${player.name}: ${e.message}")
        }
    }

    // -----------------------------------------------
    // Public API
    // -----------------------------------------------

    /**
     * Returns the PackServer instance for registering additional HTTP contexts.
     */
    fun getPackServer(): PackServer = packServer

    /**
     * Returns the configured HTTP port.
     */
    fun getPort(): Int = port

    /**
     * Returns the current pack URL, or empty if no pack is available.
     */
    fun getPackUrl(): String = packUrl

    /**
     * Returns the current SHA-1 hash, or null if no pack is generated.
     */
    fun getPackHash(): String? = currentPackResult?.sha1Hash

    /**
     * Returns whether the pack server is running.
     */
    fun isServerRunning(): Boolean = packServer.isRunning

    /**
     * Returns the pack file, or null if not generated yet.
     */
    fun getPackFile(): File? = currentPackResult?.file
}
