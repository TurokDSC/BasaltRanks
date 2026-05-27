package dev.turok_ss.basaltranks.modules.editor

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.BadgeTemplate
import dev.turok_ss.basaltranks.utils.GUIBuilder
import dev.turok_ss.basaltranks.utils.Messages
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
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
 * Template browser GUI for creating badges from pre-made templates.
 *
 * 6-row GUI with:
 * - Row 1: Category filter buttons
 * - Rows 2-5: Template items (paginated)
 * - Row 6: Navigation (prev/next pages)
 */
class TemplateGUI(private val plugin: BasaltRanks) : Listener {

    companion object {
        const val TEMPLATE_TITLE = "BasaltRanks | Templates"

        private val KEY_TEMPLATE_ID by lazy { NamespacedKey(BasaltRanks.instance, "template_id") }
        private val KEY_CATEGORY by lazy { NamespacedKey(BasaltRanks.instance, "template_category") }
        private val KEY_ACTION by lazy { NamespacedKey(BasaltRanks.instance, "template_action") }

        private val mm = MiniMessage.miniMessage()

        /** Category filter configuration */
        val CATEGORY_FILTERS = listOf(
            CategoryFilter("all", Material.NETHER_STAR, "<gold>All Templates"),
            CategoryFilter("staff", Material.DIAMOND_SWORD, "<red>Staff"),
            CategoryFilter("rank", Material.GOLD_INGOT, "<yellow>Rank"),
            CategoryFilter("special", Material.EMERALD, "<green>Special"),
            CategoryFilter("fun", Material.FIREWORK_ROCKET, "<light_purple>Fun")
        )
    }

    data class CategoryFilter(val category: String, val material: Material, val displayName: String)

    /** Player's current filter and page */
    private data class BrowseState(var category: String = "all", var page: Int = 0)

    private val playerStates = ConcurrentHashMap<UUID, BrowseState>()

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    fun open(player: Player) {
        val state = BrowseState()
        playerStates[player.uniqueId] = state
        val inv = buildInventory(state)
        player.openInventory(inv)
        GUIBuilder.playOpenSound(player)
    }

    // ─────────────────────────────────────────────
    // Inventory builder
    // ─────────────────────────────────────────────

    private fun buildInventory(state: BrowseState): Inventory {
        val inv = GUIBuilder.create(TEMPLATE_TITLE, 6)
        GUIBuilder.fillBorders(inv)

        // Row 1: Category filter buttons (slots 2-6)
        for ((index, filter) in CATEGORY_FILTERS.withIndex()) {
            val slot = index + 2
            val isActive = state.category == filter.category
            val item = ItemStack(filter.material).apply {
                editMeta { meta ->
                    val suffix = if (isActive) " <white>(active)" else ""
                    meta.displayName(mm.deserialize("${filter.displayName}$suffix"))
                    val count = if (filter.category == "all") {
                        BadgeTemplate.getAll().size
                    } else {
                        BadgeTemplate.getByCategory(filter.category).size
                    }
                    meta.lore(listOf(
                        mm.deserialize("<gray>$count templates"),
                        mm.deserialize("<yellow>Click to filter")
                    ))
                    meta.persistentDataContainer.set(KEY_CATEGORY, PersistentDataType.STRING, filter.category)
                    if (isActive) {
                        meta.setEnchantmentGlintOverride(true)
                    }
                }
            }
            inv.setItem(slot, item)
        }

        // Get filtered templates
        val templates = if (state.category == "all") {
            BadgeTemplate.getAll()
        } else {
            BadgeTemplate.getByCategory(state.category)
        }

        // Content slots (rows 2-5, columns 1-7)
        val contentSlots = GUIBuilder.getContentSlots(6)
        val itemsPerPage = contentSlots.size
        val maxPages = if (templates.isEmpty()) 1 else ((templates.size - 1) / itemsPerPage) + 1
        val pageTemplates = templates.drop(state.page * itemsPerPage).take(itemsPerPage)

        for ((index, template) in pageTemplates.withIndex()) {
            if (index >= contentSlots.size) break
            val slot = contentSlots[index]

            // Use concrete material matching the template's bgColor
            val material = GUIBuilder.findClosestConcrete(template.bgColor)
            val item = ItemStack(material).apply {
                editMeta { meta ->
                    meta.displayName(mm.deserialize("<${template.bgColor}><bold>${template.displayText}"))
                    val lore = mutableListOf(
                        mm.deserialize("<gray>Category: <white>${template.category}"),
                        mm.deserialize("<gray>BG: <white>${template.bgColor}${template.gradientColor?.let { " -> $it" } ?: ""}"),
                        mm.deserialize("<gray>Text: <white>${template.textColor}"),
                        mm.deserialize("<gray>Border: <white>${template.borderColor ?: "none"}")
                    )
                    // Check if badge already exists
                    val badgeModule = plugin.getBadgeModule()
                    val exists = badgeModule?.getBadge(template.id) != null
                    if (exists) {
                        lore.add(mm.deserialize(""))
                        lore.add(mm.deserialize("<red>Badge '${template.id}' already exists"))
                    } else {
                        lore.add(mm.deserialize(""))
                        lore.add(mm.deserialize("<yellow>Click to create badge"))
                    }
                    meta.lore(lore)
                    meta.persistentDataContainer.set(KEY_TEMPLATE_ID, PersistentDataType.STRING, template.id)
                }
            }
            inv.setItem(slot, item)
        }

        // Navigation row (row 6)
        GUIBuilder.addNavigation(inv, state.page, maxPages)

        return inv
    }

