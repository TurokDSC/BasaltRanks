package dev.turok_ss.basaltranks.modules.badge

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader

/**
 * Pre-made badge template that can be used to quickly create badges.
 * Templates are loaded from the bundled templates.yml resource file.
 */
data class BadgeTemplate(
    val id: String,
    val displayText: String,
    val bgColor: String,
    val textColor: String,
    val gradientColor: String?,
    val borderColor: String?,
    val borderRadius: Int,
    val category: String,
    val style: String = "flat"
) {
    companion object {
        private val templates = mutableListOf<BadgeTemplate>()

        /**
         * Loads all templates from the templates.yml resource embedded in the plugin JAR.
         */
        fun loadTemplates(plugin: JavaPlugin) {
            templates.clear()

            val resource = plugin.getResource("templates.yml") ?: run {
                plugin.logger.warning("templates.yml resource not found in JAR")
                return
            }

            val config = resource.use { stream ->
                YamlConfiguration.loadConfiguration(InputStreamReader(stream))
            }

            val categories = config.getKeys(false)
            for (category in categories) {
                val section = config.getConfigurationSection(category) ?: continue
                for (templateId in section.getKeys(false)) {
                    val templateSection = section.getConfigurationSection(templateId) ?: continue
                    val template = BadgeTemplate(
                        id = templateId,
                        displayText = templateSection.getString("display-text", templateId.uppercase()) ?: templateId.uppercase(),
                        bgColor = templateSection.getString("bg-color", "#808080") ?: "#808080",
                        textColor = templateSection.getString("text-color", "#FFFFFF") ?: "#FFFFFF",
                        gradientColor = templateSection.getString("gradient-color"),
                        borderColor = templateSection.getString("border-color"),
                        borderRadius = templateSection.getInt("border-radius", 1),
                        category = category,
                        style = Badge.normalizeStyle(templateSection.getString("style"))
                    )
                    templates.add(template)
                }
            }

            plugin.logger.info("Loaded ${templates.size} badge templates from templates.yml")
        }

        /**
         * Returns all loaded templates.
         */
        fun getAll(): List<BadgeTemplate> = templates.toList()

        /**
         * Returns templates filtered by category.
         */
        fun getByCategory(category: String): List<BadgeTemplate> =
            templates.filter { it.category.equals(category, ignoreCase = true) }

        /**
         * Finds a template by its ID.
         */
        fun getById(id: String): BadgeTemplate? =
            templates.find { it.id.equals(id, ignoreCase = true) }

        /**
         * Returns all unique category names.
         */
        fun getCategories(): List<String> =
            templates.map { it.category }.distinct()
    }
}
