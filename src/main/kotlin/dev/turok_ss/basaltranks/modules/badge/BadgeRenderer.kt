package dev.turok_ss.basaltranks.modules.badge

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders Badge data classes into pixel-art PNG images using Java2D.
 * Contains a hard-coded 5x7 pixel font for A-Z, 0-9, space, and symbols.
 *
 * Supports 5 visual styles: flat, 3d, glass, metallic, neon.
 */
object BadgeRenderer {

    private const val CHAR_WIDTH = 5
    private const val CHAR_HEIGHT = 7
    private const val CHAR_SPACING = 1
    private const val PADDING_X = 2
    private const val PADDING_Y = 2
    private const val BORDER_WIDTH = 1
    private const val SHADOW_OFFSET = 1

    /**
     * Hard-coded 5x7 pixel font bitmaps.
     * Each glyph is 7 rows of 5 booleans (true = pixel on).
     */
    private val FONT: Map<Char, Array<BooleanArray>> = buildFont()

    private fun b(vararg rows: String): Array<BooleanArray> {
        return Array(rows.size) { r ->
            BooleanArray(rows[r].length) { c -> rows[r][c] == '#' }
        }
    }

    private fun buildFont(): Map<Char, Array<BooleanArray>> = mapOf(
        'A' to b(
            ".###.",
            "#...#",
            "#...#",
            "#####",
            "#...#",
            "#...#",
            "#...#"
        ),
        'B' to b(
            "####.",
            "#...#",
            "#...#",
            "####.",
            "#...#",
            "#...#",
            "####."
        ),
        'C' to b(
            ".####",
            "#....",
            "#....",
            "#....",
            "#....",
            "#....",
            ".####"
        ),
        'D' to b(
            "####.",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            "####."
        ),
        'E' to b(
            "#####",
            "#....",
            "#....",
            "####.",
            "#....",
            "#....",
            "#####"
        ),
        'F' to b(
            "#####",
            "#....",
            "#....",
            "####.",
            "#....",
            "#....",
            "#...."
        ),
        'G' to b(
            ".####",
            "#....",
            "#....",
            "#.###",
            "#...#",
            "#...#",
            ".###."
        ),
        'H' to b(
            "#...#",
            "#...#",
            "#...#",
            "#####",
            "#...#",
            "#...#",
            "#...#"
        ),
        'I' to b(
            "#####",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "#####"
        ),
        'J' to b(
            "..###",
            "....#",
            "....#",
            "....#",
            "....#",
            "#...#",
            ".###."
        ),
        'K' to b(
            "#...#",
            "#..#.",
            "#.#..",
            "##...",
            "#.#..",
            "#..#.",
            "#...#"
        ),
        'L' to b(
            "#....",
            "#....",
            "#....",
            "#....",
            "#....",
            "#....",
            "#####"
        ),
        'M' to b(
            "#...#",
            "##.##",
            "#.#.#",
            "#...#",
            "#...#",
            "#...#",
            "#...#"
        ),
        'N' to b(
            "#...#",
            "##..#",
            "#.#.#",
            "#..##",
            "#...#",
            "#...#",
            "#...#"
        ),
        'O' to b(
            ".###.",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            ".###."
        ),
        'P' to b(
            "####.",
            "#...#",
            "#...#",
            "####.",
            "#....",
            "#....",
            "#...."
        ),
        'Q' to b(
            ".###.",
            "#...#",
            "#...#",
            "#...#",
            "#.#.#",
            "#..#.",
            ".##.#"
        ),
        'R' to b(
            "####.",
            "#...#",
            "#...#",
            "####.",
            "#.#..",
            "#..#.",
            "#...#"
        ),
        'S' to b(
            ".####",
            "#....",
            "#....",
            ".###.",
            "....#",
            "....#",
            "####."
        ),
        'T' to b(
            "#####",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "..#.."
        ),
        'U' to b(
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            ".###."
        ),
        'V' to b(
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            ".#.#.",
            ".#.#.",
            "..#.."
        ),
        'W' to b(
            "#...#",
            "#...#",
            "#...#",
            "#.#.#",
            "#.#.#",
            "##.##",
            "#...#"
        ),
        'X' to b(
            "#...#",
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#",
            "#...#"
        ),
        'Y' to b(
            "#...#",
            "#...#",
            ".#.#.",
            "..#..",
            "..#..",
            "..#..",
            "..#.."
        ),
        'Z' to b(
            "#####",
            "....#",
            "...#.",
            "..#..",
            ".#...",
            "#....",
            "#####"
        ),
        '0' to b(
            ".###.",
            "#..##",
            "#.#.#",
            "#.#.#",
            "#.#.#",
            "##..#",
            ".###."
        ),
        '1' to b(
            "..#..",
            ".##..",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "#####"
        ),
        '2' to b(
            ".###.",
            "#...#",
            "....#",
            "..##.",
            ".#...",
            "#....",
            "#####"
        ),
        '3' to b(
            ".###.",
            "#...#",
            "....#",
            "..##.",
            "....#",
            "#...#",
            ".###."
        ),
        '4' to b(
            "...#.",
            "..##.",
            ".#.#.",
            "#..#.",
            "#####",
            "...#.",
            "...#."
        ),
        '5' to b(
            "#####",
            "#....",
            "####.",
            "....#",
            "....#",
            "#...#",
            ".###."
        ),
        '6' to b(
            ".###.",
            "#....",
            "#....",
            "####.",
            "#...#",
            "#...#",
            ".###."
        ),
        '7' to b(
            "#####",
            "....#",
            "...#.",
            "..#..",
            ".#...",
            ".#...",
            ".#..."
        ),
        '8' to b(
            ".###.",
            "#...#",
            "#...#",
            ".###.",
            "#...#",
            "#...#",
            ".###."
        ),
        '9' to b(
            ".###.",
            "#...#",
            "#...#",
            ".####",
            "....#",
            "....#",
            ".###."
        ),
        ' ' to b(
            ".....",
            ".....",
            ".....",
            ".....",
            ".....",
            ".....",
            "....."
        ),
        '+' to b(
            ".....",
            "..#..",
            "..#..",
            "#####",
            "..#..",
            "..#..",
            "....."
        ),
        '-' to b(
            ".....",
            ".....",
            ".....",
            "#####",
            ".....",
            ".....",
            "....."
        ),
        '!' to b(
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            "..#..",
            ".....",
            "..#.."
        ),
        '?' to b(
            ".###.",
            "#...#",
            "....#",
            "..##.",
            "..#..",
            ".....",
            "..#.."
        )
    )

