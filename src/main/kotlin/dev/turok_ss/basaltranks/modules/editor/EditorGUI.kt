package dev.turok_ss.basaltranks.modules.editor

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import dev.turok_ss.basaltranks.utils.GUIBuilder
import dev.turok_ss.basaltranks.utils.Messages
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import dev.turok_ss.basaltranks.utils.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Badge property editor GUI with color picker sub-GUI and chat input handling.
 *
 * Provides a 5-row visual editor where admins can modify badge properties
 * (text, colors, border, priority) and save with resource pack regeneration.
 */
class EditorGUI(private val plugin: BasaltRanks) : Listener {

    companion object {
        const val EDITOR_TITLE_PREFIX = "BasaltRanks | Edit: "
        const val COLOR_PICKER_TITLE = "BasaltRanks | Color Picker"

        // Editor GUI slots
        private const val SLOT_PREVIEW = 4
        private const val SLOT_DISPLAY_TEXT = 10
        private const val SLOT_BG_COLOR = 12
        private const val SLOT_TEXT_COLOR = 14
        private const val SLOT_GRADIENT_COLOR = 16
        private const val SLOT_BORDER_COLOR = 20
        private const val SLOT_BORDER_RADIUS = 22
        private const val SLOT_PRIORITY = 24
        private const val SLOT_STYLE = 29
        private const val SLOT_SAVE = 31
        private const val SLOT_CANCEL = 33
        private const val SLOT_ANIMATION_TYPE = 28
        private const val SLOT_ANIMATION_SPEED = 30
        private const val SLOT_ANIMATION_FRAMES = 32

        // NamespacedKeys for PersistentDataContainer
        private val KEY_ACTION by lazy { NamespacedKey(BasaltRanks.instance, "editor_action") }
        private val KEY_COLOR_HEX by lazy { NamespacedKey(BasaltRanks.instance, "color_hex") }
        private val KEY_COLOR_PROPERTY by lazy { NamespacedKey(BasaltRanks.instance, "color_property") }

        /** Predefined color palette matching Minecraft chat colors */
        val COLOR_PALETTE: List<ColorEntry> = listOf(
            ColorEntry("Black", Material.BLACK_CONCRETE, "#000000"),
            ColorEntry("Dark Blue", Material.BLUE_CONCRETE, "#0000AA"),
            ColorEntry("Dark Green", Material.GREEN_CONCRETE, "#00AA00"),
            ColorEntry("Dark Aqua", Material.CYAN_CONCRETE, "#00AAAA"),
            ColorEntry("Dark Red", Material.RED_CONCRETE, "#AA0000"),
            ColorEntry("Dark Purple", Material.PURPLE_CONCRETE, "#AA00AA"),
            ColorEntry("Gold", Material.ORANGE_CONCRETE, "#FFAA00"),
            ColorEntry("Gray", Material.LIGHT_GRAY_CONCRETE, "#AAAAAA"),
            ColorEntry("Dark Gray", Material.GRAY_CONCRETE, "#555555"),
            ColorEntry("Blue", Material.LIGHT_BLUE_CONCRETE, "#5555FF"),
            ColorEntry("Green", Material.LIME_CONCRETE, "#55FF55"),
            ColorEntry("Aqua", Material.CYAN_WOOL, "#55FFFF"),
            ColorEntry("Red", Material.PINK_CONCRETE, "#FF5555"),
            ColorEntry("Light Purple", Material.MAGENTA_CONCRETE, "#FF55FF"),
            ColorEntry("Yellow", Material.YELLOW_CONCRETE, "#FFFF55"),
            ColorEntry("White", Material.WHITE_CONCRETE, "#FFFFFF")
        )
    }

    data class ColorEntry(val name: String, val material: Material, val hex: String)

