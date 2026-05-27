package dev.turok_ss.basaltranks.utils

import dev.turok_ss.basaltranks.BasaltRanks
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Messages {
    private val mm = MiniMessage.miniMessage()
    private lateinit var config: YamlConfiguration
    private var prefix = ""

    fun load(plugin: BasaltRanks) {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
        prefix = config.getString(
            "prefix",
            "<dark_gray>[<gradient:red:gold>BasaltRanks</gradient>]</dark_gray> <gray>"
        ) ?: ""
    }

    fun get(key: String, vararg replacements: Pair<String, String>): Component {
        var message = config.getString(key, key) ?: key
        message = message.replace("{prefix}", prefix)
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value)
        }
        return mm.deserialize(message)
    }

    fun format(message: String): Component = mm.deserialize(message)

    fun success(message: String): Component = mm.deserialize("$prefix<green>$message")

    fun error(message: String): Component = mm.deserialize("$prefix<red>$message")

    fun info(message: String): Component = mm.deserialize("$prefix<gray>$message")

    fun playersOnly(): Component = get("players-only")
}
