package dev.turok_ss.basaltranks.modules.resourcepack

import dev.turok_ss.basaltranks.modules.badge.Badge

/**
 * Generates the `assets/minecraft/font/default.json` content
 * that maps each badge's rendered PNG to its Unicode PUA character.
 *
 * Each badge gets a bitmap font provider entry pointing to
 * "basaltranks:font/{badge.id}.png" with its allocated Unicode char.
 */
object FontConfigBuilder {

    /**
     * Builds the complete default.json font config as a JSON string.
     *
     * @param badges Collection of all registered badges
     * @return A valid JSON string for Minecraft's font provider system
     */
    fun build(badges: Collection<Badge>): String {
        if (badges.isEmpty()) {
            return buildEmptyConfig()
        }

        val entries = mutableListOf<String>()

        for (badge in badges) {
            entries.add(buildProviderEntry(badge.unicodeChar, "${badge.id}.png"))

            if (badge.animated && badge.frameChars.isNotEmpty()) {
                for ((frameIndex, frameChar) in badge.frameChars.withIndex()) {
                    if (frameChar == badge.unicodeChar) continue
                    entries.add(buildProviderEntry(frameChar, "${badge.id}_f${frameIndex}.png"))
                }
            }
        }

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"providers\": [")

        for ((index, entry) in entries.withIndex()) {
            sb.append(entry)
            if (index < entries.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("  ]")
        sb.appendLine("}")

        return sb.toString()
    }

    private fun buildProviderEntry(char: Char, file: String): String {
        val unicodeEscaped = escapeUnicode(char)
        return buildString {
            appendLine("    {")
            appendLine("      \"type\": \"bitmap\",")
            appendLine("      \"file\": \"basaltranks:font/$file\",")
            appendLine("      \"ascent\": 8,")
            appendLine("      \"height\": 10,")
            appendLine("      \"chars\": [\"$unicodeEscaped\"]")
            append("    }")
        }
    }

    /**
     * Builds a minimal empty font config with no providers.
     */
    private fun buildEmptyConfig(): String {
        return """
            {
              "providers": []
            }
        """.trimIndent()
    }

    /**
     * Escapes a char to its JSON Unicode escape sequence (e.g., ).
     */
    private fun escapeUnicode(char: Char): String {
        return "\\u${char.code.toString(16).uppercase().padStart(4, '0')}"
    }
}