    /**
     * Working copy of a badge being edited, so changes are not committed until "Save".
     */
    data class EditorSession(
        val badgeId: String,
        var displayText: String,
        var bgColor: String,
        var textColor: String,
        var gradientColor: String?,
        var borderColor: String?,
        var borderRadius: Int,
        var priority: Int,
        var style: String = "flat",
        var animated: Boolean = false,
        var animationType: String = "none",
        var animationSpeed: Int = 10,
        var animationFrames: Int = 6
    ) {
        companion object {
            fun fromBadge(badge: Badge): EditorSession = EditorSession(
                badgeId = badge.id,
                displayText = badge.displayText,
                bgColor = badge.bgColor,
                textColor = badge.textColor,
                gradientColor = badge.gradientColor,
                borderColor = badge.borderColor,
                borderRadius = badge.borderRadius,
                priority = badge.priority,
                style = badge.style,
                animated = badge.animated,
                animationType = badge.animationType,
                animationSpeed = badge.animationSpeed,
                animationFrames = badge.animationFrames
            )
        }
    }

    /**
     * Represents a pending chat input from the player.
     * @property badgeId The badge being edited
     * @property property The property being changed ("display-text" or a color property name)
     */
    data class PendingEdit(val badgeId: String, val property: String)

    /** Active editor sessions keyed by player UUID */
    private val sessions = ConcurrentHashMap<UUID, EditorSession>()

    /** Pending chat inputs keyed by player UUID */
    private val pendingTextEdits = ConcurrentHashMap<UUID, PendingEdit>()

    /** Tracks which color property is being picked, keyed by player UUID */
    private val colorPickerProperty = ConcurrentHashMap<UUID, String>()

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * Opens the editor GUI for a badge.
     */
    fun open(player: Player, badgeId: String) {
        val badgeModule = plugin.getBadgeModule() ?: return
        val badge = badgeModule.getBadge(badgeId) ?: return

        val session = EditorSession.fromBadge(badge)
        sessions[player.uniqueId] = session

        val inv = buildEditorInventory(session)
        player.openInventory(inv)
        GUIBuilder.playOpenSound(player)
    }

    /**
     * Reopens the editor GUI for the player's current session.
     */
    fun reopen(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val inv = buildEditorInventory(session)
        player.openInventory(inv)
    }

    // ─────────────────────────────────────────────
    // Editor inventory builder
    // ─────────────────────────────────────────────