    // =========================================================
    // Public API
    // =========================================================

    /**
     * Renders a badge into a BufferedImage using pixel-art style.
     * Delegates to the appropriate style renderer based on badge.style.
     *
     * @param badge The badge to render
     * @return A BufferedImage of the rendered badge
     */
    fun renderBadge(badge: Badge): BufferedImage {
        return when (badge.style.lowercase()) {
            "3d" -> render3D(badge)
            "glass" -> renderGlass(badge)
            "metallic" -> renderMetallic(badge)
            "neon" -> renderNeon(badge)
            else -> renderFlat(badge)
        }
    }

    /**
     * Returns the dimensions a badge would have if rendered.
     */
    fun calculateDimensions(badge: Badge): Pair<Int, Int> {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight
        return totalWidth to totalHeight
    }

    fun renderAnimationFrames(badge: Badge): List<BufferedImage> {
        val frameCount = badge.animationFrames.coerceIn(2, 8)
        return when (badge.animationType.lowercase()) {
            "rainbow" -> renderRainbowFrames(badge, frameCount)
            "pulse" -> renderPulseFrames(badge, frameCount)
            "glow" -> renderGlowFrames(badge, frameCount)
            "gradient_shift" -> renderGradientShiftFrames(badge, frameCount)
            else -> listOf(renderBadge(badge))
        }
    }

    // =========================================================
    // FLAT style — solid/gradient background, simple shadow
    // =========================================================

