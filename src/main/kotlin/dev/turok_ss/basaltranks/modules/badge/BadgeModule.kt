package dev.turok_ss.basaltranks.modules.badge

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module
import dev.turok_ss.basaltranks.utils.FoliaCompat
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Core badge module: CRUD operations, YAML persistence,
 * player assignments, and Unicode Private Use Area allocation.
 *
 * Unicode chars are allocated starting at U+E100 (decimal 57600).
 *
 * Command handling is delegated to EditorModule's BadgeCommand.
 */
class BadgeModule(private val plugin: BasaltRanks) : Module {

    override val name: String = "Badge"
    override val configKey: String = "badge"

    companion object {
        private const val UNICODE_START = 0xE100 // 57600 decimal
    }

    /** All registered badges keyed by ID */
    private val badges = mutableMapOf<String, Badge>()

    /** Player UUID -> assigned prefix badge ID */
    private val playerPrefixes = mutableMapOf<UUID, String>()

    /** Player UUID -> assigned suffix badge ID */
    private val playerSuffixes = mutableMapOf<UUID, String>()

    /** Next available Unicode slot (incremented on each badge creation) */
    private var nextUnicode: Int = UNICODE_START

    private lateinit var badgesFile: File
    private lateinit var badgesConfig: YamlConfiguration

    private var dirty = false
    private var saveTask: FoliaCompat.CancellableTask? = null

    // ─────────────────────────────────────────────
    // Module lifecycle
    // ─────────────────────────────────────────────

    override fun onEnable() {
        BadgeTemplate.loadTemplates(plugin)
        loadBadges()

        saveTask = FoliaCompat.runTaskTimer(plugin, Runnable {
            if (dirty) {
                saveBadges()
                dirty = false
            }
        }, 600L, 600L)

        plugin.logger.info("BadgeModule enabled with ${badges.size} badges")
    }

    override fun onDisable() {
        saveTask?.cancel()
        saveTask = null
        if (::badgesFile.isInitialized && ::badgesConfig.isInitialized) {
            if (dirty) saveBadges()
            plugin.logger.info("BadgeModule disabled, saved ${badges.size} badges")
        } else {
            plugin.logger.info("BadgeModule disabled (no data to save, badges were not loaded)")
        }
    }

    // ─────────────────────────────────────────────
    // YAML persistence
    // ─────────────────────────────────────────────

    private fun loadBadges() {
        badgesFile = File(plugin.dataFolder, "badges.yml")
        if (!badgesFile.exists()) {
            plugin.saveResource("badges.yml", false)
        }
        badgesConfig = YamlConfiguration.loadConfiguration(badgesFile)

        nextUnicode = badgesConfig.getInt("next-unicode", UNICODE_START)

        // Load badges
        val badgesSection = badgesConfig.getConfigurationSection("badges")
        if (badgesSection != null) {
            for (id in badgesSection.getKeys(false)) {
                val section = badgesSection.getConfigurationSection(id) ?: continue
                val badge = Badge(
                    id = id,
                    displayText = section.getString("display-text", id.uppercase()) ?: id.uppercase(),
                    bgColor = section.getString("bg-color", "#808080") ?: "#808080",
                    textColor = section.getString("text-color", "#FFFFFF") ?: "#FFFFFF",
                    gradientColor = section.getString("gradient-color"),
                    borderColor = section.getString("border-color"),
                    borderRadius = section.getInt("border-radius", 0),
                    priority = section.getInt("priority", 50),
                    unicodeChar = section.getInt("unicode-char", UNICODE_START).toChar(),
                    style = Badge.normalizeStyle(section.getString("style")),
                    animated = section.getBoolean("animated", false),
                    animationType = Badge.normalizeAnimation(section.getString("animation-type")),
                    animationSpeed = section.getInt("animation-speed", 10),
                    animationFrames = section.getInt("animation-frames", 6),
                    frameChars = section.getIntegerList("frame-chars").map { it.toChar() }
                )
                badges[id] = badge
            }
        }

        // Load player assignments (supports legacy string format and new section format)
        val playersSection = badgesConfig.getConfigurationSection("players")
        if (playersSection != null) {
            for (uuidStr in playersSection.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                val section = playersSection.getConfigurationSection(uuidStr)
                if (section != null) {
                    val prefix = section.getString("prefix")
                    val suffix = section.getString("suffix")
                    if (prefix != null && badges.containsKey(prefix)) playerPrefixes[uuid] = prefix
                    if (suffix != null && badges.containsKey(suffix)) playerSuffixes[uuid] = suffix
                } else {
                    val badgeId = playersSection.getString(uuidStr) ?: continue
                    if (badges.containsKey(badgeId)) playerPrefixes[uuid] = badgeId
                }
            }
        }

        plugin.logger.info("Loaded ${badges.size} badges, ${playerPrefixes.size + playerSuffixes.size} player assignments")
    }

