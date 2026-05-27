package dev.turok_ss.basaltranks.modules.web

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import dev.turok_ss.basaltranks.modules.badge.BadgeRenderer
import dev.turok_ss.basaltranks.modules.badge.BadgeTemplate
import dev.turok_ss.basaltranks.utils.FoliaCompat
import org.bukkit.Bukkit
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Handles all REST API requests for the web badge editor.
 *
 * Routes:
 *  GET  /api/badges             -> list all badges
 *  POST /api/badges             -> create a new badge
 *  PUT  /api/badges/{id}        -> update a badge
 *  DELETE /api/badges/{id}      -> delete a badge
 *  GET  /api/badges/{id}/preview -> render badge as PNG
 *  GET  /api/templates          -> list all templates
 *  POST /api/badges/from-template -> create badge from template
 *  GET  /api/players            -> list online players
 *  POST /api/assign             -> assign badge to player
 *  POST /api/remove             -> remove badge from player
 *  POST /api/regenerate         -> regenerate resource pack
 *  GET  /api/preview            -> render preview from query params (no save)
 *  GET  /api/luckperms/status   -> LuckPerms connection status + groups
 *  GET  /api/luckperms/groups   -> group-to-badge mappings
 *  PUT  /api/luckperms/groups   -> update group-to-badge mappings
 */