    private fun renderFlat(badge: Badge): BufferedImage {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        disableSmoothing(g2d)

        // Border background
        if (hasBorder) {
            g2d.color = parseColor(badge.borderColor ?: "#000000")
            g2d.fillRect(0, 0, totalWidth, totalHeight)
        }

        val fillX = if (hasBorder) BORDER_WIDTH else 0
        val fillY = if (hasBorder) BORDER_WIDTH else 0
        val fillW = contentWidth
        val fillH = contentHeight

        // Fill background: gradient or solid
        drawBackground(g2d, badge, fillX, fillY, fillW, fillH)

        // Border radius
        if (badge.borderRadius > 0) {
            applyBorderRadius(image, badge.borderRadius, totalWidth, totalHeight)
        }

        // Text position
        val textStartX = fillX + PADDING_X
        val textStartY = fillY + PADDING_Y

        // Shadow
        val shadowColor = Color(0, 0, 0, 80)
        drawPixelText(image, text, textStartX + SHADOW_OFFSET, textStartY + SHADOW_OFFSET, shadowColor)

        // Main text
        val textColor = parseColor(badge.textColor)
        drawPixelText(image, text, textStartX, textStartY, textColor)

        g2d.dispose()
        return image
    }

    // =========================================================
    // 3D style — bottom edge, top highlight, stronger shadow
    // =========================================================

    private fun render3D(badge: Badge): BufferedImage {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        disableSmoothing(g2d)

        // Border background
        if (hasBorder) {
            g2d.color = parseColor(badge.borderColor ?: "#000000")
            g2d.fillRect(0, 0, totalWidth, totalHeight)
        }

        val fillX = if (hasBorder) BORDER_WIDTH else 0
        val fillY = if (hasBorder) BORDER_WIDTH else 0
        val fillW = contentWidth
        val fillH = contentHeight

        // Fill background
        drawBackground(g2d, badge, fillX, fillY, fillW, fillH)

        // 3D effect: darker bottom strip (2px)
        val bgColor = parseColor(badge.bgColor)
        val darkBottom = darkenColor(bgColor, 0.55)
        g2d.color = darkBottom
        val edgeHeight = 2.coerceAtMost(fillH)
        g2d.fillRect(fillX, fillY + fillH - edgeHeight, fillW, edgeHeight)

        // 3D effect: top highlight line (1px)
        val brightTop = brightenColor(bgColor, 1.4)
        g2d.color = brightTop
        g2d.fillRect(fillX, fillY, fillW, 1)

        // Border radius
        if (badge.borderRadius > 0) {
            applyBorderRadius(image, badge.borderRadius, totalWidth, totalHeight)
        }

        // Text position
        val textStartX = fillX + PADDING_X
        val textStartY = fillY + PADDING_Y

        // Stronger shadow (2px down, darker)
        val shadowColor = Color(0, 0, 0, 130)
        drawPixelText(image, text, textStartX + SHADOW_OFFSET, textStartY + 2, shadowColor)

        // Main text
        val textColor = parseColor(badge.textColor)
        drawPixelText(image, text, textStartX, textStartY, textColor)

        g2d.dispose()
        return image
    }

    // =========================================================
    // GLASS style — gloss overlay, subtle border, light shadow
    // =========================================================

    private fun renderGlass(badge: Badge): BufferedImage {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        disableSmoothing(g2d)

        // Subtle light border (white at 20% opacity) or specified border
        if (hasBorder) {
            g2d.color = parseColor(badge.borderColor ?: "#000000")
            g2d.fillRect(0, 0, totalWidth, totalHeight)
        } else {
            // Even without border color, glass gets a subtle white border
            g2d.color = Color(255, 255, 255, 50)
            g2d.fillRect(0, 0, totalWidth, totalHeight)
        }

        // Glass always gets a border (either specified or subtle white)
        val fillX = BORDER_WIDTH
        val fillY = BORDER_WIDTH
        val fillW = if (hasBorder) contentWidth else totalWidth - 2
        val fillH = if (hasBorder) contentHeight else totalHeight - 2

        // Fill background
        drawBackground(g2d, badge, fillX, fillY, fillW, fillH)

        // Glass gloss: white overlay on top 45% of content area
        val glossHeight = (fillH * 0.45).toInt().coerceAtLeast(1)
        val savedComposite = g2d.composite
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
        g2d.color = Color.WHITE
        g2d.fillRect(fillX, fillY, fillW, glossHeight)
        g2d.composite = savedComposite

        // Border radius
        if (badge.borderRadius > 0) {
            applyBorderRadius(image, badge.borderRadius, totalWidth, totalHeight)
        }

        // Text position
        val textStartX = fillX + PADDING_X
        val textStartY = fillY + PADDING_Y

        // Very light shadow
        val shadowColor = Color(0, 0, 0, 40)
        drawPixelText(image, text, textStartX + SHADOW_OFFSET, textStartY + SHADOW_OFFSET, shadowColor)

        // Main text
        val textColor = parseColor(badge.textColor)
        drawPixelText(image, text, textStartX, textStartY, textColor)

        g2d.dispose()
        return image
    }

