package dev.turok_ss.basaltranks.modules.badge

/**
 * Represents a visual badge that can be rendered as a PNG image
 * and displayed in-game via resource pack custom characters.
 *
 * @property id Unique identifier for the badge (e.g., "owner", "vip")
 * @property displayText Text shown on the badge (e.g., "OWNER", "VIP+")
 * @property bgColor Background color in hex format "#RRGGBB"
 * @property textColor Text color in hex format "#RRGGBB"
 * @property gradientColor Optional gradient end color (left-to-right)
 * @property borderColor Optional 1px border color
 * @property borderRadius Corner radius 0-3 (pixel art rounded corners)
 * @property priority Display priority — higher values shown first
 * @property unicodeChar Assigned Private Use Area character for resource pack
 * @property style Visual style: "flat", "3d", "glass", "metallic", "neon"
 * @property animated Whether this badge has animation frames
 * @property animationType Animation type: "none", "rainbow", "pulse", "glow", "gradient_shift"
 * @property animationSpeed Ticks between frame changes (lower = faster)
 * @property animationFrames Number of animation frames (2-8)
 * @property frameChars List of Unicode chars allocated for each animation frame
 */
data class Badge(
    val id: String,
    val displayText: String,
    val bgColor: String,
    val textColor: String,
    val gradientColor: String? = null,
    val borderColor: String? = null,
    val borderRadius: Int = 0,
    val priority: Int = 50,
    val unicodeChar: Char = '',
    val style: String = "flat",
    val animated: Boolean = false,
    val animationType: String = "none",
    val animationSpeed: Int = 10,
    val animationFrames: Int = 6,
    val frameChars: List<Char> = emptyList()
) {
    val unicodeHex: String get() = "U+${unicodeChar.code.toString(16).uppercase()}"

    fun getCurrentChar(frameIndex: Int): Char =
        if (animated && frameChars.isNotEmpty()) frameChars[frameIndex % frameChars.size]
        else unicodeChar

    companion object {
        val VALID_STYLES = setOf("flat", "3d", "glass", "metallic", "neon")
        val VALID_ANIMATIONS = setOf("none", "rainbow", "pulse", "glow", "gradient_shift")

        fun normalizeStyle(style: String?): String =
            style?.lowercase()?.takeIf { it in VALID_STYLES } ?: "flat"

        fun normalizeAnimation(type: String?): String =
            type?.lowercase()?.takeIf { it in VALID_ANIMATIONS } ?: "none"
    }
}