class WebApiHandler(
    private val plugin: BasaltRanks,
    private val password: String,
    private val isWebActive: () -> Boolean
) {

    private val failedAuthAttempts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val AUTH_COOLDOWN_MS = 5_000L
    private val SYNC_TIMEOUT_SECONDS = 5L

    private fun <T> runSync(task: () -> T): T {
        if (Bukkit.isPrimaryThread()) return task()
        val future = java.util.concurrent.CompletableFuture<T>()
        FoliaCompat.runTask(plugin, Runnable {
            try {
                future.complete(task())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        })
        return future.get(SYNC_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
    }

    /**
     * Main entry point for all /api/ requests.
     */
    fun handle(exchange: HttpExchange) {
        val origin = exchange.requestHeaders.getFirst("Origin")
        if (origin != null && isAllowedOrigin(origin)) {
            exchange.responseHeaders["Access-Control-Allow-Origin"] = listOf(origin)
            exchange.responseHeaders["Access-Control-Allow-Credentials"] = listOf("true")
        }
        exchange.responseHeaders["Access-Control-Allow-Methods"] = listOf("GET, POST, PUT, DELETE, OPTIONS")
        exchange.responseHeaders["Access-Control-Allow-Headers"] = listOf("Content-Type, Authorization")
        exchange.responseHeaders["X-Content-Type-Options"] = listOf("nosniff")

        // Handle preflight OPTIONS
        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        // Defense-in-depth: reject requests if web editor is disabled
        if (!isWebActive()) {
            sendJson(exchange, 503, """{"error":"Web editor is disabled. Enable with /badge web on"}""")
            return
        }

        val clientIp = exchange.remoteAddress.address.hostAddress
        if (failedAuthAttempts.size > 100) {
            val now = System.currentTimeMillis()
            failedAuthAttempts.entries.removeIf { now - it.value > AUTH_COOLDOWN_MS }
        }
        val lastFail = failedAuthAttempts[clientIp]
        if (lastFail != null && System.currentTimeMillis() - lastFail < AUTH_COOLDOWN_MS) {
            sendJson(exchange, 429, """{"error":"Too many failed attempts. Try again later."}""")
            return
        }

        // Check authorization
        if (!checkAuth(exchange)) {
            failedAuthAttempts[clientIp] = System.currentTimeMillis()
            sendJson(exchange, 401, """{"error":"Unauthorized. Provide Authorization: Bearer <password>"}""")
            return
        }
        // Clear failed attempts on successful auth
        failedAuthAttempts.remove(clientIp)

        val path = exchange.requestURI.path.removePrefix("/api").trimEnd('/')
        val method = exchange.requestMethod

        try {
            when {
                // GET /api/preview?displayText=...&bgColor=...
                path == "/preview" && method == "GET" -> handlePreviewFromParams(exchange)

                // GET /api/badges
                path == "/badges" && method == "GET" -> handleListBadges(exchange)

                // POST /api/badges
                path == "/badges" && method == "POST" -> handleCreateBadge(exchange)

                // POST /api/badges/from-template
                path == "/badges/from-template" && method == "POST" -> handleCreateFromTemplate(exchange)

                // GET /api/badges/{id}/preview
                path.matches(BADGE_PREVIEW_ROUTE) && method == "GET" -> {
                    val badgeId = path.removePrefix("/badges/").removeSuffix("/preview")
                    handleBadgePreview(exchange, badgeId)
                }

                // PUT /api/badges/{id}
                path.matches(BADGE_ID_ROUTE) && method == "PUT" -> {
                    val badgeId = path.removePrefix("/badges/")
                    handleUpdateBadge(exchange, badgeId)
                }

                // DELETE /api/badges/{id}
                path.matches(BADGE_ID_ROUTE) && method == "DELETE" -> {
                    val badgeId = path.removePrefix("/badges/")
                    handleDeleteBadge(exchange, badgeId)
                }

                // GET /api/templates
                path == "/templates" && method == "GET" -> handleListTemplates(exchange)

                // GET /api/players
                path == "/players" && method == "GET" -> handleListPlayers(exchange)

                // POST /api/assign
                path == "/assign" && method == "POST" -> handleAssignBadge(exchange)

                // POST /api/remove
                path == "/remove" && method == "POST" -> handleRemoveBadge(exchange)

                // POST /api/regenerate
                path == "/regenerate" && method == "POST" -> handleRegenerate(exchange)

                // GET /api/luckperms/status
                path == "/luckperms/status" && method == "GET" -> handleLuckPermsStatus(exchange)

                // GET /api/luckperms/groups
                path == "/luckperms/groups" && method == "GET" -> handleLuckPermsGroups(exchange)

                // PUT /api/luckperms/groups
                path == "/luckperms/groups" && method == "PUT" -> handleUpdateLuckPermsGroups(exchange)

                else -> sendJson(exchange, 404, """{"error":"Not found: $method $path"}""")
            }
        } catch (e: Exception) {
            plugin.logger.warning("API error on $method $path: ${e.message}")
            sendJson(exchange, 500, """{"error":"${escapeJson(e.message ?: "Unknown error")}"}""")
        }
    }

    // -----------------------------------------------
    // Auth
    // -----------------------------------------------

    private fun checkAuth(exchange: HttpExchange): Boolean {
        if (password.isBlank()) return true
        val authHeader = exchange.requestHeaders.getFirst("Authorization") ?: return false
        val token = authHeader.removePrefix("Bearer ").trim()
        return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8))
    }

    private fun isAllowedOrigin(origin: String): Boolean {
        val host = try { java.net.URI(origin).host ?: "" } catch (_: Exception) { "" }
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        val serverIp = plugin.config.getString("resourcepack.server-ip", "localhost") ?: "localhost"
        return host == serverIp
    }

    // -----------------------------------------------
    // Badge CRUD handlers
    // -----------------------------------------------

    private fun handleListBadges(exchange: HttpExchange) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val badges = badgeModule.getAllBadges()
        val json = badges.joinToString(",", "[", "]") { badgeToJson(it) }
        sendJson(exchange, 200, json)
    }

    private fun handleCreateBadge(exchange: HttpExchange) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val body = readBody(exchange)
        val id = parseJsonString(body, "id")
        val displayText = parseJsonString(body, "displayText")
        val bgColor = parseJsonString(body, "bgColor")
        val textColor = parseJsonString(body, "textColor")

        if (id.isNullOrBlank() || displayText.isNullOrBlank() || bgColor.isNullOrBlank() || textColor.isNullOrBlank()) {
            sendJson(exchange, 400, """{"error":"Missing required fields: id, displayText, bgColor, textColor"}""")
            return
        }

        // Validate field lengths
        if (id.length > 32) {
            sendJson(exchange, 400, """{"error":"Badge ID must be 32 characters or less"}""")
            return
        }
        if (displayText.length > 16) {
            sendJson(exchange, 400, """{"error":"Display text must be 16 characters or less"}""")
            return
        }

        if (!badgeModule.isValidHexColor(bgColor)) {
            sendJson(exchange, 400, """{"error":"Invalid bgColor format. Use #RRGGBB"}""")
            return
        }
        if (!badgeModule.isValidHexColor(textColor)) {
            sendJson(exchange, 400, """{"error":"Invalid textColor format. Use #RRGGBB"}""")
            return
        }

        val gradientColor = parseJsonString(body, "gradientColor")
        val borderColor = parseJsonString(body, "borderColor")
        val borderRadius = parseJsonInt(body, "borderRadius") ?: 0
        val priority = parseJsonInt(body, "priority") ?: 50
        val style = parseJsonString(body, "style") ?: "flat"
        val animated = parseJsonBoolean(body, "animated") ?: false
        val animationType = parseJsonString(body, "animationType") ?: "none"
        val animationSpeed = parseJsonInt(body, "animationSpeed") ?: 10
        val animationFrames = parseJsonInt(body, "animationFrames") ?: 6

        val badge = badgeModule.createBadge(
            id = id,
            displayText = displayText,
            bgColor = bgColor,
            textColor = textColor,
            gradientColor = gradientColor?.takeIf { it.isNotBlank() && badgeModule.isValidHexColor(it) },
            borderColor = borderColor?.takeIf { it.isNotBlank() && badgeModule.isValidHexColor(it) },
            borderRadius = borderRadius,
            priority = priority,
            style = style,
            animated = animated,
            animationType = animationType,
            animationSpeed = animationSpeed,
            animationFrames = animationFrames
        )

        if (badge == null) {
            sendJson(exchange, 409, """{"error":"Badge with id '$id' already exists"}""")
            return
        }

        // Render the badge image
        badgeModule.renderAndSave(badge.id)

        // Auto-regenerate resource pack
        autoRegenerate()

        sendJson(exchange, 201, badgeToJson(badge))
    }

    private fun handleUpdateBadge(exchange: HttpExchange, badgeId: String) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val body = readBody(exchange)

        val displayText = parseJsonString(body, "displayText")
        val bgColor = parseJsonString(body, "bgColor")
        val textColor = parseJsonString(body, "textColor")
        val gradientColor = parseJsonString(body, "gradientColor")
        val borderColor = parseJsonString(body, "borderColor")
        val borderRadius = parseJsonInt(body, "borderRadius")
        val priority = parseJsonInt(body, "priority")
        val clearGradient = parseJsonBoolean(body, "clearGradient") ?: false
        val clearBorder = parseJsonBoolean(body, "clearBorder") ?: false
        val style = parseJsonString(body, "style")
        val animated = parseJsonBoolean(body, "animated")
        val animationType = parseJsonString(body, "animationType")
        val animationSpeed = parseJsonInt(body, "animationSpeed")
        val animationFrames = parseJsonInt(body, "animationFrames")

        // Validate text length if provided
        if (displayText != null && displayText.length > 16) {
            sendJson(exchange, 400, """{"error":"Display text must be 16 characters or less"}""")
            return
        }

        // Validate colors if provided
        if (bgColor != null && !badgeModule.isValidHexColor(bgColor)) {
            sendJson(exchange, 400, """{"error":"Invalid bgColor format. Use #RRGGBB"}""")
            return
        }
        if (textColor != null && !badgeModule.isValidHexColor(textColor)) {
            sendJson(exchange, 400, """{"error":"Invalid textColor format. Use #RRGGBB"}""")
            return
        }

        val badge = badgeModule.updateBadge(
            id = badgeId,
            displayText = displayText,
            bgColor = bgColor,
            textColor = textColor,
            gradientColor = gradientColor?.takeIf { it.isNotBlank() },
            borderColor = borderColor?.takeIf { it.isNotBlank() },
            borderRadius = borderRadius,
            priority = priority,
            clearGradient = clearGradient,
            clearBorder = clearBorder,
            style = style,
            animated = animated,
            animationType = animationType,
            animationSpeed = animationSpeed,
            animationFrames = animationFrames
        )

        if (badge == null) {
            sendJson(exchange, 404, """{"error":"Badge '$badgeId' not found"}""")
            return
        }

        // Re-render the badge image
        badgeModule.renderAndSave(badge.id)

        // Auto-regenerate resource pack
        autoRegenerate()

        sendJson(exchange, 200, badgeToJson(badge))
    }

    private fun handleDeleteBadge(exchange: HttpExchange, badgeId: String) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val deleted = badgeModule.deleteBadge(badgeId)
        if (!deleted) {
            sendJson(exchange, 404, """{"error":"Badge '$badgeId' not found"}""")
            return
        }

        // Auto-regenerate resource pack
        autoRegenerate()

        sendJson(exchange, 200, """{"success":true,"message":"Badge '$badgeId' deleted"}""")
    }

    // -----------------------------------------------
    // Preview handlers
    // -----------------------------------------------

    private fun handleBadgePreview(exchange: HttpExchange, badgeId: String) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val badge = badgeModule.getBadge(badgeId)
        if (badge == null) {
            sendJson(exchange, 404, """{"error":"Badge '$badgeId' not found"}""")
            return
        }

        sendBadgeImage(exchange, badge)
    }

    /**
     * Renders a preview from query parameters without saving.
     * Used for live preview in the editor.
     */
    private fun handlePreviewFromParams(exchange: HttpExchange) {
        val params = parseQueryParams(exchange.requestURI.query ?: "")

        val displayText = params["displayText"] ?: "BADGE"
        val bgColor = params["bgColor"] ?: "#808080"
        val textColor = params["textColor"] ?: "#FFFFFF"
        val gradientColor = params["gradientColor"]?.takeIf { it.isNotBlank() && it != "null" }
        val borderColor = params["borderColor"]?.takeIf { it.isNotBlank() && it != "null" }
        val borderRadius = params["borderRadius"]?.toIntOrNull() ?: 0
        val priority = params["priority"]?.toIntOrNull() ?: 50

        val style = params["style"] ?: "flat"
        val animated = params["animated"]?.toBooleanStrictOrNull() ?: false
        val animationType = params["animationType"] ?: "none"

        val badge = Badge(
            id = "preview",
            displayText = displayText.uppercase(),
            bgColor = if (bgColor.startsWith("#")) bgColor else "#$bgColor",
            textColor = if (textColor.startsWith("#")) textColor else "#$textColor",
            gradientColor = gradientColor?.let { if (it.startsWith("#")) it else "#$it" },
            borderColor = borderColor?.let { if (it.startsWith("#")) it else "#$it" },
            borderRadius = borderRadius.coerceIn(0, 3),
            priority = priority,
            style = Badge.normalizeStyle(style),
            animated = animated,
            animationType = Badge.normalizeAnimation(animationType)
        )

        sendBadgeImage(exchange, badge)
    }

    private fun sendBadgeImage(exchange: HttpExchange, badge: Badge) {
        val image = BadgeRenderer.renderBadge(badge)
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        val bytes = baos.toByteArray()

        exchange.responseHeaders["Content-Type"] = listOf("image/png")
        exchange.responseHeaders["Cache-Control"] = listOf("no-cache")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // -----------------------------------------------
    // Template handlers
    // -----------------------------------------------

    private fun handleListTemplates(exchange: HttpExchange) {
        val templates = BadgeTemplate.getAll()
        val json = templates.joinToString(",", "[", "]") { templateToJson(it) }
        sendJson(exchange, 200, json)
    }

    private fun handleCreateFromTemplate(exchange: HttpExchange) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val body = readBody(exchange)
        val templateId = parseJsonString(body, "templateId")
        val customId = parseJsonString(body, "customId")

        if (templateId.isNullOrBlank()) {
            sendJson(exchange, 400, """{"error":"Missing required field: templateId"}""")
            return
        }

        val badge = badgeModule.createFromTemplate(templateId, customId?.takeIf { it.isNotBlank() })
        if (badge == null) {
            sendJson(exchange, 409, """{"error":"Template '$templateId' not found or badge already exists"}""")
            return
        }

        // Render the badge image
        badgeModule.renderAndSave(badge.id)

        // Auto-regenerate resource pack
        autoRegenerate()

        sendJson(exchange, 201, badgeToJson(badge))
    }

    // -----------------------------------------------
    // Player handlers
    // -----------------------------------------------

    private fun handleListPlayers(exchange: HttpExchange) {
        val json = runSync {
            val badgeModule = plugin.getBadgeModule()
            val players = Bukkit.getOnlinePlayers().map { player ->
                val prefixId = badgeModule?.getPlayerBadgeId(player.uniqueId)
                val prefixName = prefixId?.let { badgeModule?.getBadge(it)?.displayText }
                val suffixId = badgeModule?.getPlayerSuffixBadgeId(player.uniqueId)
                val suffixName = suffixId?.let { badgeModule?.getBadge(it)?.displayText }
                buildString {
                    append("{")
                    append("\"name\":\"${escapeJson(player.name)}\",")
                    append("\"uuid\":\"${player.uniqueId}\",")
                    append("\"badge\":${if (prefixId != null) "\"${escapeJson(prefixId)}\"" else "null"},")
                    append("\"badgeName\":${if (prefixName != null) "\"${escapeJson(prefixName)}\"" else "null"},")
                    append("\"suffixBadge\":${if (suffixId != null) "\"${escapeJson(suffixId)}\"" else "null"},")
                    append("\"suffixBadgeName\":${if (suffixName != null) "\"${escapeJson(suffixName)}\"" else "null"}")
                    append("}")
                }
            }
            players.joinToString(",", "[", "]")
        }
        sendJson(exchange, 200, json)
    }

    private fun handleAssignBadge(exchange: HttpExchange) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val body = readBody(exchange)
        val playerName = parseJsonString(body, "player")
        val badgeId = parseJsonString(body, "badgeId")
        val slot = parseJsonString(body, "slot") ?: "prefix"

        if (playerName.isNullOrBlank() || badgeId.isNullOrBlank()) {
            sendJson(exchange, 400, """{"error":"Missing required fields: player, badgeId"}""")
            return
        }

        if (slot != "prefix" && slot != "suffix") {
            sendJson(exchange, 400, """{"error":"Invalid slot. Must be 'prefix' or 'suffix'"}""")
            return
        }

        val result = runSync {
            val player = Bukkit.getPlayerExact(playerName)
                ?: return@runSync """{"error":"Player '$playerName' not found or offline"}""" to 404

            val success = badgeModule.assignBadge(player.uniqueId, badgeId, slot)
            if (!success) {
                return@runSync """{"error":"Badge '$badgeId' not found"}""" to 404
            }

            plugin.getDisplayModule()?.updatePlayerDisplay(player)
            """{"success":true,"message":"Badge '$badgeId' assigned as $slot to '${escapeJson(playerName)}'"}""" to 200
        }

        sendJson(exchange, result.second, result.first)
    }

    private fun handleRemoveBadge(exchange: HttpExchange) {
        val badgeModule = plugin.getBadgeModule()
        if (badgeModule == null) {
            sendJson(exchange, 503, """{"error":"Badge module not available"}""")
            return
        }

        val body = readBody(exchange)
        val playerName = parseJsonString(body, "player")
        val slot = parseJsonString(body, "slot")

        if (playerName.isNullOrBlank()) {
            sendJson(exchange, 400, """{"error":"Missing required field: player"}""")
            return
        }

        if (slot != null && slot != "prefix" && slot != "suffix") {
            sendJson(exchange, 400, """{"error":"Invalid slot. Must be 'prefix', 'suffix', or omitted for both"}""")
            return
        }

        val result = runSync {
            val player = Bukkit.getPlayerExact(playerName)
                ?: return@runSync """{"error":"Player '$playerName' not found or offline"}""" to 404

            val removed = badgeModule.removeBadge(player.uniqueId, slot)
            if (!removed) {
                return@runSync """{"error":"Player '${escapeJson(playerName)}' has no badge assigned in the specified slot"}""" to 404
            }

            val hasAnyBadge = badgeModule.getPlayerBadge(player.uniqueId) != null ||
                badgeModule.getPlayerSuffixBadge(player.uniqueId) != null
            if (hasAnyBadge) {
                plugin.getDisplayModule()?.updatePlayerDisplay(player)
            } else {
                plugin.getDisplayModule()?.removePlayerDisplay(player)
            }
            val slotLabel = slot ?: "all"
            """{"success":true,"message":"Badge ($slotLabel) removed from '${escapeJson(playerName)}'"}""" to 200
        }

        sendJson(exchange, result.second, result.first)
    }

    // -----------------------------------------------
    // LuckPerms handlers
    // -----------------------------------------------

    /**
     * GET /api/luckperms/status
     * Returns LuckPerms availability and all group names.
     */
    private fun handleLuckPermsStatus(exchange: HttpExchange) {
        val lpModule = plugin.getLuckPermsModule()
        if (lpModule == null) {
            sendJson(exchange, 200, """{"available":false,"groups":[]}""")
            return
        }

        val available = lpModule.isLuckPermsAvailable()
        val groups = if (available) lpModule.getAllGroups() else emptyList()
        val groupsJson = groups.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }

        sendJson(exchange, 200, """{"available":$available,"groups":$groupsJson}""")
    }

    /**
     * GET /api/luckperms/groups
     * Returns all LuckPerms groups with their current badge mapping.
     */
    private fun handleLuckPermsGroups(exchange: HttpExchange) {
        val lpModule = plugin.getLuckPermsModule()
        if (lpModule == null) {
            sendJson(exchange, 503, """{"error":"LuckPerms module not available"}""")
            return
        }

        val allGroups = lpModule.getAllGroups()
        val mappings = lpModule.getGroupBadgeMappings()

        val json = allGroups.joinToString(",", "[", "]") { group ->
            val badge = mappings[group] ?: ""
            """{"group":"${escapeJson(group)}","badge":"${escapeJson(badge)}"}"""
        }

        sendJson(exchange, 200, json)
    }

    /**
     * PUT /api/luckperms/groups
     * Updates group-to-badge mappings from JSON body:
     * {"mappings": {"owner": "owner", "admin": "admin", "default": ""}}
     */
    private fun handleUpdateLuckPermsGroups(exchange: HttpExchange) {
        val lpModule = plugin.getLuckPermsModule()
        if (lpModule == null) {
            sendJson(exchange, 503, """{"error":"LuckPerms module not available"}""")
            return
        }

        val body = readBody(exchange)
        val mappings = parseJsonStringMap(body, "mappings")

        if (mappings == null) {
            sendJson(exchange, 400, """{"error":"Missing or invalid 'mappings' object in request body"}""")
            return
        }

        // Validate: badge IDs that are non-empty should reference existing badges
        val badgeModule = plugin.getBadgeModule()
        val invalidBadges = mutableListOf<String>()
        for ((group, badgeId) in mappings) {
            if (badgeId.isNotBlank() && badgeModule?.getBadge(badgeId) == null) {
                invalidBadges.add("$group -> $badgeId")
            }
        }

        if (invalidBadges.isNotEmpty()) {
            sendJson(
                exchange, 400,
                """{"error":"Badge(s) not found: ${escapeJson(invalidBadges.joinToString(", "))}"}"""
            )
            return
        }

        // Update config and reload mappings
        lpModule.updateMappings(mappings)

        // Refresh all online players on the main thread
        FoliaCompat.runTask(plugin, Runnable {
            lpModule.refreshAllPlayers()
        })

        sendJson(exchange, 200, """{"success":true,"message":"Updated ${mappings.size} group-badge mappings"}""")
    }

    // -----------------------------------------------
    // Regenerate
    // -----------------------------------------------

    private fun handleRegenerate(exchange: HttpExchange) {
        val rpModule = plugin.getResourcePackModule()
        if (rpModule == null) {
            sendJson(exchange, 503, """{"error":"ResourcePack module not available"}""")
            return
        }

        // Render all badges (already on HTTP thread, not main thread).
        // Then schedule pack regeneration on main thread (Bukkit API requirement).
        val badgeModule = plugin.getBadgeModule()
        val count = badgeModule?.renderAll() ?: 0

        FoliaCompat.runTask(plugin, Runnable {
            rpModule.regeneratePack()
        })

        sendJson(exchange, 200, """{"success":true,"message":"Regenerated resource pack with $count badges"}""")
    }

    // -----------------------------------------------
    // Auto-regeneration helper
    // -----------------------------------------------

    private fun autoRegenerate() {
        val autoRegen = plugin.config.getBoolean("resourcepack.auto-regenerate", true)
        if (!autoRegen) return

        val rpModule = plugin.getResourcePackModule() ?: return

        FoliaCompat.runTask(plugin, Runnable {
            rpModule.regeneratePack()
        })
    }

    // -----------------------------------------------
    // JSON serialization helpers
    // -----------------------------------------------

    private fun badgeToJson(badge: Badge): String = buildString {
        append("{")
        append("\"id\":\"${escapeJson(badge.id)}\",")
        append("\"displayText\":\"${escapeJson(badge.displayText)}\",")
        append("\"bgColor\":\"${escapeJson(badge.bgColor)}\",")
        append("\"textColor\":\"${escapeJson(badge.textColor)}\",")
        append("\"gradientColor\":${nullableString(badge.gradientColor)},")
        append("\"borderColor\":${nullableString(badge.borderColor)},")
        append("\"borderRadius\":${badge.borderRadius},")
        append("\"priority\":${badge.priority},")
        append("\"unicodeChar\":${badge.unicodeChar.code},")
        append("\"style\":\"${escapeJson(badge.style)}\",")
        append("\"animated\":${badge.animated},")
        append("\"animationType\":\"${escapeJson(badge.animationType)}\",")
        append("\"animationSpeed\":${badge.animationSpeed},")
        append("\"animationFrames\":${badge.animationFrames}")
        append("}")
    }

    private fun templateToJson(template: BadgeTemplate): String = buildString {
        append("{")
        append("\"id\":\"${escapeJson(template.id)}\",")
        append("\"displayText\":\"${escapeJson(template.displayText)}\",")
        append("\"bgColor\":\"${escapeJson(template.bgColor)}\",")
        append("\"textColor\":\"${escapeJson(template.textColor)}\",")
        append("\"gradientColor\":${nullableString(template.gradientColor)},")
        append("\"borderColor\":${nullableString(template.borderColor)},")
        append("\"borderRadius\":${template.borderRadius},")
        append("\"category\":\"${escapeJson(template.category)}\",")
        append("\"style\":\"${escapeJson(template.style)}\"")
        append("}")
    }

    private fun nullableString(value: String?): String =
        if (value != null) "\"${escapeJson(value)}\"" else "null"

    // -----------------------------------------------
    // JSON parsing (Gson — bundled in Paper runtime)
    // -----------------------------------------------

    private fun parseBody(json: String): JsonObject? {
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonString(json: String, key: String): String? {
        val obj = parseBody(json) ?: return null
        val el = obj.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else null
    }

    private fun parseJsonInt(json: String, key: String): Int? {
        val obj = parseBody(json) ?: return null
        val el = obj.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) el.asInt else null
    }

    private fun parseJsonBoolean(json: String, key: String): Boolean? {
        val obj = parseBody(json) ?: return null
        val el = obj.get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) el.asBoolean else null
    }

    private fun parseJsonStringMap(json: String, key: String): Map<String, String>? {
        val obj = parseBody(json) ?: return null
        val mapObj = obj.getAsJsonObject(key) ?: return null
        val result = mutableMapOf<String, String>()
        for ((k, v) in mapObj.entrySet()) {
            result[k] = if (v.isJsonPrimitive) v.asString else ""
        }
        return result
    }

    // -----------------------------------------------
    // HTTP helpers
    // -----------------------------------------------

    companion object {
        private const val MAX_BODY_SIZE = 64 * 1024
        private val BADGE_PREVIEW_ROUTE = Regex("/badges/[^/]+/preview")
        private val BADGE_ID_ROUTE = Regex("/badges/[^/]+")
    }

    private fun readBody(exchange: HttpExchange): String {
        return exchange.requestBody.bufferedReader().use { reader ->
            val buffer = CharArray(MAX_BODY_SIZE)
            val charsRead = reader.read(buffer, 0, MAX_BODY_SIZE)
            if (charsRead < 0) "" else String(buffer, 0, charsRead)
        }
    }

    private fun sendJson(exchange: HttpExchange, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders["Content-Type"] = listOf("application/json; charset=utf-8")
        exchange.responseHeaders["Cache-Control"] = listOf("no-cache")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                key to value
            } else null
        }.toMap()
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                else -> if (ch.code in 0x00..0x1F) {
                    sb.append("\\u%04x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }
}