    // =========================================================
    // METALLIC style — metal sheen gradient, bevel border, outlined text
    // =========================================================

    private fun renderMetallic(badge: Badge): BufferedImage {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        disableSmoothing(g2d)

        // Bevel border: top/left lighter, bottom/right darker
        if (hasBorder) {
            val borderBase = parseColor(badge.borderColor ?: "#808080")
            val borderLight = brightenColor(borderBase, 1.5)
            val borderDark = darkenColor(borderBase, 0.5)

            // Fill with dark (bottom/right)
            g2d.color = borderDark
            g2d.fillRect(0, 0, totalWidth, totalHeight)

            // Top edge (bright)
            g2d.color = borderLight
            g2d.fillRect(0, 0, totalWidth, 1)

            // Left edge (bright)
            g2d.fillRect(0, 0, 1, totalHeight)
        }

        val fillX = if (hasBorder) BORDER_WIDTH else 0
        val fillY = if (hasBorder) BORDER_WIDTH else 0
        val fillW = contentWidth
        val fillH = contentHeight

        // Metallic gradient: bright at top, dark at bottom (metal sheen)
        val bgColor = parseColor(badge.bgColor)
        val brightTop = brightenColor(bgColor, 1.4)
        val darkBottom = darkenColor(bgColor, 0.6)
        val metalGradient = GradientPaint(
            fillX.toFloat(), fillY.toFloat(), brightTop,
            fillX.toFloat(), (fillY + fillH).toFloat(), darkBottom
        )
        g2d.paint = metalGradient
        g2d.fillRect(fillX, fillY, fillW, fillH)

        // Highlight reflection band at ~30% from top
        val highlightY = fillY + (fillH * 0.3).toInt()
        val highlightColor = brightenColor(bgColor, 1.8)
        g2d.color = Color(highlightColor.red, highlightColor.green, highlightColor.blue, 180)
        g2d.fillRect(fillX, highlightY, fillW, 1)

        // Border radius
        if (badge.borderRadius > 0) {
            applyBorderRadius(image, badge.borderRadius, totalWidth, totalHeight)
        }

        // Text position
        val textStartX = fillX + PADDING_X
        val textStartY = fillY + PADDING_Y

        // Text with dark outline: draw text in dark at 4 offsets, then main text on top
        val textColor = parseColor(badge.textColor)
        val outlineColor = darkenColor(textColor, 0.25)
        drawPixelText(image, text, textStartX - 1, textStartY, outlineColor)
        drawPixelText(image, text, textStartX + 1, textStartY, outlineColor)
        drawPixelText(image, text, textStartX, textStartY - 1, outlineColor)
        drawPixelText(image, text, textStartX, textStartY + 1, outlineColor)

        // Bright inner fill
        val brightText = brightenColor(textColor, 1.3)
        drawPixelText(image, text, textStartX, textStartY, brightText)

        g2d.dispose()
        return image
    }

    // =========================================================
    // NEON style — dark bg, glow text, glow border
    // =========================================================

