package dev.turok_ss.basaltranks.commands

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.utils.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class BasaltRanksCommand(private val plugin: BasaltRanks) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendVersionPanel(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("basaltranks.admin")) {
                    sender.sendMessage(Messages.get("no-permission"))
                    return true
                }
                plugin.reloadConfig()
                Messages.load(plugin)
                sender.sendMessage(Messages.get("config-reloaded"))
            }
            "version" -> {
                sendVersionPanel(sender)
            }
            else -> {
                sender.sendMessage(Messages.get("invalid-usage", "usage" to "/basaltranks <reload|version>"))
            }
        }
        return true
    }

    private fun sendVersionPanel(sender: CommandSender) {
        val version = plugin.pluginMeta.version
        val badgeCount = plugin.getBadgeModule()?.getAllBadges()?.size ?: 0
        val modules = plugin.moduleManager.count()
        val lp = plugin.getLuckPermsModule()?.isLuckPermsAvailable() == true
        val web = plugin.getWebModule()?.isWebActive() == true
        val lpStatus = if (lp) "<green>Active" else "<red>Inactive"
        val webStatus = if (web) "<green>Active" else "<red>Inactive"

        sender.sendMessage(Messages.format(""))
        sender.sendMessage(Messages.format("<dark_gray><strikethrough>      </strikethrough> <gradient:gold:yellow>BasaltRanks</gradient> <dark_gray><strikethrough>      </strikethrough>"))
        sender.sendMessage(Messages.format("  <gray>Version: <white>$version"))
        sender.sendMessage(Messages.format("  <gray>Author: <white>Turok_SS <dark_gray>(Basalt)"))
        sender.sendMessage(Messages.format("  <gray>Modules: <white>$modules active"))
        sender.sendMessage(Messages.format("  <gray>Badges: <white>$badgeCount loaded"))
        sender.sendMessage(Messages.format("  <gray>LuckPerms: $lpStatus"))
        sender.sendMessage(Messages.format("  <gray>Web Editor: $webStatus"))
        sender.sendMessage(Messages.format("<dark_gray><strikethrough>                            </strikethrough>"))
        sender.sendMessage(Messages.format(""))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val subcommands = mutableListOf("version")
            if (sender.hasPermission("basaltranks.admin")) {
                subcommands.add("reload")
            }
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