    private fun saveBadges() {
        badgesConfig.set("next-unicode", nextUnicode)

        // Save badges
        badgesConfig.set("badges", null) // Clear section
        for ((id, badge) in badges) {
            val path = "badges.$id"
            badgesConfig.set("$path.display-text", badge.displayText)
            badgesConfig.set("$path.bg-color", badge.bgColor)
            badgesConfig.set("$path.text-color", badge.textColor)
            badgesConfig.set("$path.gradient-color", badge.gradientColor)
            badgesConfig.set("$path.border-color", badge.borderColor)
            badgesConfig.set("$path.border-radius", badge.borderRadius)
            badgesConfig.set("$path.priority", badge.priority)
            badgesConfig.set("$path.unicode-char", badge.unicodeChar.code)
            badgesConfig.set("$path.style", badge.style)
            badgesConfig.set("$path.animated", badge.animated)
            badgesConfig.set("$path.animation-type", badge.animationType)
            badgesConfig.set("$path.animation-speed", badge.animationSpeed)
            badgesConfig.set("$path.animation-frames", badge.animationFrames)
            badgesConfig.set("$path.frame-chars", badge.frameChars.map { it.code })
        }

        // Save player assignments (new section format with prefix/suffix)
        badgesConfig.set("players", null)
        val allUuids = (playerPrefixes.keys + playerSuffixes.keys).distinct()
        for (uuid in allUuids) {
            val prefix = playerPrefixes[uuid]
            val suffix = playerSuffixes[uuid]
            if (prefix != null) badgesConfig.set("players.$uuid.prefix", prefix)
            if (suffix != null) badgesConfig.set("players.$uuid.suffix", suffix)
        }

        badgesConfig.save(badgesFile)
    }

    fun reloadBadges() {
        badges.clear()
        playerPrefixes.clear()
        playerSuffixes.clear()
        dirty = false
        loadBadges()
        BadgeTemplate.loadTemplates(plugin)
    }

    private fun markDirty() {
        dirty = true
    }

    // ─────────────────────────────────────────────
    // Badge CRUD
    // ─────────────────────────────────────────────

    /**
     * Creates a new badge with the given parameters and allocates a Unicode character.
     * @return The created Badge, or null if ID already exists.
     */
    fun createBadge(
        id: String,
        displayText: String,
        bgColor: String,
        textColor: String,
        gradientColor: String? = null,
        borderColor: String? = null,
        borderRadius: Int = 0,
        priority: Int = 50,
        style: String = "flat",
        animated: Boolean = false,
        animationType: String = "none",
        animationSpeed: Int = 10,
        animationFrames: Int = 6
    ): Badge? {
        val normalizedId = id.lowercase().replace(" ", "-")
            .replace(Regex("[^a-z0-9_-]"), "")
        if (normalizedId.isBlank() || normalizedId.length > 32) return null
        if (badges.containsKey(normalizedId)) return null

        val frameCount = animationFrames.coerceIn(2, 8)
        val unicodeChar: Char
        val frameChars: List<Char>

        if (animated) {
            frameChars = (0 until frameCount).map { (nextUnicode + it).toChar() }
            unicodeChar = frameChars.first()
            nextUnicode += frameCount
        } else {
            unicodeChar = nextUnicode.toChar()
            nextUnicode++
            frameChars = emptyList()
        }

        val badge = Badge(
            id = normalizedId,
            displayText = displayText.uppercase(),
            bgColor = bgColor,
            textColor = textColor,
            gradientColor = gradientColor,
            borderColor = borderColor,
            borderRadius = borderRadius.coerceIn(0, 3),
            priority = priority,
            unicodeChar = unicodeChar,
            style = Badge.normalizeStyle(style),
            animated = animated,
            animationType = Badge.normalizeAnimation(animationType),
            animationSpeed = animationSpeed.coerceIn(1, 100),
            animationFrames = frameCount,
            frameChars = frameChars
        )

        badges[normalizedId] = badge
        markDirty()
        return badge
    }