    private fun renderNeon(badge: Badge): BufferedImage {
        val text = badge.displayText.uppercase()
        val hasBorder = badge.borderColor != null
        val textWidth = calculateTextWidth(text)
        val contentWidth = textWidth + (PADDING_X * 2)
        val contentHeight = CHAR_HEIGHT + (PADDING_Y * 2)
        val totalWidth = if (hasBorder) contentWidth + (BORDER_WIDTH * 2) else contentWidth
        val totalHeight = if (hasBorder) contentHeight + (BORDER_WIDTH * 2) else contentHeight

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        disableSmoothing(g2d)

        val bgColor = parseColor(badge.bgColor)

        // Neon border: glow effect with double border
        if (hasBorder) {
            val borderColor = parseColor(badge.borderColor ?: "#000000")
            // Outer glow border (dimmer)
            val glowBorder = Color(borderColor.red, borderColor.green, borderColor.blue, 100)
            g2d.color = glowBorder
            g2d.fillRect(0, 0, totalWidth, totalHeight)
        }

        val fillX = if (hasBorder) BORDER_WIDTH else 0
        val fillY = if (hasBorder) BORDER_WIDTH else 0
        val fillW = contentWidth
        val fillH = contentHeight

        // Very dark background (dark version of bgColor or near-black)
        val darkBg = darkenColor(bgColor, 0.15)
        g2d.color = Color(
            darkBg.red.coerceAtMost(30),
            darkBg.green.coerceAtMost(30),
            darkBg.blue.coerceAtMost(30),
            255
        )
        g2d.fillRect(fillX, fillY, fillW, fillH)

        // Border radius
        if (badge.borderRadius > 0) {
            applyBorderRadius(image, badge.borderRadius, totalWidth, totalHeight)
        }

        // Text position
        val textStartX = fillX + PADDING_X
        val textStartY = fillY + PADDING_Y

        // Glow effect: draw text multiple times in the glow color at low opacity
        // Pass 1: outer glow (the neon halo) — drawn with glow color at low opacity, offset by 1 in all directions
        val glowColor = Color(bgColor.red, bgColor.green, bgColor.blue, 60)
        drawPixelText(image, text, textStartX - 1, textStartY - 1, glowColor)
        drawPixelText(image, text, textStartX + 1, textStartY - 1, glowColor)
        drawPixelText(image, text, textStartX - 1, textStartY + 1, glowColor)
        drawPixelText(image, text, textStartX + 1, textStartY + 1, glowColor)
        drawPixelText(image, text, textStartX - 1, textStartY, glowColor)
        drawPixelText(image, text, textStartX + 1, textStartY, glowColor)
        drawPixelText(image, text, textStartX, textStartY - 1, glowColor)
        drawPixelText(image, text, textStartX, textStartY + 1, glowColor)

        // Pass 2: body glow — brighter glow at center
        val bodyGlow = Color(bgColor.red, bgColor.green, bgColor.blue, 120)
        drawPixelText(image, text, textStartX, textStartY, bodyGlow)

        // Pass 3: core — bright crisp text
        val textColor = parseColor(badge.textColor)
        val brightCore = brightenColor(textColor, 1.3)
        drawPixelText(image, text, textStartX, textStartY, brightCore)

        g2d.dispose()
        return image
    }

    // =========================================================
    // Shared rendering utilities
    // =========================================================

    private fun disableSmoothing(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
    }

    /**
     * Draws the badge background (gradient or solid) in the given fill area.
     */
    private fun drawBackground(g2d: Graphics2D, badge: Badge, fillX: Int, fillY: Int, fillW: Int, fillH: Int) {
        val bgColor = parseColor(badge.bgColor)
        if (badge.gradientColor != null) {
            val gradEnd = parseColor(badge.gradientColor)
            val gradient = GradientPaint(
                fillX.toFloat(), fillY.toFloat(), bgColor,
                (fillX + fillW).toFloat(), fillY.toFloat(), gradEnd
            )
            g2d.paint = gradient
        } else {
            g2d.color = bgColor
        }
        g2d.fillRect(fillX, fillY, fillW, fillH)
    }