    private fun buildEditorInventory(session: EditorSession): Inventory {
        val inv = GUIBuilder.create("${EDITOR_TITLE_PREFIX}${session.badgeId}", 5)
        GUIBuilder.fillBorders(inv)

        // Preview item (slot 4)
        val gradientInfo = session.gradientColor?.let { " -> $it" } ?: ""
        val borderInfo = session.borderColor ?: "none"
        val animInfo = if (session.animated) "<gray>Anim: <green>${session.animationType.uppercase()} <gray>(${session.animationFrames}f, ${session.animationSpeed}t)" else "<gray>Anim: <white>none"
        inv.setItem(SLOT_PREVIEW, GUIBuilder.createItem(
            Material.PAINTING,
            "<${session.bgColor}><bold>${session.displayText}</bold>",
            "<gray>ID: <white>${session.badgeId}",
            "<gray>BG: <white>${session.bgColor}$gradientInfo",
            "<gray>Text: <white>${session.textColor}",
            "<gray>Border: <white>$borderInfo <gray>R:${session.borderRadius}",
            "<gray>Style: <white>${session.style.uppercase()}",
            "<gray>Priority: <white>${session.priority}",
            animInfo
        ))

        // Display Text (slot 10)
        inv.setItem(SLOT_DISPLAY_TEXT, createActionItem(
            Material.PAPER,
            "<gold>Display Text",
            "display-text",
            "<gray>Current: <white>${session.displayText}",
            "",
            "<yellow>Click to change"
        ))

        // Background Color (slot 12)
        val bgMaterial = GUIBuilder.findClosestConcrete(session.bgColor)
        inv.setItem(SLOT_BG_COLOR, createActionItem(
            bgMaterial,
            "<gold>Background Color",
            "bg-color",
            "<gray>Current: <white>${session.bgColor}",
            "",
            "<yellow>Click to change"
        ))

        // Text Color (slot 14)
        val textMaterial = findClosestDye(session.textColor)
        inv.setItem(SLOT_TEXT_COLOR, createActionItem(
            textMaterial,
            "<gold>Text Color",
            "text-color",
            "<gray>Current: <white>${session.textColor}",
            "",
            "<yellow>Click to change"
        ))

        // Gradient Color (slot 16)
        val gradMaterial = session.gradientColor?.let { GUIBuilder.findClosestConcrete(it) } ?: Material.GRAY_CONCRETE
        inv.setItem(SLOT_GRADIENT_COLOR, createActionItem(
            gradMaterial,
            "<gold>Gradient Color",
            "gradient-color",
            "<gray>Current: <white>${session.gradientColor ?: "none"}",
            "",
            "<yellow>Click to change",
            "<yellow>Shift-click to remove"
        ))

        // Border Color (slot 20)
        val borderMaterial = if (session.borderColor != null) Material.IRON_BARS else Material.BARRIER
        inv.setItem(SLOT_BORDER_COLOR, createActionItem(
            borderMaterial,
            "<gold>Border Color",
            "border-color",
            "<gray>Current: <white>${session.borderColor ?: "none"}",
            "",
            "<yellow>Click to change",
            "<yellow>Shift-click to remove"
        ))

        // Border Radius (slot 22)
        inv.setItem(SLOT_BORDER_RADIUS, createActionItem(
            Material.STONE_BUTTON,
            "<gold>Border Radius: <white>${session.borderRadius}",
            "border-radius",
            "<gray>Range: 0-3",
            "",
            "<yellow>Click to cycle"
        ))

        // Priority (slot 24)
        inv.setItem(SLOT_PRIORITY, createActionItem(
            Material.EXPERIENCE_BOTTLE,
            "<gold>Priority: <white>${session.priority}",
            "priority",
            "<gray>Higher = shown first",
            "",
            "<yellow>Left-click +10",
            "<yellow>Right-click -10"
        ))

        // Style (slot 29)
        inv.setItem(SLOT_STYLE, createActionItem(
            getStyleMaterial(session.style),
            "<gold>Style: <white>${session.style.uppercase()}",
            "style",
            "<gray>Styles: flat, 3d, glass, metallic, neon",
            "",
            "<yellow>Click to cycle"
        ))

        // Animation Type (slot 28)
        val animMaterial = if (session.animated) Material.MAGMA_CREAM else Material.SNOWBALL
        val animLabel = if (session.animated) session.animationType.uppercase() else "NONE"
        inv.setItem(SLOT_ANIMATION_TYPE, createActionItem(
            animMaterial,
            "<gold>Animation: <white>$animLabel",
            "animation-type",
            "<gray>Types: none, rainbow, pulse, glow, gradient_shift",
            "",
            "<yellow>Click to cycle"
        ))

        // Animation Speed (slot 30) - only show if animated
        if (session.animated) {
            inv.setItem(SLOT_ANIMATION_SPEED, createActionItem(
                Material.CLOCK,
                "<gold>Speed: <white>${session.animationSpeed}t",
                "animation-speed",
                "<gray>Ticks between frames (5-40)",
                "<gray>Lower = faster",
                "",
                "<yellow>Left-click +5",
                "<yellow>Right-click -5"
            ))

            // Animation Frames (slot 32)
            inv.setItem(SLOT_ANIMATION_FRAMES, createActionItem(
                Material.ITEM_FRAME,
                "<gold>Frames: <white>${session.animationFrames}",
                "animation-frames",
                "<gray>Number of frames (2-8)",
                "",
                "<yellow>Left-click +1",
                "<yellow>Right-click -1"
            ))
        }

        // Save (slot 31)
        inv.setItem(SLOT_SAVE, createActionItem(
            Material.EMERALD_BLOCK,
            "<green><bold>Save & Generate",
            "save",
            "<gray>Saves badge and regenerates",
            "<gray>the resource pack"
        ))

        // Cancel (slot 33)
        inv.setItem(SLOT_CANCEL, createActionItem(
            Material.BARRIER,
            "<red><bold>Cancel",
            "cancel",
            "<gray>Discard all changes"
        ))

        return inv
    }