    /**
     * Creates a badge from a pre-made template.
     */
    fun createFromTemplate(templateId: String, badgeId: String? = null, priority: Int = 50): Badge? {
        val template = BadgeTemplate.getById(templateId) ?: return null
        val id = badgeId ?: template.id
        return createBadge(
            id = id,
            displayText = template.displayText,
            bgColor = template.bgColor,
            textColor = template.textColor,
            gradientColor = template.gradientColor,
            borderColor = template.borderColor,
            borderRadius = template.borderRadius,
            priority = priority,
            style = template.style
        )
    }

    /**
     * Updates an existing badge's properties without changing its Unicode allocation.
     * @return The updated Badge, or null if not found.
     */
    fun updateBadge(
        id: String,
        displayText: String? = null,
        bgColor: String? = null,
        textColor: String? = null,
        gradientColor: String? = null,
        borderColor: String? = null,
        borderRadius: Int? = null,
        priority: Int? = null,
        clearGradient: Boolean = false,
        clearBorder: Boolean = false,
        style: String? = null,
        animated: Boolean? = null,
        animationType: String? = null,
        animationSpeed: Int? = null,
        animationFrames: Int? = null
    ): Badge? {
        val normalizedId = id.lowercase()
        val existing = badges[normalizedId] ?: return null

        val newAnimated = animated ?: existing.animated
        val newFrameCount = (animationFrames ?: existing.animationFrames).coerceIn(2, 8)

        val newFrameChars: List<Char>
        if (newAnimated) {
            val needsRealloc = !existing.animated || newFrameCount != existing.frameChars.size
            if (needsRealloc) {
                val chars = mutableListOf(existing.unicodeChar)
                val remaining = newFrameCount - 1
                for (i in 0 until remaining) {
                    chars.add((nextUnicode + i).toChar())
                }
                nextUnicode += remaining
                newFrameChars = chars
            } else {
                newFrameChars = existing.frameChars
            }
        } else {
            newFrameChars = emptyList()
        }

        val updated = existing.copy(
            displayText = displayText ?: existing.displayText,
            bgColor = bgColor ?: existing.bgColor,
            textColor = textColor ?: existing.textColor,
            gradientColor = if (clearGradient) null else (gradientColor ?: existing.gradientColor),
            borderColor = if (clearBorder) null else (borderColor ?: existing.borderColor),
            borderRadius = borderRadius?.coerceIn(0, 3) ?: existing.borderRadius,
            priority = priority ?: existing.priority,
            style = style?.let { Badge.normalizeStyle(it) } ?: existing.style,
            animated = newAnimated,
            animationType = animationType?.let { Badge.normalizeAnimation(it) } ?: existing.animationType,
            animationSpeed = animationSpeed?.coerceIn(1, 100) ?: existing.animationSpeed,
            animationFrames = newFrameCount,
            frameChars = newFrameChars
        )

        badges[normalizedId] = updated
        markDirty()
        return updated
    }

    /**
     * Deletes a badge by ID. Removes all player assignments for it.
     * @return true if the badge existed and was deleted.
     */
    fun deleteBadge(id: String): Boolean {
        val normalizedId = id.lowercase()
        if (!badges.containsKey(normalizedId)) return false

        badges.remove(normalizedId)

        // Remove all player assignments for this badge (both prefix and suffix)
        val toRemovePrefix = playerPrefixes.entries.filter { it.value == normalizedId }.map { it.key }
        toRemovePrefix.forEach { playerPrefixes.remove(it) }
        val toRemoveSuffix = playerSuffixes.entries.filter { it.value == normalizedId }.map { it.key }
        toRemoveSuffix.forEach { playerSuffixes.remove(it) }

        // Delete rendered images (static + animation frames)
        val renderedDir = File(plugin.dataFolder, "rendered")
        val imageFile = File(renderedDir, "$normalizedId.png")
        if (imageFile.exists()) imageFile.delete()
        for (i in 0 until 8) {
            val frameFile = File(renderedDir, "${normalizedId}_f$i.png")
            if (frameFile.exists()) frameFile.delete()
        }

        markDirty()
        return true
    }

