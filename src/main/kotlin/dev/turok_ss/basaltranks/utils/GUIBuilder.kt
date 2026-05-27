package dev.turok_ss.basaltranks.utils

import dev.turok_ss.basaltranks.BasaltRanks
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object GUIBuilder {

    private val mm = MiniMessage.miniMessage()

    private fun getSound(configKey: String, default: Sound): Sound {
        val plugin = BasaltRanks.instance
        val defaultName = default.toString()
        val soundName = plugin.config.getString("sounds.$configKey", defaultName) ?: defaultName
        return try {
            val key = NamespacedKey.minecraft(soundName.lowercase().replace(".", "_"))
            Registry.SOUNDS.get(key) ?: default
        } catch (_: Exception) { default }
    }

    private fun getVolume(): Float {
        return BasaltRanks.instance.config.getDouble("sounds.volume", 0.5).toFloat()
    }

    private fun getPitch(default: Float): Float {
        return BasaltRanks.instance.config.getDouble("sounds.pitch", default.toDouble()).toFloat()
    }

    fun create(title: String, rows: Int = 6): Inventory {
        val size = (rows * 9).coerceIn(9, 54)
        return Bukkit.createInventory(null, size, mm.deserialize("<dark_gray>$title"))
    }

    fun fillBorders(inv: Inventory, material: Material = Material.GRAY_STAINED_GLASS_PANE) {
        val border = createItem(material, " ")
        val size = inv.size
        val cols = 9
        for (i in 0 until cols) inv.setItem(i, border)
        for (i in size - cols until size) inv.setItem(i, border)
        for (i in 0 until size step cols) inv.setItem(i, border)
        for (i in cols - 1 until size step cols) inv.setItem(i, border)
    }

    fun createItem(material: Material, name: String, vararg lore: String): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(mm.deserialize(name))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { mm.deserialize(it) })
                }
            }
        }
    }

    fun createHead(playerName: String, displayName: String, vararg lore: String): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            editMeta { meta ->
                meta.displayName(mm.deserialize(displayName))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { mm.deserialize(it) })
                }
                if (meta is org.bukkit.inventory.meta.SkullMeta) {
                    meta.owningPlayer = Bukkit.getPlayerExact(playerName)
                        ?: Bukkit.getOfflinePlayerIfCached(playerName)
                }
            }
        }
    }

    fun addNavigation(inv: Inventory, page: Int, maxPages: Int) {
        val size = inv.size
        if (page > 0) inv.setItem(size - 9, createItem(Material.ARROW, "<green>Previous page"))
        inv.setItem(size - 5, createItem(Material.PAPER, "<gray>Page <white>${page + 1}/$maxPages</white>"))
        if (page < maxPages - 1) inv.setItem(size - 1, createItem(Material.ARROW, "<green>Next page"))
    }

    fun addBackButton(inv: Inventory, slot: Int = -1) {
        val pos = if (slot >= 0) slot else inv.size - 5
        inv.setItem(pos, createItem(Material.BARRIER, "<red>Back"))
    }

    fun playOpenSound(player: Player) {
        val sound = getSound("open", Sound.BLOCK_NOTE_BLOCK_CHIME)
        player.playSound(player.location, sound, getVolume(), getPitch(1.2f))
    }

    fun playClickSound(player: Player) {
        val sound = getSound("click", Sound.UI_BUTTON_CLICK)
        player.playSound(player.location, sound, getVolume(), getPitch(1.8f))
    }

    fun playSuccessSound(player: Player) {
        val sound = getSound("success", Sound.ENTITY_PLAYER_LEVELUP)
        player.playSound(player.location, sound, getVolume(), getPitch(2.0f))
    }

    fun playErrorSound(player: Player) {
        val sound = getSound("error", Sound.ENTITY_VILLAGER_NO)
        player.playSound(player.location, sound, getVolume(), getPitch(0.5f))
    }

    fun getContentSlots(rows: Int = 6): List<Int> {
        val slots = mutableListOf<Int>()
        for (row in 1 until rows - 1) {
            for (col in 1..7) {
                slots.add(row * 9 + col)
            }
        }
        return slots
    }

    fun isBorderSlot(slot: Int, size: Int): Boolean {
        val cols = 9
        val row = slot / cols
        val col = slot % cols
        val maxRow = size / cols - 1
        return row == 0 || row == maxRow || col == 0 || col == cols - 1
    }

    fun findClosestConcrete(hex: String): Material {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return Material.GRAY_CONCRETE

        val r: Int
        val g: Int
        val b: Int
        try {
            r = clean.substring(0, 2).toInt(16)
            g = clean.substring(2, 4).toInt(16)
            b = clean.substring(4, 6).toInt(16)
        } catch (_: NumberFormatException) {
            return Material.GRAY_CONCRETE
        }

        val concreteColors = arrayOf(
            intArrayOf(255, 255, 255) to Material.WHITE_CONCRETE,
            intArrayOf(255, 170, 0) to Material.ORANGE_CONCRETE,
            intArrayOf(255, 85, 255) to Material.MAGENTA_CONCRETE,
            intArrayOf(85, 170, 255) to Material.LIGHT_BLUE_CONCRETE,
            intArrayOf(255, 255, 85) to Material.YELLOW_CONCRETE,
            intArrayOf(85, 255, 85) to Material.LIME_CONCRETE,
            intArrayOf(255, 150, 150) to Material.PINK_CONCRETE,
            intArrayOf(85, 85, 85) to Material.GRAY_CONCRETE,
            intArrayOf(170, 170, 170) to Material.LIGHT_GRAY_CONCRETE,
            intArrayOf(0, 170, 170) to Material.CYAN_CONCRETE,
            intArrayOf(170, 0, 170) to Material.PURPLE_CONCRETE,
            intArrayOf(0, 0, 170) to Material.BLUE_CONCRETE,
            intArrayOf(139, 69, 19) to Material.BROWN_CONCRETE,
            intArrayOf(0, 170, 0) to Material.GREEN_CONCRETE,
            intArrayOf(170, 0, 0) to Material.RED_CONCRETE,
            intArrayOf(0, 0, 0) to Material.BLACK_CONCRETE
        )

        var bestMatch = Material.GRAY_CONCRETE
        var bestDistance = Int.MAX_VALUE
        for ((rgb, material) in concreteColors) {
            val dr = r - rgb[0]
            val dg = g - rgb[1]
            val db = b - rgb[2]
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = material
            }
        }
        return bestMatch
    }
}
