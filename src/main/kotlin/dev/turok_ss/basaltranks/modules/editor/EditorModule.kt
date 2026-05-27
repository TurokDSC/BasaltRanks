package dev.turok_ss.basaltranks.modules.editor

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Editor module: provides the visual GUI editor for badges,
 * the template browser, and handles all /badge command routing.
 *
 * This module must be registered AFTER BadgeModule since it depends on it.
 * It takes over /badge command handling from BadgeModule, which now only
 * manages data (CRUD, persistence).
 */
class EditorModule(private val plugin: BasaltRanks) : Module {

    override val name: String = "Editor"
    override val configKey: String = "editor"

    private var badgeCommand: BadgeCommand? = null
    var editorGUI: EditorGUI? = null
        private set
    var templateGUI: TemplateGUI? = null
        private set

    // ─────────────────────────────────────────────
    // Module lifecycle
    // ─────────────────────────────────────────────

    override fun onEnable() {
        // Create GUI handlers
        val editor = EditorGUI(plugin)
        val templates = TemplateGUI(plugin)
        editorGUI = editor
        templateGUI = templates

        // Register command handler for /badge
        val command = BadgeCommand(plugin, this)
        badgeCommand = command
        plugin.getCommand("badge")?.let { cmd ->
            cmd.setExecutor(command)
            cmd.tabCompleter = command
        }

        // Register GUI listeners
        Bukkit.getPluginManager().registerEvents(editor, plugin)
        Bukkit.getPluginManager().registerEvents(templates, plugin)

        plugin.logger.info("EditorModule enabled (GUI editor + template browser)")
    }

    override fun onDisable() {
        editorGUI?.cleanup()
        templateGUI?.cleanup()
        editorGUI = null
        templateGUI = null
        badgeCommand = null
        plugin.logger.info("EditorModule disabled")
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * Opens the badge editor GUI for a specific badge.
     * @param player The player to open the editor for
     * @param badgeId The ID of the badge to edit
     */
    fun openEditor(player: Player, badgeId: String) {
        editorGUI?.open(player, badgeId)
    }

    /**
     * Opens the template browser GUI.
     * @param player The player to open the browser for
     */
    fun openTemplates(player: Player) {
        templateGUI?.open(player)
    }
}