    // ─────────────────────────────────────────────
    // Click handling
    // ─────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = PlainTextComponentSerializer.plainText().serialize(event.view.title())
        if (title != TEMPLATE_TITLE) return

        event.isCancelled = true
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        val state = playerStates[player.uniqueId] ?: return

        // Check for category filter click
        val category = meta.persistentDataContainer.get(KEY_CATEGORY, PersistentDataType.STRING)
        if (category != null) {
            state.category = category
            state.page = 0
            refreshInventory(player, state)
            GUIBuilder.playClickSound(player)
            return
        }

        // Check for template click
        val templateId = meta.persistentDataContainer.get(KEY_TEMPLATE_ID, PersistentDataType.STRING)
        if (templateId != null) {
            handleTemplateClick(player, templateId)
            return
        }

        // Check for navigation clicks
        val plainName = meta.displayName()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        when {
            plainName.contains("Previous") && state.page > 0 -> {
                state.page--
                refreshInventory(player, state)
                GUIBuilder.playClickSound(player)
            }
            plainName.contains("Next") -> {
                state.page++
                refreshInventory(player, state)
                GUIBuilder.playClickSound(player)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = PlainTextComponentSerializer.plainText().serialize(event.view.title())
        if (title == TEMPLATE_TITLE) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val title = PlainTextComponentSerializer.plainText().serialize(event.view.title())
        if (title == TEMPLATE_TITLE) {
            playerStates.remove(player.uniqueId)
        }
    }

    private fun handleTemplateClick(player: Player, templateId: String) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            player.sendMessage(Messages.error("Badge module is not enabled."))
            return
        }

        // Check if badge already exists with this ID
        if (badgeModule.getBadge(templateId) != null) {
            player.sendMessage(Messages.get("badge-exists", "id" to templateId))
            GUIBuilder.playErrorSound(player)
            return
        }

        // Create badge from template
        val badge = badgeModule.createFromTemplate(templateId)
        if (badge == null) {
            player.sendMessage(Messages.error("Failed to create badge from template '$templateId'."))
            GUIBuilder.playErrorSound(player)
            return
        }

        // Render the badge
        badgeModule.renderAndSave(badge.id)

        // Regenerate resource pack
        plugin.getResourcePackModule()?.regeneratePack()

        player.closeInventory()
        player.sendMessage(Messages.get("badge-created", "id" to badge.id))
        player.sendMessage(Messages.format(
            "  <gray>Unicode: <white>${badge.unicodeHex}"
        ))
        player.sendMessage(Messages.format(
            "  <gray>Use <white>/badge edit ${badge.id}</white> to customize"
        ))
        GUIBuilder.playSuccessSound(player)

        // Clean up state
        playerStates.remove(player.uniqueId)
    }

    private fun refreshInventory(player: Player, state: BrowseState) {
        val inv = buildInventory(state)
        player.openInventory(inv)
    }

    /**
     * Cleans up all browse states.
     */
    fun cleanup() {
        playerStates.clear()
    }
}
