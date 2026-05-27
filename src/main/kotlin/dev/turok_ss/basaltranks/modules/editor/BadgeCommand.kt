package dev.turok_ss.basaltranks.modules.editor

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import dev.turok_ss.basaltranks.modules.badge.BadgeRenderer
import dev.turok_ss.basaltranks.modules.badge.BadgeTemplate
import dev.turok_ss.basaltranks.utils.FoliaCompat
import dev.turok_ss.basaltranks.utils.GUIBuilder
import dev.turok_ss.basaltranks.utils.Messages
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Main /badge command handler with all subcommands + tab completion.
 * Delegates GUI operations to EditorModule's TemplateGUI and EditorGUI.
 */
class BadgeCommand(
    private val plugin: BasaltRanks,
    private val editorModule: EditorModule
) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "delete" -> handleDelete(sender, args)
            "list" -> handleList(sender)
            "assign" -> handleAssign(sender, args)
            "remove" -> handleRemove(sender, args)
            "edit" -> handleEdit(sender, args)
            "templates" -> handleTemplates(sender, args)
            "reload" -> handleReload(sender)
            "preview" -> handlePreview(sender, args)
            "info" -> handleInfo(sender, args)
            "render" -> handleRender(sender, args)
            "web" -> handleWeb(sender, args)
            "animate" -> handleAnimate(sender, args)
            else -> sendUsage(sender)
        }
        return true
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Messages.info("Badge commands:"))
        sender.sendMessage(Messages.format("  <white>/badge list <gray>- List all badges"))
        if (sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.format("  <white>/badge create <id> [template] <gray>- Create a badge"))
            sender.sendMessage(Messages.format("  <white>/badge edit <id> <gray>- Open editor GUI"))
            sender.sendMessage(Messages.format("  <white>/badge delete <id> <gray>- Delete a badge"))
            sender.sendMessage(Messages.format("  <white>/badge assign <player> <badge> [prefix|suffix] <gray>- Assign badge"))
            sender.sendMessage(Messages.format("  <white>/badge remove <player> [prefix|suffix] <gray>- Remove badge"))
            sender.sendMessage(Messages.format("  <white>/badge templates <gray>- Browse templates"))
            sender.sendMessage(Messages.format("  <white>/badge preview <id> <gray>- Preview badge"))
            sender.sendMessage(Messages.format("  <white>/badge info <id> <gray>- Badge details"))
            sender.sendMessage(Messages.format("  <white>/badge render <id|all> <gray>- Render to PNG"))
            sender.sendMessage(Messages.format("  <white>/badge web [on|off] <gray>- Web editor toggle"))
            sender.sendMessage(Messages.format("  <white>/badge animate <id> <type|off> [speed] [frames] <gray>- Animation"))
            sender.sendMessage(Messages.format("  <white>/badge reload <gray>- Reload config"))
        }
    }

    // ─────────────────────────────────────────────
    // /badge create <id> <text> <bg> <textcolor> [gradient] [border]
    // /badge create <id> --template <template_id> [priority]
    // /badge create <id> [template]  (simplified)
    // ─────────────────────────────────────────────

    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.error("Usage: /badge create <id> [template]"))
            sender.sendMessage(Messages.error("   or: /badge create <id> <text> <bg> <textcolor> [gradient] [border]"))
            return
        }

        val id = args[1].lowercase()

        // Validate badge ID format
        if (id.length > 32) {
            sender.sendMessage(Messages.error("Badge ID must be 32 characters or less."))
            return
        }
        if (!id.matches(Regex("^[a-z0-9_-]+$"))) {
            sender.sendMessage(Messages.error("Badge ID can only contain lowercase letters, numbers, hyphens, and underscores."))
            return
        }

        // Check for template shorthand: /badge create <id> <template>
        if (args.size == 3 && !args[2].startsWith("#") && args[2] != "--template") {
            val templateId = args[2]
            val badge = badgeModule.createFromTemplate(templateId, id)
            if (badge == null) {
                val template = BadgeTemplate.getById(templateId)
                if (template == null) {
                    sender.sendMessage(Messages.error("Template '$templateId' not found. Use /badge templates"))
                } else {
                    sender.sendMessage(Messages.get("badge-exists", "id" to id))
                }
                return
            }
            badgeModule.renderAndSave(badge.id)
            sender.sendMessage(Messages.get("badge-created", "id" to badge.id))
            sender.sendMessage(Messages.format("  <gray>Unicode: <white>${badge.unicodeHex}"))
            if (sender is Player) GUIBuilder.playSuccessSound(sender)
            return
        }

        // Check for template flag: /badge create <id> --template <template_id> [priority]
        if (args.size >= 4 && args[2] == "--template") {
            val templateId = args[3]
            val priority = if (args.size >= 5) args[4].toIntOrNull() ?: 50 else 50
            val badge = badgeModule.createFromTemplate(templateId, id, priority)
            if (badge == null) {
                val template = BadgeTemplate.getById(templateId)
                if (template == null) {
                    sender.sendMessage(Messages.error("Template '$templateId' not found. Use /badge templates"))
                } else {
                    sender.sendMessage(Messages.get("badge-exists", "id" to id))
                }
                return
            }
            badgeModule.renderAndSave(badge.id)
            sender.sendMessage(Messages.get("badge-created", "id" to badge.id))
            sender.sendMessage(Messages.format("  <gray>Unicode: <white>${badge.unicodeHex}"))
            if (sender is Player) GUIBuilder.playSuccessSound(sender)
            return
        }

        // Manual creation: /badge create <id> <text> <bg> <textcolor> [gradient] [border] [style]
        if (args.size < 5) {
            sender.sendMessage(Messages.error("Usage: /badge create <id> <text> <bg> <textcolor> [gradient] [border] [style]"))
            return
        }

        val displayText = args[2]
        val bgColor = args[3]
        val textColor = args[4]
        val gradientColor = if (args.size >= 6 && args[5] != "none") args[5] else null
        val borderColor = if (args.size >= 7 && args[6] != "none") args[6] else null
        val style = if (args.size >= 8) args[7] else "flat"

        if (!badgeModule.isValidHexColor(bgColor) || !badgeModule.isValidHexColor(textColor)) {
            sender.sendMessage(Messages.error("Colors must be in hex format: #RRGGBB"))
            return
        }
        if (gradientColor != null && !badgeModule.isValidHexColor(gradientColor)) {
            sender.sendMessage(Messages.error("Gradient color must be in hex format: #RRGGBB"))
            return
        }
        if (borderColor != null && !badgeModule.isValidHexColor(borderColor)) {
            sender.sendMessage(Messages.error("Border color must be in hex format: #RRGGBB"))
            return
        }

        if (displayText.length > 16) {
            sender.sendMessage(Messages.error("Display text must be 16 characters or less."))
            return
        }

        val badge = badgeModule.createBadge(
            id = id,
            displayText = displayText,
            bgColor = bgColor,
            textColor = textColor,
            gradientColor = gradientColor,
            borderColor = borderColor,
            borderRadius = 1,
            priority = 50,
            style = style
        )

        if (badge == null) {
            sender.sendMessage(Messages.get("badge-exists", "id" to id))
            return
        }

        badgeModule.renderAndSave(badge.id)
        sender.sendMessage(Messages.get("badge-created", "id" to badge.id))
        sender.sendMessage(Messages.format("  <gray>Unicode: <white>${badge.unicodeHex}"))
        if (sender is Player) GUIBuilder.playSuccessSound(sender)
    }

    // ─────────────────────────────────────────────
    // /badge delete <id>
    // ─────────────────────────────────────────────

    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge delete <id>"))
            return
        }

        val id = args[1].lowercase()

        // Verify badge exists before proceeding
        val badge = badgeModule.getBadge(id)
        if (badge == null) {
            sender.sendMessage(Messages.get("badge-not-found", "id" to id))
            return
        }

        // Confirmation: require explicit "confirm" when badge is assigned to players
        val assigned = badgeModule.getPlayerAssignmentCount(id)
        if (assigned > 0 && (args.size < 3 || !args[2].equals("confirm", ignoreCase = true))) {
            sender.sendMessage(Messages.get("editor-delete-confirm",
                "id" to id, "count" to assigned.toString()))
            return
        }

        if (badgeModule.deleteBadge(id)) {
            sender.sendMessage(Messages.get("badge-deleted", "id" to id))
            if (sender is Player) {
                sender.playSound(sender.location, Sound.BLOCK_ANVIL_BREAK, 0.5f, 1.2f)
            }
        } else {
            sender.sendMessage(Messages.get("badge-not-found", "id" to id))
        }
    }

    // ─────────────────────────────────────────────
    // /badge list
    // ─────────────────────────────────────────────

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("basaltranks.list")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        val allBadges = badgeModule.getAllBadges()
        if (allBadges.isEmpty()) {
            sender.sendMessage(Messages.get("badge-list-empty"))
            return
        }

        sender.sendMessage(Messages.get("badge-list-header", "count" to allBadges.size.toString()))
        for (badge in allBadges) {
            val assignedCount = badgeModule.getPlayerAssignmentCount(badge.id)

            val hoverLines = mutableListOf<String>()
            hoverLines.add("<gold><bold>${badge.displayText}</bold></gold>")
            hoverLines.add("<dark_gray><strikethrough>          </strikethrough>")
            hoverLines.add("<gray>ID: <white>${badge.id}")
            hoverLines.add("<gray>Style: <white>${badge.style.uppercase()}")
            hoverLines.add("<gray>Priority: <white>${badge.priority}")
            hoverLines.add("<gray>Background: <white>${badge.bgColor}")
            hoverLines.add("<gray>Text Color: <white>${badge.textColor}")
            if (badge.gradientColor != null) hoverLines.add("<gray>Gradient: <white>${badge.gradientColor}")
            if (badge.borderColor != null) hoverLines.add("<gray>Border: <white>${badge.borderColor}")
            hoverLines.add("<gray>Radius: <white>${badge.borderRadius}")
            hoverLines.add("<gray>Unicode: <white>${badge.unicodeHex}")
            if (badge.animated) {
                hoverLines.add("<gray>Animation: <green>${badge.animationType.uppercase()} <gray>(${badge.animationFrames}f, ${badge.animationSpeed}t)")
            }
            hoverLines.add("<gray>Assigned to: <white>$assignedCount players")
            hoverLines.add("<dark_gray><strikethrough>          </strikethrough>")

            val isAdmin = sender.hasPermission("basaltranks.admin")
            val clickCmd = if (isAdmin) "/badge edit ${badge.id}" else "/badge info ${badge.id}"
            val clickTag = if (isAdmin) "run_command" else "suggest_command"
            val clickHint = if (isAdmin) "<yellow>Click to edit" else "<yellow>Click for details"
            hoverLines.add(clickHint)

            val hoverText = hoverLines.joinToString("<newline>").replace("'", "\\'")

            val animTag = if (badge.animated) " <gold>⚡${badge.animationType.uppercase()}" else ""
            sender.sendMessage(Messages.format(
                "  <gray>- <hover:show_text:'$hoverText'><click:$clickTag:'$clickCmd'><white>${badge.id}</white> <dark_gray>\"${badge.displayText}\" <gray>[${badge.unicodeHex}] <dark_gray>${badge.style.uppercase()} P:${badge.priority}$animTag</click></hover>"
            ))
        }
    }

    // ─────────────────────────────────────────────
    // /badge assign <player> <badge>
    // ─────────────────────────────────────────────

    private fun handleAssign(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge assign <player> <badge> [prefix|suffix]"))
            return
        }

        val playerName = args[1]
        val badgeId = args[2].lowercase()
        val slot = if (args.size >= 4) args[3].lowercase() else "prefix"

        if (slot != "prefix" && slot != "suffix") {
            sender.sendMessage(Messages.error("Slot must be 'prefix' or 'suffix'."))
            return
        }

        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        val target = onlinePlayer ?: Bukkit.getOfflinePlayerIfCached(playerName)
        if (target == null) {
            sender.sendMessage(Messages.get("player-not-found", "player" to playerName))
            return
        }

        if (badgeModule.assignBadge(target.uniqueId, badgeId, slot)) {
            val msgKey = if (slot == "suffix") "badge-assigned-suffix" else "badge-assigned"
            sender.sendMessage(Messages.get(msgKey, "badge" to badgeId, "player" to playerName))
            if (sender is Player) GUIBuilder.playSuccessSound(sender)
            if (onlinePlayer != null) {
                val badge = badgeModule.getBadge(badgeId)
                val slotLabel = if (slot == "suffix") "suffix" else ""
                onlinePlayer.sendMessage(Messages.format(
                    "<gradient:gold:yellow>★</gradient> <gray>You received the <white>${badge?.displayText ?: badgeId}</white> <gray>${if (slotLabel.isNotEmpty()) "$slotLabel " else ""}badge!"
                ))
                val titleTimes = Title.Times.times(
                    Duration.ofMillis(200),
                    Duration.ofMillis(2000),
                    Duration.ofMillis(500)
                )
                onlinePlayer.showTitle(Title.title(
                    mm.deserialize("<gradient:gold:yellow>New Badge!</gradient>"),
                    mm.deserialize("<gray>${badge?.displayText ?: badgeId}"),
                    titleTimes
                ))
                onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f)
                plugin.getDisplayModule()?.updatePlayerDisplay(onlinePlayer)
            }
        } else {
            sender.sendMessage(Messages.get("badge-not-found", "id" to badgeId))
        }
    }

    // ─────────────────────────────────────────────
    // /badge remove <player>
    // ─────────────────────────────────────────────

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge remove <player> [prefix|suffix]"))
            return
        }

        val playerName = args[1]
        val slot = if (args.size >= 3) args[2].lowercase() else null

        if (slot != null && slot != "prefix" && slot != "suffix") {
            sender.sendMessage(Messages.error("Slot must be 'prefix' or 'suffix'. Omit to remove both."))
            return
        }

        val onlinePlayer = Bukkit.getPlayerExact(playerName)
        val target = onlinePlayer ?: Bukkit.getOfflinePlayerIfCached(playerName)
        if (target == null) {
            sender.sendMessage(Messages.get("player-not-found", "player" to playerName))
            return
        }

        if (badgeModule.removeBadge(target.uniqueId, slot)) {
            val msgKey = when (slot) {
                "prefix" -> "badge-removed-prefix"
                "suffix" -> "badge-removed-suffix"
                else -> "badge-removed-all"
            }
            sender.sendMessage(Messages.get(msgKey, "player" to playerName))
            if (sender is Player) sender.playSound(sender.location, Sound.ENTITY_ITEM_BREAK, 0.6f, 1.0f)
            if (onlinePlayer != null) {
                val label = when (slot) {
                    "prefix" -> "prefix badge"
                    "suffix" -> "suffix badge"
                    else -> "badges"
                }
                onlinePlayer.sendMessage(Messages.format("<gray>Your $label has been removed."))
                onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f)
                val hasAnyBadge = badgeModule.getPlayerBadge(target.uniqueId) != null ||
                    badgeModule.getPlayerSuffixBadge(target.uniqueId) != null
                if (hasAnyBadge) {
                    plugin.getDisplayModule()?.updatePlayerDisplay(onlinePlayer)
                } else {
                    plugin.getDisplayModule()?.removePlayerDisplay(onlinePlayer)
                }
            }
        } else {
            sender.sendMessage(Messages.get("editor-no-badge", "player" to playerName))
        }
    }

    // ─────────────────────────────────────────────
    // /badge edit <id>
    // ─────────────────────────────────────────────

    private fun handleEdit(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(Messages.playersOnly())
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge edit <id>"))
            return
        }

        val id = args[1].lowercase()
        val badge = badgeModule.getBadge(id)
        if (badge == null) {
            sender.sendMessage(Messages.get("badge-not-found", "id" to id))
            return
        }

        editorModule.openEditor(sender, id)
    }

    // ─────────────────────────────────────────────
    // /badge templates [category]
    // ─────────────────────────────────────────────

    private fun handleTemplates(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        // If sender is a player, open the GUI
        if (sender is Player) {
            editorModule.openTemplates(sender)
            return
        }

        // Console fallback: text-based template listing
        if (args.size >= 2) {
            val category = args[1]
            val templates = BadgeTemplate.getByCategory(category)
            if (templates.isEmpty()) {
                sender.sendMessage(Messages.error("No templates in category '$category'."))
                sender.sendMessage(Messages.info("Categories: ${BadgeTemplate.getCategories().joinToString(", ")}"))
                return
            }
            sender.sendMessage(Messages.info("Templates in '$category' (${templates.size}):"))
            for (t in templates) {
                sender.sendMessage(Messages.format(
                    "  <gray>- <white>${t.id}</white> <dark_gray>\"${t.displayText}\" <gray>[${t.bgColor}]"
                ))
            }
        } else {
            val categories = BadgeTemplate.getCategories()
            val total = BadgeTemplate.getAll().size
            sender.sendMessage(Messages.info("Badge templates ($total total):"))
            for (cat in categories) {
                val count = BadgeTemplate.getByCategory(cat).size
                sender.sendMessage(Messages.format("  <white>$cat</white> <gray>($count templates)"))
            }
            sender.sendMessage(Messages.format("  <gray>Use <white>/badge templates <category></white> to view."))
        }
    }

    // ─────────────────────────────────────────────
    // /badge info <id>
    // ─────────────────────────────────────────────

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.list")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge info <id>"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        val badge = badgeModule.getBadge(args[1])
        if (badge == null) {
            sender.sendMessage(Messages.get("badge-not-found", "id" to args[1]))
            return
        }

        val dims = BadgeRenderer.calculateDimensions(badge)
        val assignedCount = badgeModule.getPlayerAssignmentCount(badge.id)

        sender.sendMessage(Messages.info("Badge info: <white>${badge.id}"))
        sender.sendMessage(Messages.format("  <gray>Text: <white>${badge.displayText}"))
        sender.sendMessage(Messages.format("  <gray>BG: <white>${badge.bgColor}${badge.gradientColor?.let { " -> $it" } ?: ""}"))
        sender.sendMessage(Messages.format("  <gray>Text color: <white>${badge.textColor}"))
        sender.sendMessage(Messages.format("  <gray>Border: <white>${badge.borderColor ?: "none"}"))
        sender.sendMessage(Messages.format("  <gray>Radius: <white>${badge.borderRadius}"))
        sender.sendMessage(Messages.format("  <gray>Style: <white>${badge.style.uppercase()}"))
        sender.sendMessage(Messages.format("  <gray>Priority: <white>${badge.priority}"))
        sender.sendMessage(Messages.format("  <gray>Unicode: <white>${badge.unicodeHex}"))
        if (badge.animated) {
            sender.sendMessage(Messages.format("  <gray>Animation: <green>${badge.animationType.uppercase()} <gray>(${badge.animationFrames} frames, ${badge.animationSpeed}t speed)"))
        } else {
            sender.sendMessage(Messages.format("  <gray>Animation: <white>none"))
        }
        sender.sendMessage(Messages.format("  <gray>Size: <white>${dims.first}x${dims.second}px"))
        sender.sendMessage(Messages.format("  <gray>Assigned to: <white>$assignedCount players <gray>(prefix + suffix)"))
    }

    // ─────────────────────────────────────────────
    // /badge render <id|all>
    // ─────────────────────────────────────────────

    private fun handleRender(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge render <id|all>"))
            return
        }

        if (args[1].equals("all", ignoreCase = true)) {
            FoliaCompat.runAsync(plugin, Runnable {
                val count = badgeModule.renderAll()
                FoliaCompat.runTask(plugin, Runnable {
                    sender.sendMessage(Messages.success("Rendered $count badges to plugins/BasaltRanks/rendered/"))
                })
            })
        } else {
            val id = args[1].lowercase()
            FoliaCompat.runAsync(plugin, Runnable {
                val file = badgeModule.renderAndSave(id)
                FoliaCompat.runTask(plugin, Runnable {
                    if (file != null) {
                        sender.sendMessage(Messages.success("Rendered badge '$id' -> ${file.name}"))
                    } else {
                        sender.sendMessage(Messages.get("badge-not-found", "id" to id))
                    }
                })
            })
        }
    }

    // ─────────────────────────────────────────────
    // /badge preview <id>
    // ─────────────────────────────────────────────

    private fun handlePreview(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Messages.get("invalid-usage", "usage" to "/badge preview <id>"))
            return
        }

        val badge = badgeModule.getBadge(args[1])
        if (badge == null) {
            sender.sendMessage(Messages.get("badge-not-found", "id" to args[1]))
            return
        }

        val dims = BadgeRenderer.calculateDimensions(badge)
        sender.sendMessage(Messages.info("Preview of '${badge.id}':"))
        sender.sendMessage(Messages.format(
            "  <${badge.bgColor}><bold>${badge.displayText}</bold></${badge.bgColor}>"
        ))
        sender.sendMessage(Messages.format(
            "  <gray>Would render as <white>${dims.first}x${dims.second}px</white> image"
        ))
        if (sender is Player) {
            sender.sendMessage(Messages.format(
                "  <gray>Use <white>/badge render ${badge.id}</white> to generate PNG"
            ))
        }
    }

    // ─────────────────────────────────────────────
    // /badge web [on|off]
    // ─────────────────────────────────────────────

    private fun handleWeb(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val webModule = plugin.getWebModule()
        if (webModule == null) {
            sender.sendMessage(Messages.error("Web module is not enabled in config."))
            return
        }

        // /badge web — show status
        if (args.size < 2) {
            showWebStatus(sender, webModule)
            return
        }

        when (args[1].lowercase()) {
            "on" -> handleWebOn(sender, webModule)
            "off" -> handleWebOff(sender, webModule)
            else -> showWebStatus(sender, webModule)
        }
    }

    private fun handleWebOn(sender: CommandSender, webModule: dev.turok_ss.basaltranks.modules.web.WebModule) {
        if (webModule.isWebActive()) {
            sender.sendMessage(Messages.get("web-already-on"))
            return
        }

        webModule.enableWeb()
        sender.sendMessage(Messages.get("web-enabled", "url" to webModule.getWebUrl()))
    }

    private fun handleWebOff(sender: CommandSender, webModule: dev.turok_ss.basaltranks.modules.web.WebModule) {
        if (!webModule.isWebActive()) {
            sender.sendMessage(Messages.get("web-already-off"))
            return
        }

        webModule.disableWeb()
        sender.sendMessage(Messages.get("web-disabled"))
    }

    private fun showWebStatus(sender: CommandSender, webModule: dev.turok_ss.basaltranks.modules.web.WebModule) {
        sender.sendMessage(Messages.get("web-status-header"))

        if (webModule.isWebActive()) {
            sender.sendMessage(Messages.get("web-status-on"))
        } else {
            sender.sendMessage(Messages.get("web-status-off"))
        }

        sender.sendMessage(Messages.get("web-status-url", "url" to webModule.getWebUrl()))
        sender.sendMessage(Messages.get("web-status-toggle"))
    }

    // ─────────────────────────────────────────────
    // /badge animate <id> <type|off> [speed] [frames]
    // ─────────────────────────────────────────────

    private fun handleAnimate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sender.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(Messages.error("Usage: /badge animate <id> <type|off> [speed] [frames]"))
            sender.sendMessage(Messages.format("  <gray>Types: <white>rainbow, pulse, glow, gradient_shift"))
            sender.sendMessage(Messages.format("  <gray>Speed: <white>5-40 ticks <gray>(default 10, lower=faster)"))
            sender.sendMessage(Messages.format("  <gray>Frames: <white>2-8 <gray>(default 6)"))
            return
        }

        val id = args[1].lowercase()
        val typeOrOff = args[2].lowercase()

        if (badgeModule.getBadge(id) == null) {
            sender.sendMessage(Messages.get("badge-not-found", "id" to id))
            return
        }

        if (typeOrOff == "off" || typeOrOff == "none") {
            badgeModule.updateBadge(id, animated = false, animationType = "none")
            plugin.getDisplayModule()?.animationScheduler?.resetBadge(id)
            sender.sendMessage(Messages.get("animation-disabled", "id" to id))
            if (sender is Player) GUIBuilder.playClickSound(sender as Player)
            FoliaCompat.runAsync(plugin, Runnable {
                badgeModule.renderAndSave(id)
                FoliaCompat.runTask(plugin, Runnable {
                    plugin.getResourcePackModule()?.regeneratePack()
                    plugin.getDisplayModule()?.updateAllDisplays()
                })
            })
            return
        }

        // Validate animation type
        val animType = Badge.normalizeAnimation(typeOrOff)
        if (animType == "none") {
            sender.sendMessage(Messages.error("Invalid animation type: $typeOrOff"))
            sender.sendMessage(Messages.format("  <gray>Types: <white>rainbow, pulse, glow, gradient_shift"))
            return
        }

        val speed = if (args.size >= 4) args[3].toIntOrNull()?.coerceIn(5, 40) ?: 10 else 10
        val frames = if (args.size >= 5) args[4].toIntOrNull()?.coerceIn(2, 8) ?: 6 else 6

        badgeModule.updateBadge(
            id = id,
            animated = true,
            animationType = animType,
            animationSpeed = speed,
            animationFrames = frames
        )
        plugin.getDisplayModule()?.animationScheduler?.resetBadge(id)

        sender.sendMessage(Messages.get("animation-enabled",
            "type" to animType,
            "id" to id,
            "frames" to frames.toString(),
            "speed" to speed.toString()
        ))
        if (sender is Player) GUIBuilder.playSuccessSound(sender as Player)

        // Render frames and regenerate pack async
        FoliaCompat.runAsync(plugin, Runnable {
            badgeModule.renderAndSave(id)
            FoliaCompat.runTask(plugin, Runnable {
                plugin.getResourcePackModule()?.regeneratePack()
                plugin.getDisplayModule()?.updateAllDisplays()
            })
        })
    }

    // ─────────────────────────────────────────────
    // /badge reload
    // ─────────────────────────────────────────────

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("basaltranks.admin")) {
            sender.sendMessage(Messages.get("no-permission"))
            return
        }

        plugin.reloadConfig()
        Messages.load(plugin)

        val badgeModule = plugin.getBadgeModule()
        badgeModule?.reloadBadges()

        // Regenerate resource pack
        val resourcePackModule = plugin.getResourcePackModule()
        resourcePackModule?.regeneratePack()

        val count = badgeModule?.getAllBadges()?.size ?: 0
        sender.sendMessage(Messages.get("config-reloaded"))
        sender.sendMessage(Messages.success("Reloaded $count badges + resource pack"))
        if (sender is Player) GUIBuilder.playSuccessSound(sender)
    }

    // ─────────────────────────────────────────────
    // Tab completion
    // ─────────────────────────────────────────────

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        val badgeModule = plugin.getBadgeModule()

        return when (args.size) {
            1 -> {
                val subcommands = mutableListOf("list", "info", "preview")
                if (sender.hasPermission("basaltranks.admin")) {
                    subcommands.addAll(listOf("create", "edit", "delete", "assign", "remove", "templates", "render", "web", "animate", "reload"))
                }
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> when (args[0].lowercase()) {
                "edit", "delete", "info", "preview", "animate" ->
                    badgeModule?.getBadgeIds()?.filter { it.startsWith(args[1].lowercase()) } ?: emptyList()
                "render" ->
                    ((badgeModule?.getBadgeIds() ?: emptyList()) + "all").filter { it.startsWith(args[1].lowercase()) }
                "assign", "remove" ->
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) }
                "templates" ->
                    BadgeTemplate.getCategories().filter { it.startsWith(args[1].lowercase()) }
                "create" -> listOf("<id>")
                "web" -> listOf("on", "off").filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "assign" ->
                    badgeModule?.getBadgeIds()?.filter { it.startsWith(args[2].lowercase()) } ?: emptyList()
                "remove" ->
                    listOf("prefix", "suffix").filter { it.startsWith(args[2].lowercase()) }
                "animate" ->
                    (Badge.VALID_ANIMATIONS.filter { it != "none" } + "off").filter { it.startsWith(args[2].lowercase()) }.toList()
                "create" -> {
                    val templateIds = BadgeTemplate.getAll().map { it.id }
                    (templateIds + listOf("--template", "<text>")).filter { it.startsWith(args[2].lowercase()) }
                }
                "delete" -> listOf("confirm").filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "assign" ->
                    listOf("prefix", "suffix").filter { it.startsWith(args[3].lowercase()) }
                "animate" -> listOf("5", "10", "15", "20", "30", "40").filter { it.startsWith(args[3]) }
                "create" -> if (args[2] == "--template") {
                    BadgeTemplate.getAll().map { it.id }.filter { it.startsWith(args[3].lowercase()) }
                } else {
                    listOf("#RRGGBB")
                }
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "animate" -> listOf("2", "3", "4", "5", "6", "7", "8").filter { it.startsWith(args[4]) }
                "create" -> if (args[2] != "--template") listOf("#RRGGBB") else emptyList()
                else -> emptyList()
            }
            6 -> when (args[0].lowercase()) {
                "create" -> if (args[2] != "--template") listOf("#RRGGBB", "none") else emptyList()
                else -> emptyList()
            }
            7 -> when (args[0].lowercase()) {
                "create" -> if (args[2] != "--template") listOf("#RRGGBB", "none") else emptyList()
                else -> emptyList()
            }
            8 -> when (args[0].lowercase()) {
                "create" -> if (args[2] != "--template") {
                    Badge.VALID_STYLES.filter { it.startsWith(args[7].lowercase()) }.toList()
                } else emptyList()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