    fun getBadge(id: String): Badge? = badges[id.lowercase()]

    fun getAllBadges(): List<Badge> = badges.values.sortedByDescending { it.priority }

    fun getBadgeIds(): List<String> = badges.keys.toList()

    // ─────────────────────────────────────────────
    // Player assignment
    // ─────────────────────────────────────────────

    fun assignBadge(playerUuid: UUID, badgeId: String, slot: String = "prefix"): Boolean {
        val normalizedId = badgeId.lowercase()
        if (!badges.containsKey(normalizedId)) return false
        when (slot.lowercase()) {
            "suffix" -> playerSuffixes[playerUuid] = normalizedId
            else -> playerPrefixes[playerUuid] = normalizedId
        }
        markDirty()
        return true
    }

    fun removeBadge(playerUuid: UUID, slot: String? = null): Boolean {
        var removed = false
        when (slot?.lowercase()) {
            "prefix" -> removed = playerPrefixes.remove(playerUuid) != null
            "suffix" -> removed = playerSuffixes.remove(playerUuid) != null
            else -> {
                val r1 = playerPrefixes.remove(playerUuid) != null
                val r2 = playerSuffixes.remove(playerUuid) != null
                removed = r1 || r2
            }
        }
        if (removed) markDirty()
        return removed
    }

    fun getPlayerBadge(playerUuid: UUID): Badge? {
        val badgeId = playerPrefixes[playerUuid] ?: return null
        return badges[badgeId]
    }

    fun getPlayerSuffixBadge(playerUuid: UUID): Badge? {
        val badgeId = playerSuffixes[playerUuid] ?: return null
        return badges[badgeId]
    }

    fun getPlayerBadgeId(playerUuid: UUID): String? = playerPrefixes[playerUuid]

    fun getPlayerSuffixBadgeId(playerUuid: UUID): String? = playerSuffixes[playerUuid]

    fun getPlayerAssignmentCount(badgeId: String): Int {
        val id = badgeId.lowercase()
        return playerPrefixes.values.count { it == id } + playerSuffixes.values.count { it == id }
    }

    // ─────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────

    /**
     * Renders a badge to PNG and saves it in the rendered/ directory.
     */
    fun renderAndSave(badgeId: String): File? {
        val badge = getBadge(badgeId) ?: return null
        val outputDir = File(plugin.dataFolder, "rendered")
        if (!outputDir.exists()) outputDir.mkdirs()

        if (badge.animated && badge.frameChars.isNotEmpty()) {
            val frames = BadgeRenderer.renderAnimationFrames(badge)
            frames.forEachIndexed { index, image ->
                ImageIO.write(image, "PNG", File(outputDir, "${badge.id}_f$index.png"))
            }
        }

        val image = BadgeRenderer.renderBadge(badge)
        val file = File(outputDir, "${badge.id}.png")
        ImageIO.write(image, "PNG", file)
        return file
    }

    /**
     * Renders all badges and saves them.
     */
    fun renderAll(): Int {
        val outputDir = File(plugin.dataFolder, "rendered")
        if (!outputDir.exists()) outputDir.mkdirs()
        var count = 0
        for (badge in badges.values) {
            if (badge.animated && badge.frameChars.isNotEmpty()) {
                val frames = BadgeRenderer.renderAnimationFrames(badge)
                frames.forEachIndexed { index, image ->
                    ImageIO.write(image, "PNG", File(outputDir, "${badge.id}_f$index.png"))
                }
            }
            val image = BadgeRenderer.renderBadge(badge)
            ImageIO.write(image, "PNG", File(outputDir, "${badge.id}.png"))
            count++
        }
        return count
    }

    // ─────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────

    fun isValidHexColor(color: String): Boolean {
        return color.matches(Regex("^#[0-9A-Fa-f]{6}$"))
    }
}
