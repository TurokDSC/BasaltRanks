package dev.turok_ss.basaltranks

import dev.turok_ss.basaltranks.commands.BasaltRanksCommand
import dev.turok_ss.basaltranks.hooks.BasaltRanksExpansion
import dev.turok_ss.basaltranks.modules.ModuleManager
import dev.turok_ss.basaltranks.modules.badge.BadgeModule
import dev.turok_ss.basaltranks.modules.display.DisplayModule
import dev.turok_ss.basaltranks.modules.editor.EditorModule
import dev.turok_ss.basaltranks.modules.luckperms.LuckPermsModule
import dev.turok_ss.basaltranks.modules.resourcepack.ResourcePackModule
import dev.turok_ss.basaltranks.modules.web.WebModule
import dev.turok_ss.basaltranks.utils.Messages
import dev.turok_ss.basaltranks.utils.UpdateChecker
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class BasaltRanks : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: BasaltRanks
            private set

        fun getInstanceOrNull(): BasaltRanks? =
            if (::instance.isInitialized) instance else null
    }

    lateinit var moduleManager: ModuleManager
        private set

    val isFullyEnabled: Boolean
        get() = ::moduleManager.isInitialized

    private var badgeModule: BadgeModule? = null
    private var resourcePackModule: ResourcePackModule? = null
    private var displayModule: DisplayModule? = null
    private var luckPermsModule: LuckPermsModule? = null
    private var editorModule: EditorModule? = null
    private var webModule: WebModule? = null

    override fun onEnable() {
        instance = this

        saveDefaultConfig()
        migrateConfig()
        Messages.load(this)

        moduleManager = ModuleManager(this)
        registerModules()
        moduleManager.enableAll()

        getCommand("basaltranks")?.let { cmd ->
            val handler = BasaltRanksCommand(this)
            cmd.setExecutor(handler)
            cmd.tabCompleter = handler
        }

        val metrics = Metrics(this, 31418)
        metrics.addCustomChart(SimplePie("badge_count") {
            val count = getBadgeModule()?.getAllBadges()?.size ?: 0
            when {
                count == 0 -> "0"
                count <= 5 -> "1-5"
                count <= 10 -> "6-10"
                count <= 20 -> "11-20"
                else -> "21+"
            }
        })
        metrics.addCustomChart(SimplePie("web_editor") {
            if (getWebModule()?.isWebActive() == true) "Enabled" else "Disabled"
        })
        metrics.addCustomChart(SimplePie("luckperms") {
            if (getLuckPermsModule()?.isLuckPermsAvailable() == true) "Active" else "Inactive"
        })

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            BasaltRanksExpansion(this).register()
            logger.info("PlaceholderAPI expansion registered (8 placeholders)")
        }

        val updateChecker = UpdateChecker(this)
        Bukkit.getPluginManager().registerEvents(updateChecker, this)
        updateChecker.check()

        checkWebEditorPassword()
        Bukkit.getPluginManager().registerEvents(this, this)

        printStartupBanner()
    }

    private fun printStartupBanner() {
        val version = pluginMeta.version
        val modules = moduleManager.count()
        val badges = getBadgeModule()?.getAllBadges()?.size ?: 0
        val border = "──────────────────────────────────────"
        logger.info("")
        logger.info("  ┌$border┐")
        logger.info("  │${"  BasaltRanks v$version".padEnd(38)}│")
        logger.info("  │${"  Pixel-Art Rank Badges".padEnd(38)}│")
        logger.info("  ├$border┤")
        logger.info("  │${"  Modules: $modules active".padEnd(38)}│")
        logger.info("  │${"  Badges:  $badges loaded".padEnd(38)}│")
        logger.info("  │${"  Author:  Turok_SS (Basalt)".padEnd(38)}│")
        logger.info("  └$border┘")
        logger.info("")
    }

    private fun migrateConfig() {
        val currentVersion = config.getInt("config-version", 0)
        val latestVersion = 2
        if (currentVersion >= latestVersion) return

        val userValues = mutableMapOf<String, Any?>()
        for (key in config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                userValues[key] = config.get(key)
            }
        }

        val configFile = java.io.File(dataFolder, "config.yml")
        configFile.delete()
        saveResource("config.yml", false)
        reloadConfig()

        for ((key, value) in userValues) {
            if (key == "config-version") continue
            config.set(key, value)
        }
        config.set("config-version", latestVersion)
        saveConfig()

        logger.info("Config migrated to version $latestVersion (new settings added, comments preserved)")
        migrateMessages()
    }

    private fun migrateMessages() {
        val messagesFile = java.io.File(dataFolder, "messages.yml")
        if (!messagesFile.exists()) return

        val current = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile)
        val defaultMessages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            java.io.InputStreamReader(getResource("messages.yml") ?: return)
        )

        var added = 0
        for (key in defaultMessages.getKeys(false)) {
            if (!current.contains(key)) {
                current.set(key, defaultMessages.getString(key))
                added++
            }
        }

        if (added > 0) {
            current.save(messagesFile)
            logger.info("Messages migrated ($added new messages added)")
        }
    }

    private fun checkWebEditorPassword() {
        val password = config.getString("web-editor.password", "change-me") ?: "change-me"
        val webEnabled = config.getBoolean("web-editor.auto-start", true)
        if (webEnabled && (password == "change-me" || password.isBlank())) {
            logger.warning("======================================================")
            logger.warning("  SECURITY WARNING: Web editor password is '$password'!")
            logger.warning("  Change 'web-editor.password' in config.yml NOW!")
            logger.warning("  Or set 'web-editor.auto-start: false' to disable it.")
            logger.warning("======================================================")
        }
    }

    @EventHandler
    fun onOpJoin(event: PlayerJoinEvent) {
        if (!event.player.hasPermission("basaltranks.admin")) return
        val password = config.getString("web-editor.password", "change-me") ?: "change-me"
        val webEnabled = getWebModule()?.isWebActive() == true
        if (webEnabled && (password == "change-me" || password.isBlank())) {
            event.player.sendMessage(Messages.format(
                "<red>[BasaltRanks]</red> <yellow>Web editor has an insecure password! " +
                "Change <white>web-editor.password</white> in <white>config.yml</white> and run <white>/badge reload</white>."
            ))
        }
    }

    override fun onDisable() {
        if (::moduleManager.isInitialized) {
            moduleManager.disableAll()
        }
        logger.info("BasaltRanks disabled!")
    }

    private fun registerModules() {
        // 1. Badge module (core, must be first)
        val badge = BadgeModule(this)
        badgeModule = badge
        moduleManager.register(badge)

        // 2. Resource Pack module (depends on Badge)
        val resourcePack = ResourcePackModule(this)
        resourcePackModule = resourcePack
        moduleManager.register(resourcePack)

        // 3. Display module (depends on Badge)
        val display = DisplayModule(this)
        displayModule = display
        moduleManager.register(display)

        // 4. LuckPerms module (depends on Badge + Display, soft-depends on LuckPerms plugin)
        val luckPerms = LuckPermsModule(this)
        luckPermsModule = luckPerms
        moduleManager.register(luckPerms)

        // 5. Editor module (depends on Badge, handles /badge commands + GUIs)
        val editor = EditorModule(this)
        editorModule = editor
        moduleManager.register(editor)

        // 6. Web editor module (depends on ResourcePack + Badge)
        val web = WebModule(this)
        webModule = web
        moduleManager.register(web)
    }

    /**
     * Returns the BadgeModule instance, or null if not enabled.
     */
    fun getBadgeModule(): BadgeModule? {
        if (badgeModule == null) return null
        return if (moduleManager.isEnabled("Badge")) badgeModule else null
    }

    /**
     * Returns the ResourcePackModule instance, or null if not enabled.
     */
    fun getResourcePackModule(): ResourcePackModule? {
        if (resourcePackModule == null) return null
        return if (moduleManager.isEnabled("ResourcePack")) resourcePackModule else null
    }

    /**
     * Returns the DisplayModule instance, or null if not enabled.
     */
    fun getDisplayModule(): DisplayModule? {
        if (displayModule == null) return null
        return if (moduleManager.isEnabled("Display")) displayModule else null
    }

    /**
     * Returns the EditorModule instance, or null if not enabled.
     */
    fun getEditorModule(): EditorModule? {
        if (editorModule == null) return null
        return if (moduleManager.isEnabled("Editor")) editorModule else null
    }

    /**
     * Returns the LuckPermsModule instance, or null if not enabled.
     */
    fun getLuckPermsModule(): LuckPermsModule? {
        if (luckPermsModule == null) return null
        return if (moduleManager.isEnabled("LuckPerms")) luckPermsModule else null
    }

    /**
     * Returns the WebModule instance, or null if not enabled.
     */
    fun getWebModule(): WebModule? {
        if (webModule == null) return null
        return if (moduleManager.isEnabled("Web")) webModule else null
    }
}
