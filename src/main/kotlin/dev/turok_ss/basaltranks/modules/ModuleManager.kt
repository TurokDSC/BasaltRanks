package dev.turok_ss.basaltranks.modules

import dev.turok_ss.basaltranks.BasaltRanks
import org.bukkit.event.HandlerList

class ModuleManager(private val plugin: BasaltRanks) {
    private val modules = mutableMapOf<String, Module>()
    private val enabledModules = mutableSetOf<String>()

    fun register(module: Module) {
        modules[module.name] = module
    }

    fun enableAll() {
        val config = plugin.config
        modules.forEach { (name, module) ->
            val enabled = config.getBoolean("modules.${module.configKey}", true)
            if (enabled) {
                try {
                    module.onEnable()
                    enabledModules.add(name)
                    plugin.logger.info("Module '$name' enabled")
                } catch (e: Exception) {
                    plugin.slF4JLogger.error("Module '$name' failed to enable", e)
                }
            } else {
                plugin.logger.info("Module '$name' disabled by config")
            }
        }
    }

    fun disableAll() {
        // Only disable modules that were successfully enabled, in reverse order
        modules.entries.reversed().forEach { (name, module) ->
            if (name in enabledModules) {
                try {
                    module.onDisable()
                } catch (e: Exception) {
                    plugin.slF4JLogger.error("Module '$name' failed to disable", e)
                }
            }
        }
        HandlerList.unregisterAll(plugin as org.bukkit.plugin.Plugin)
        enabledModules.clear()
    }

    fun get(name: String): Module? = modules[name]

    fun isEnabled(name: String): Boolean = name in enabledModules

    fun count(): Int = enabledModules.size
}
