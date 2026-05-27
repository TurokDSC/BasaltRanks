package dev.turok_ss.basaltranks.modules.resourcepack

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import dev.turok_ss.basaltranks.modules.badge.BadgeRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Creates a valid Minecraft resource pack ZIP containing:
 * - pack.mcmeta (pack_format: 34 for 1.21.x)
 * - assets/basaltranks/textures/font/{id}.png (rendered badge PNGs)
 * - assets/minecraft/font/default.json (font providers for each badge)
 *
 * Also computes the SHA-1 hash of the resulting ZIP for resource pack verification.
 */
object PackGenerator {

    private const val PACK_FORMAT = 55
    private const val PACK_FORMAT_MIN = 34
    private const val PACK_FORMAT_MAX = 65
    private const val PACK_DESCRIPTION = "BasaltRanks Badge Pack"

    /**
     * Result of a pack generation containing the file and its SHA-1 hash.
     */
    data class PackResult(
        val file: File,
        val sha1Hash: String,
        val sha1Bytes: ByteArray,
        val badgeCount: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PackResult) return false
            return file == other.file && sha1Hash == other.sha1Hash
        }

        override fun hashCode(): Int {
            var result = file.hashCode()
            result = 31 * result + sha1Hash.hashCode()
            return result
        }
    }

    /**
     * Generates the resource pack ZIP with all current badges.
     *
     * @param plugin The plugin instance for file path resolution
     * @param badges Collection of badges to include in the pack
     * @return PackResult with file reference and SHA-1 hash, or null on failure
     */
    fun generate(plugin: BasaltRanks, badges: Collection<Badge>): PackResult? {
        return try {
            val packDir = File(plugin.dataFolder, "pack")
            if (!packDir.exists()) packDir.mkdirs()

            val packFile = File(packDir, "basaltranks.zip")

            // Build the ZIP in memory first, then write to disk
            val zipBytes = buildZipBytes(badges)

            // Write to file
            packFile.writeBytes(zipBytes)

            // Compute SHA-1
            val digest = MessageDigest.getInstance("SHA-1")
            val sha1Bytes = digest.digest(zipBytes)
            val sha1Hash = sha1Bytes.joinToString("") { "%02x".format(it) }

            plugin.logger.info("Resource pack generated: ${packFile.name} (${zipBytes.size} bytes, SHA-1: $sha1Hash)")

            PackResult(
                file = packFile,
                sha1Hash = sha1Hash,
                sha1Bytes = sha1Bytes,
                badgeCount = badges.size
            )
        } catch (e: Exception) {
            plugin.slF4JLogger.error("Failed to generate resource pack", e)
            null
        }
    }

    /**
     * Builds the entire ZIP file content as a byte array.
     */
    private fun buildZipBytes(badges: Collection<Badge>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // 1. pack.mcmeta
            addEntry(zip, "pack.mcmeta", buildPackMcmeta())

            // 2. Badge PNG textures
            for (badge in badges) {
                val pngBytes = renderBadgeToPng(badge)
                if (pngBytes != null) {
                    addEntry(
                        zip,
                        "assets/basaltranks/textures/font/${badge.id}.png",
                        pngBytes
                    )
                }

                if (badge.animated && badge.frameChars.isNotEmpty()) {
                    val frames = BadgeRenderer.renderAnimationFrames(badge)
                    for ((frameIndex, frame) in frames.withIndex()) {
                        val frameBytes = imageToPng(frame) ?: continue
                        addEntry(
                            zip,
                            "assets/basaltranks/textures/font/${badge.id}_f${frameIndex}.png",
                            frameBytes
                        )
                    }
                }
            }

            // 3. Font configuration (default.json)
            val fontJson = FontConfigBuilder.build(badges)
            addEntry(zip, "assets/minecraft/font/default.json", fontJson.toByteArray(Charsets.UTF_8))
        }
        return baos.toByteArray()
    }

    /**
     * Creates the pack.mcmeta JSON content.
     */
    private fun buildPackMcmeta(): ByteArray {
        val json = """
            {
              "pack": {
                "pack_format": $PACK_FORMAT,
                "supported_formats": {"min_inclusive": $PACK_FORMAT_MIN, "max_inclusive": $PACK_FORMAT_MAX},
                "description": "$PACK_DESCRIPTION"
              }
            }
        """.trimIndent()
        return json.toByteArray(Charsets.UTF_8)
    }

    /**
     * Renders a badge to PNG bytes using BadgeRenderer.
     */
    private fun renderBadgeToPng(badge: Badge): ByteArray? {
        return try {
            imageToPng(BadgeRenderer.renderBadge(badge))
        } catch (e: Exception) {
            null
        }
    }

    private fun imageToPng(image: java.awt.image.BufferedImage): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", baos)
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Adds a single entry to the ZIP output stream.
     */
    private fun addEntry(zip: ZipOutputStream, path: String, data: ByteArray) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }
}