    /**
     * Creates an ItemStack with a PersistentDataContainer action tag.
     */
    private fun createActionItem(material: Material, name: String, action: String, vararg lore: String): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(it) })
                }
                meta.persistentDataContainer.set(KEY_ACTION, PersistentDataType.STRING, action)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Color picker
    // ─────────────────────────────────────────────

    /**
     * Opens the color picker sub-GUI for a specific property.
     */
    private fun openColorPicker(player: Player, property: String) {
        colorPickerProperty[player.uniqueId] = property
        val inv = buildColorPickerInventory()
        player.openInventory(inv)
        GUIBuilder.playClickSound(player)
    }

    private fun buildColorPickerInventory(): Inventory {
        val inv = GUIBuilder.create(COLOR_PICKER_TITLE, 3)

        // Place 16 predefined colors in rows 1-2
        for ((index, entry) in COLOR_PALETTE.withIndex()) {
            val item = ItemStack(entry.material).apply {
                editMeta { meta ->
                    meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<${entry.hex}>${entry.name}"))
                    meta.lore(listOf(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<gray>Hex: <white>${entry.hex}"),
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<yellow>Click to select")
                    ))
                    meta.persistentDataContainer.set(KEY_COLOR_HEX, PersistentDataType.STRING, entry.hex)
                }
            }
            inv.setItem(index, item)
        }

        // Custom hex input (slot 16)
        val customHex = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<gold>Custom Hex"))
                meta.lore(listOf(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<gray>Type a custom <white>#RRGGBB</white> color"),
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<yellow>Click to enter in chat")
                ))
                meta.persistentDataContainer.set(KEY_ACTION, PersistentDataType.STRING, "custom-hex")
            }
        }
        inv.setItem(16, customHex)

        // Back button (slot 22, bottom center)
        GUIBuilder.addBackButton(inv, 22)

        return inv
    }

    // ─────────────────────────────────────────────
    // Inventory click handling
    // ─────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = getPlainTitle(event)

        // Handle editor GUI clicks
        if (title.startsWith(EDITOR_TITLE_PREFIX)) {
            event.isCancelled = true
            handleEditorClick(player, event)
            return
        }

        // Handle color picker clicks
        if (title == COLOR_PICKER_TITLE) {
            event.isCancelled = true
            handleColorPickerClick(player, event)
            return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = getPlainTitle(event)
        if (title.startsWith(EDITOR_TITLE_PREFIX) || title == COLOR_PICKER_TITLE) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val title = PlainTextComponentSerializer.plainText().serialize(event.view.title())

        // Don't clean up session if we're just transitioning between GUIs or waiting for chat input
        if (pendingTextEdits.containsKey(player.uniqueId)) return

        // Clean up color picker property when color picker closes
        // (but only if not transitioning back to editor)
        if (title == COLOR_PICKER_TITLE) {
            // Schedule a check to see if the editor reopened
            FoliaCompat.runTaskLater(plugin, Runnable {
                val currentTitle = runCatching {
                    PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
                }.getOrNull() ?: ""
                if (!currentTitle.startsWith(EDITOR_TITLE_PREFIX)) {
                    colorPickerProperty.remove(player.uniqueId)
                    sessions.remove(player.uniqueId)
                }
            }, 2L)
            return
        }

        // Clean up when editor GUI is closed (and not transitioning)
        if (title.startsWith(EDITOR_TITLE_PREFIX)) {
            FoliaCompat.runTaskLater(plugin, Runnable {
                val currentTitle = runCatching {
                    PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
                }.getOrNull() ?: ""
                if (currentTitle != COLOR_PICKER_TITLE && !currentTitle.startsWith(EDITOR_TITLE_PREFIX)) {
                    if (!pendingTextEdits.containsKey(player.uniqueId)) {
                        sessions.remove(player.uniqueId)
                        colorPickerProperty.remove(player.uniqueId)
                    }
                }
            }, 2L)
        }
    }

    private fun handleEditorClick(player: Player, event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val session = sessions[player.uniqueId] ?: return
        val action = item.itemMeta?.persistentDataContainer
            ?.get(KEY_ACTION, PersistentDataType.STRING) ?: return

        when (action) {
            "display-text" -> {
                // Close GUI, prompt in chat
                pendingTextEdits[player.uniqueId] = PendingEdit(session.badgeId, "display-text")
                player.closeInventory()
                player.sendMessage(Messages.get("editor-type-text"))
                GUIBuilder.playClickSound(player)
            }
            "bg-color" -> {
                openColorPicker(player, "bg-color")
            }
            "text-color" -> {
                openColorPicker(player, "text-color")
            }
            "gradient-color" -> {
                if (event.isShiftClick) {
                    // Remove gradient
                    session.gradientColor = null
                    reopen(player)
                    GUIBuilder.playClickSound(player)
                } else {
                    openColorPicker(player, "gradient-color")
                }
            }
            "border-color" -> {
                if (event.isShiftClick) {
                    // Remove border
                    session.borderColor = null
                    reopen(player)
                    GUIBuilder.playClickSound(player)
                } else {
                    openColorPicker(player, "border-color")
                }
            }
            "border-radius" -> {
                session.borderRadius = (session.borderRadius + 1) % 4
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "priority" -> {
                if (event.isLeftClick) {
                    session.priority = (session.priority + 10).coerceAtMost(1000)
                } else if (event.isRightClick) {
                    session.priority = (session.priority - 10).coerceAtLeast(0)
                }
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "style" -> {
                val styles = Badge.VALID_STYLES.toList()
                val currentIndex = styles.indexOf(session.style)
                session.style = styles[(currentIndex + 1) % styles.size]
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "animation-type" -> {
                val types = listOf("none") + Badge.VALID_ANIMATIONS.filter { it != "none" }
                val currentIndex = types.indexOf(session.animationType)
                val nextType = types[(currentIndex + 1) % types.size]
                session.animationType = nextType
                session.animated = nextType != "none"
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "animation-speed" -> {
                if (event.isLeftClick) {
                    session.animationSpeed = (session.animationSpeed + 5).coerceAtMost(40)
                } else if (event.isRightClick) {
                    session.animationSpeed = (session.animationSpeed - 5).coerceAtLeast(5)
                }
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "animation-frames" -> {
                if (event.isLeftClick) {
                    session.animationFrames = (session.animationFrames + 1).coerceAtMost(8)
                } else if (event.isRightClick) {
                    session.animationFrames = (session.animationFrames - 1).coerceAtLeast(2)
                }
                reopen(player)
                GUIBuilder.playClickSound(player)
            }
            "save" -> {
                saveBadge(player, session)
            }
            "cancel" -> {
                sessions.remove(player.uniqueId)
                player.closeInventory()
                player.sendMessage(Messages.get("editor-cancelled"))
                GUIBuilder.playClickSound(player)
            }
        }
    }

    private fun handleColorPickerClick(player: Player, event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        val session = sessions[player.uniqueId] ?: return
        val property = colorPickerProperty[player.uniqueId] ?: return

        // Check for "Back" button
        val plainName = meta.displayName()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        if (plainName == "Back") {
            colorPickerProperty.remove(player.uniqueId)
            reopen(player)
            GUIBuilder.playClickSound(player)
            return
        }

        // Check for "Custom Hex" action
        val action = meta.persistentDataContainer.get(KEY_ACTION, PersistentDataType.STRING)
        if (action == "custom-hex") {
            pendingTextEdits[player.uniqueId] = PendingEdit(session.badgeId, property)
            player.closeInventory()
            player.sendMessage(Messages.get("editor-type-hex"))
            GUIBuilder.playClickSound(player)
            return
        }

        // Check for predefined color
        val hex = meta.persistentDataContainer.get(KEY_COLOR_HEX, PersistentDataType.STRING) ?: return
        applyColorToSession(session, property, hex)
        colorPickerProperty.remove(player.uniqueId)
        reopen(player)
        GUIBuilder.playClickSound(player)
    }

    // ─────────────────────────────────────────────
    // Chat input handling
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val pending = pendingTextEdits.remove(player.uniqueId) ?: return

        // Cancel the chat message so it doesn't broadcast
        event.isCancelled = true

        val input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()

        // Check for cancel
        if (input.equals("cancel", ignoreCase = true)) {
            FoliaCompat.runTask(plugin, Runnable {
                player.sendMessage(Messages.get("editor-cancelled"))
                // Reopen editor if session still exists
                val session = sessions[player.uniqueId]
                if (session != null) {
                    reopen(player)
                }
            })
            return
        }

        val session = sessions[player.uniqueId]
        if (session == null) {
            FoliaCompat.runTask(plugin, Runnable {
                player.sendMessage(Messages.error("Editor session expired. Please reopen the editor."))
            })
            return
        }

        if (pending.property == "display-text") {
            // Validate display text
            if (input.length > 16) {
                FoliaCompat.runTask(plugin, Runnable {
                    player.sendMessage(Messages.error("Display text must be 16 characters or less."))
                    player.sendMessage(Messages.get("editor-type-text"))
                    pendingTextEdits[player.uniqueId] = pending
                })
                return
            }
            if (input.isEmpty()) {
                FoliaCompat.runTask(plugin, Runnable {
                    player.sendMessage(Messages.error("Display text cannot be empty."))
                    player.sendMessage(Messages.get("editor-type-text"))
                    pendingTextEdits[player.uniqueId] = pending
                })
                return
            }
            session.displayText = input.uppercase()
            FoliaCompat.runTask(plugin, Runnable {
                player.sendMessage(Messages.success("Display text set to: ${session.displayText}"))
                reopen(player)
            })
        } else {
            // Color property input — validate hex
            val badgeModule = plugin.getBadgeModule()
            if (badgeModule == null || !badgeModule.isValidHexColor(input)) {
                FoliaCompat.runTask(plugin, Runnable {
                    player.sendMessage(Messages.error("Invalid hex color. Use format: #RRGGBB"))
                    player.sendMessage(Messages.get("editor-type-hex"))
                    pendingTextEdits[player.uniqueId] = pending
                })
                return
            }
            applyColorToSession(session, pending.property, input)
            FoliaCompat.runTask(plugin, Runnable {
                player.sendMessage(Messages.success("Color set to: $input"))
                reopen(player)
            })
        }
    }

    // ─────────────────────────────────────────────
    // Save logic
    // ─────────────────────────────────────────────

    private fun saveBadge(player: Player, session: EditorSession) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            player.sendMessage(Messages.error("Badge module is not available."))
            return
        }

        val updated = badgeModule.updateBadge(
            id = session.badgeId,
            displayText = session.displayText,
            bgColor = session.bgColor,
            textColor = session.textColor,
            gradientColor = session.gradientColor,
            borderColor = session.borderColor,
            borderRadius = session.borderRadius,
            priority = session.priority,
            clearGradient = session.gradientColor == null,
            clearBorder = session.borderColor == null,
            style = session.style,
            animated = session.animated,
            animationType = session.animationType,
            animationSpeed = session.animationSpeed,
            animationFrames = session.animationFrames
        )

        if (updated == null) {
            player.sendMessage(Messages.get("badge-not-found", "id" to session.badgeId))
            player.closeInventory()
            sessions.remove(player.uniqueId)
            return
        }

        sessions.remove(player.uniqueId)
        player.closeInventory()
        player.sendMessage(Messages.get("editor-saved", "id" to session.badgeId))
        GUIBuilder.playSuccessSound(player)

        plugin.getDisplayModule()?.animationScheduler?.resetBadge(session.badgeId)

        FoliaCompat.runAsync(plugin, Runnable {
            badgeModule.renderAndSave(session.badgeId)

            // Regenerate resource pack and update displays back on main thread
            FoliaCompat.runTask(plugin, Runnable {
                val resourcePackModule = plugin.getResourcePackModule()
                resourcePackModule?.regeneratePack()

                val displayModule = plugin.getDisplayModule()
                displayModule?.updateAllDisplays()
            })
        })
    }

    // ─────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────

    private fun applyColorToSession(session: EditorSession, property: String, hex: String) {
        when (property) {
            "bg-color" -> session.bgColor = hex
            "text-color" -> session.textColor = hex
            "gradient-color" -> session.gradientColor = hex
            "border-color" -> session.borderColor = hex
        }
    }

    private fun getPlainTitle(event: InventoryClickEvent): String {
        return PlainTextComponentSerializer.plainText().serialize(event.view.title())
    }

    private fun getPlainTitle(event: InventoryDragEvent): String {
        return PlainTextComponentSerializer.plainText().serialize(event.view.title())
    }

    private fun getStyleMaterial(style: String): Material = when (style.lowercase()) {
        "3d" -> Material.BRICKS
        "glass" -> Material.GLASS
        "metallic" -> Material.IRON_INGOT
        "neon" -> Material.GLOWSTONE
        else -> Material.SMOOTH_STONE_SLAB
    }

    /**
     * Finds the closest dye material to a hex color for visual representation.
     */
    private fun findClosestDye(hex: String): Material {
        val target = parseHexToRgb(hex) ?: return Material.WHITE_DYE
        var bestMatch = Material.WHITE_DYE
        var bestDistance = Int.MAX_VALUE

        val dyeColors = mapOf(
            Material.WHITE_DYE to intArrayOf(255, 255, 255),
            Material.ORANGE_DYE to intArrayOf(255, 170, 0),
            Material.MAGENTA_DYE to intArrayOf(255, 85, 255),
            Material.LIGHT_BLUE_DYE to intArrayOf(85, 85, 255),
            Material.YELLOW_DYE to intArrayOf(255, 255, 85),
            Material.LIME_DYE to intArrayOf(85, 255, 85),
            Material.PINK_DYE to intArrayOf(255, 85, 85),
            Material.GRAY_DYE to intArrayOf(85, 85, 85),
            Material.LIGHT_GRAY_DYE to intArrayOf(170, 170, 170),
            Material.CYAN_DYE to intArrayOf(0, 170, 170),
            Material.PURPLE_DYE to intArrayOf(170, 0, 170),
            Material.BLUE_DYE to intArrayOf(0, 0, 170),
            Material.BROWN_DYE to intArrayOf(139, 69, 19),
            Material.GREEN_DYE to intArrayOf(0, 170, 0),
            Material.RED_DYE to intArrayOf(170, 0, 0),
            Material.BLACK_DYE to intArrayOf(0, 0, 0)
        )

        for ((material, rgb) in dyeColors) {
            val dr = target[0] - rgb[0]
            val dg = target[1] - rgb[1]
            val db = target[2] - rgb[2]
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = material
            }
        }

        return bestMatch
    }

    private fun parseHexToRgb(hex: String): IntArray? {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return null
        return try {
            intArrayOf(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Cleans up all sessions and pending edits.
     */
    fun cleanup() {
        sessions.clear()
        pendingTextEdits.clear()
        colorPickerProperty.clear()
    }
}