    /**
     * Calculates the total pixel width needed for the given text.
     */
    private fun calculateTextWidth(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length * CHAR_WIDTH) + ((text.length - 1) * CHAR_SPACING)
    }

    /**
     * Draws pixel-art text onto the image at the given position.
     */
    private fun drawPixelText(image: BufferedImage, text: String, startX: Int, startY: Int, color: Color) {
        val rgb = color.rgb
        var cursorX = startX

        for (ch in text) {
            val glyph = FONT[ch] ?: FONT['?'] ?: continue
            for (row in glyph.indices) {
                val rowData = glyph[row]
                for (col in rowData.indices) {
                    if (rowData[col]) {
                        val px = cursorX + col
                        val py = startY + row
                        if (px in 0 until image.width && py in 0 until image.height) {
                            // Blend shadow alpha properly
                            if (color.alpha < 255) {
                                val existing = Color(image.getRGB(px, py), true)
                                val blended = blendColors(existing, color)
                                image.setRGB(px, py, blended.rgb)
                            } else {
                                image.setRGB(px, py, rgb)
                            }
                        }
                    }
                }
            }
            cursorX += CHAR_WIDTH + CHAR_SPACING
        }
    }

    /**
     * Blends a foreground color with alpha onto a background color.
     */
    private fun blendColors(bg: Color, fg: Color): Color {
        val alpha = fg.alpha / 255.0
        val r = (fg.red * alpha + bg.red * (1 - alpha)).toInt().coerceIn(0, 255)
        val g = (fg.green * alpha + bg.green * (1 - alpha)).toInt().coerceIn(0, 255)
        val b = (fg.blue * alpha + bg.blue * (1 - alpha)).toInt().coerceIn(0, 255)
        return Color(r, g, b, 255)
    }

    /**
     * Brightens a color by the given factor (>1.0 = brighter).
     */
    private fun brightenColor(color: Color, factor: Double): Color {
        val r = (color.red * factor).toInt().coerceIn(0, 255)
        val g = (color.green * factor).toInt().coerceIn(0, 255)
        val b = (color.blue * factor).toInt().coerceIn(0, 255)
        return Color(r, g, b, color.alpha)
    }

    /**
     * Darkens a color by the given factor (<1.0 = darker).
     */
    private fun darkenColor(color: Color, factor: Double): Color {
        val r = (color.red * factor).toInt().coerceIn(0, 255)
        val g = (color.green * factor).toInt().coerceIn(0, 255)
        val b = (color.blue * factor).toInt().coerceIn(0, 255)
        return Color(r, g, b, color.alpha)
    }

    /**
     * Clears corner pixels to simulate rounded corners (pixel-art style).
     * radius 1 = 1px corner removed, radius 2 = 3px pattern, radius 3 = larger arc.
     */
    private fun applyBorderRadius(image: BufferedImage, radius: Int, width: Int, height: Int) {
        val transparent = 0x00000000
        val corners = generateCornerMask(radius)

        // Top-left
        for ((dy, dx) in corners) {
            if (dx in 0 until width && dy in 0 until height) {
                image.setRGB(dx, dy, transparent)
            }
        }
        // Top-right
        for ((dy, dx) in corners) {
            val px = width - 1 - dx
            if (px in 0 until width && dy in 0 until height) {
                image.setRGB(px, dy, transparent)
            }
        }
        // Bottom-left
        for ((dy, dx) in corners) {
            val py = height - 1 - dy
            if (dx in 0 until width && py in 0 until height) {
                image.setRGB(dx, py, transparent)
            }
        }
        // Bottom-right
        for ((dy, dx) in corners) {
            val px = width - 1 - dx
            val py = height - 1 - dy
            if (px in 0 until width && py in 0 until height) {
                image.setRGB(px, py, transparent)
            }
        }
    }

    /**
     * Generates pixel offsets for a corner mask at the given radius.
     * Returns list of (row, col) pairs to clear.
     */
    private fun generateCornerMask(radius: Int): List<Pair<Int, Int>> {
        return when (radius.coerceIn(0, 3)) {
            1 -> listOf(0 to 0)
            2 -> listOf(0 to 0, 0 to 1, 1 to 0)
            3 -> listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 2 to 0)
            else -> emptyList()
        }
    }

    /**
     * Parses a hex color string (#RRGGBB or #AARRGGBB) into a Color.
     */
    private fun parseColor(hex: String): Color {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            6 -> Color(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
            8 -> Color(
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16),
                clean.substring(6, 8).toInt(16),
                clean.substring(0, 2).toInt(16)
            )
            else -> Color.GRAY
        }
    }

    // =========================================================
    // Animation frame renderers
    // =========================================================

    private fun renderRainbowFrames(badge: Badge, frameCount: Int): List<BufferedImage> {
        return (0 until frameCount).map { i ->
            val hueShift = i * 360.0f / frameCount
            val modified = badge.copy(
                bgColor = shiftHueHex(badge.bgColor, hueShift),
                gradientColor = badge.gradientColor?.let { shiftHueHex(it, hueShift) },
                borderColor = badge.borderColor?.let { shiftHueHex(it, hueShift) }
            )
            renderBadge(modified)
        }
    }

    private fun renderPulseFrames(badge: Badge, frameCount: Int): List<BufferedImage> {
        return (0 until frameCount).map { i ->
            val factor = 0.7 + 0.6 * sin(i * 2.0 * PI / frameCount)
            val modified = badge.copy(
                bgColor = pulseBrightness(badge.bgColor, factor),
                gradientColor = badge.gradientColor?.let { pulseBrightness(it, factor) },
                borderColor = badge.borderColor?.let { pulseBrightness(it, factor) }
            )
            renderBadge(modified)
        }
    }

    private fun renderGlowFrames(badge: Badge, frameCount: Int): List<BufferedImage> {
        val isNeon = badge.style.lowercase() == "neon"
        return (0 until frameCount).map { i ->
            val factor = 0.7 + 0.6 * sin(i * 2.0 * PI / frameCount)
            val modified = if (isNeon) {
                badge.copy(
                    borderColor = badge.borderColor?.let { pulseBrightness(it, factor) },
                    textColor = pulseBrightness(badge.textColor, 0.85 + 0.3 * sin(i * 2.0 * PI / frameCount))
                )
            } else {
                badge.copy(
                    borderColor = badge.borderColor?.let { pulseBrightness(it, factor) }
                )
            }
            renderBadge(modified)
        }
    }

    private fun renderGradientShiftFrames(badge: Badge, frameCount: Int): List<BufferedImage> {
        val effectiveGradient = badge.gradientColor ?: pulseBrightness(badge.bgColor, 0.7)
        return (0 until frameCount).map { i ->
            val t = i.toDouble() / frameCount
            val cyclicT = if (t <= 0.5) t * 2.0 else (1.0 - t) * 2.0
            val modified = badge.copy(
                bgColor = interpolateColor(badge.bgColor, effectiveGradient, cyclicT),
                gradientColor = interpolateColor(effectiveGradient, badge.bgColor, cyclicT)
            )
            renderBadge(modified)
        }
    }

    // =========================================================
    // Animation color utilities
    // =========================================================

    private fun shiftHue(color: Color, degrees: Float): Color {
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        hsb[0] = (hsb[0] + degrees / 360f) % 1f
        if (hsb[0] < 0) hsb[0] += 1f
        val rgb = Color.getHSBColor(hsb[0], hsb[1], hsb[2])
        return Color(rgb.red, rgb.green, rgb.blue, color.alpha)
    }

    private fun shiftHueHex(hex: String, degrees: Float): String {
        val color = parseColor(hex)
        val shifted = shiftHue(color, degrees)
        return "#%02X%02X%02X".format(shifted.red, shifted.green, shifted.blue)
    }

    private fun pulseBrightness(hex: String, factor: Double): String {
        val color = parseColor(hex)
        val r = (color.red * factor).toInt().coerceIn(0, 255)
        val g = (color.green * factor).toInt().coerceIn(0, 255)
        val b = (color.blue * factor).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun interpolateColor(hex1: String, hex2: String, t: Double): String {
        val c1 = parseColor(hex1)
        val c2 = parseColor(hex2)
        val r = (c1.red + (c2.red - c1.red) * t).toInt().coerceIn(0, 255)
        val g = (c1.green + (c2.green - c1.green) * t).toInt().coerceIn(0, 255)
        val b = (c1.blue + (c2.blue - c1.blue) * t).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }
}
