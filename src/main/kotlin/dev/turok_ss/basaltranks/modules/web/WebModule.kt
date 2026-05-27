package dev.turok_ss.basaltranks.modules.web

import com.sun.net.httpserver.HttpExchange
import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.Module

/**
 * Web editor module: registers HTTP routes on the embedded server
 * to serve the web-based badge editor UI and REST API.
 *
 * Depends on ResourcePackModule for the HTTP server instance.
 *
 * Routes registered (only when web is active):
 *  /api/  -> REST API (handled by WebApiHandler)
 *  /web   -> Static web files (index.html, editor.css, editor.js)
 *  /editor -> Alias for /web
 *
 * The /pack endpoint on PackServer is NEVER affected by this module's toggle.
 *
 * Supports runtime enable/disable via /badge web on|off:
 *  - When OFF: no routes registered, no web resources in memory
 *  - When ON: routes registered, HTML/CSS/JS cached in memory
 */
class WebModule(private val plugin: BasaltRanks) : Module {

    override val name: String = "Web"
    override val configKey: String = "web-editor"

    private var apiHandler: WebApiHandler? = null

    /** Whether the web editor routes and resources are currently active */
    private var webActive = false

    /** Cached web resource files loaded from the JAR — only populated when web is active */
    private val cachedResources = mutableMapOf<String, ByteArray>()

    // -----------------------------------------------
    // Module lifecycle
    // -----------------------------------------------

    override fun onEnable() {
        val rpModule = plugin.getResourcePackModule()
        if (rpModule == null) {
            plugin.logger.warning("WebModule: ResourcePackModule not available, web editor disabled")
            return
        }

        val autoStart = plugin.config.getBoolean("web-editor.auto-start", true)

        if (autoStart) {
            enableWeb()
        } else {
            plugin.logger.info("WebModule loaded - Web editor OFF. Enable with /badge web on")
        }
    }

    override fun onDisable() {
        if (webActive) {
            disableWeb()
        }
        plugin.logger.info("WebModule disabled")
    }

    // -----------------------------------------------
    // Runtime toggle API
    // -----------------------------------------------

    /**
     * Enables the web editor: loads web resources into memory,
     * creates the API handler, and registers HTTP routes on PackServer.
     */
    fun enableWeb() {
        if (webActive) return

        val rpModule = plugin.getResourcePackModule()
        if (rpModule == null) {
            plugin.logger.warning("WebModule: Cannot enable web — ResourcePackModule not available")
            return
        }

        val password = plugin.config.getString("web-editor.password", "change-me") ?: "change-me"
        if (password == "change-me") {
            plugin.logger.warning("======================================================")
            plugin.logger.warning("  Web editor password is set to 'change-me'!")
            plugin.logger.warning("  Change 'web-editor.password' in config.yml!")
            plugin.logger.warning("======================================================")
        }

        apiHandler = WebApiHandler(plugin, password, isWebActive = { webActive })
        val packServer = rpModule.getPackServer()

        // Register API route — handles all /api/ requests
        packServer.registerContext("/api/") { exchange ->
            apiHandler?.handle(exchange)
        }

        // Register static file route — handles /web and sub-paths
        packServer.registerContext("/web") { exchange ->
            handleStaticFile(exchange)
        }

        // Register editor alias
        packServer.registerContext("/editor") { exchange ->
            handleStaticFile(exchange)
        }

        // Load web resources into memory
        preloadResources()

        webActive = true

        val port = rpModule.getPort()
        val serverIp = plugin.config.getString("resourcepack.server-ip", "localhost") ?: "localhost"
        val url = "http://$serverIp:$port/web"
        plugin.logger.info("Web editor enabled at $url")
        plugin.logger.info("Password: ${maskPassword(password)} (change with /badge web password <new>)")
    }

    /**
     * Disables the web editor: removes HTTP routes from PackServer
     * and releases web resources from memory.
     * The /pack endpoint is NEVER affected.
     */
    fun disableWeb() {
        if (!webActive) return

        val rpModule = plugin.getResourcePackModule()
        rpModule?.getPackServer()?.let { packServer ->
            packServer.removeContext("/api/")
            packServer.removeContext("/web")
            packServer.removeContext("/editor")
        }

        // Calculate memory freed before clearing
        val totalBytes = cachedResources.values.sumOf { it.size }
        val totalKb = totalBytes / 1024

        cachedResources.clear()
        apiHandler = null
        webActive = false

        plugin.logger.info("Web editor disabled. Resource pack still active at /pack")
        plugin.logger.info("Released web resources (~${totalKb}kb freed)")
    }

    /**
     * Returns whether the web editor is currently active and serving requests.
     */
    fun isWebActive(): Boolean = webActive

    /**
     * Returns the current web editor password from config.
     */
    fun getPassword(): String {
        return plugin.config.getString("web-editor.password", "change-me") ?: "change-me"
    }

    /**
     * Returns the web editor URL based on current server config.
     */
    fun getWebUrl(): String {
        val rpModule = plugin.getResourcePackModule() ?: return "N/A"
        val serverIp = plugin.config.getString("resourcepack.server-ip", "localhost") ?: "localhost"
        val port = rpModule.getPort()
        return "http://$serverIp:$port/web"
    }

    /**
     * Masks a password for display: shows first 2 and last 2 chars.
     * Example: "change-me" -> "ch*****me"
     */
    fun maskPassword(password: String): String {
        if (password.length <= 4) return "****"
        return password.take(2) + "*".repeat(password.length - 4) + password.takeLast(2)
    }

    // -----------------------------------------------
    // Static file serving
    // -----------------------------------------------

    private fun preloadResources() {
        val files = listOf("index.html", "editor.css", "editor.js")
        for (fileName in files) {
            val resource = plugin.getResource("web/$fileName")
            if (resource != null) {
                cachedResources[fileName] = resource.readBytes()
                plugin.logger.info("WebModule: Loaded web/$fileName (${cachedResources[fileName]?.size ?: 0} bytes)")
            } else {
                plugin.logger.warning("WebModule: Missing resource web/$fileName")
            }
        }
    }

    private fun handleStaticFile(exchange: HttpExchange) {
        // Defense-in-depth: if web is off, reject requests
        if (!webActive) {
            val response = "Web editor is disabled. Enable with /badge web on".toByteArray()
            exchange.sendResponseHeaders(503, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            return
        }

        if (exchange.requestMethod != "GET") {
            val response = "Method not allowed".toByteArray()
            exchange.sendResponseHeaders(405, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            return
        }

        val path = exchange.requestURI.path

        // Determine which file to serve
        val (fileName, contentType) = when {
            path == "/web" || path == "/web/" || path == "/editor" || path == "/editor/" || path == "/" ->
                "index.html" to "text/html; charset=utf-8"
            path.endsWith("/editor.css") || path.endsWith("/style.css") ->
                "editor.css" to "text/css; charset=utf-8"
            path.endsWith("/editor.js") || path.endsWith("/script.js") ->
                "editor.js" to "application/javascript; charset=utf-8"
            path == "/favicon.ico" -> {
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
                return
            }
            else -> {
                val response = "Not Found".toByteArray()
                exchange.sendResponseHeaders(404, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                return
            }
        }

        val bytes = cachedResources[fileName]
        if (bytes == null) {
            val response = "Resource not found: $fileName".toByteArray()
            exchange.sendResponseHeaders(404, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            return
        }

        exchange.responseHeaders["Content-Type"] = listOf(contentType)
        exchange.responseHeaders["Cache-Control"] = listOf("no-cache")
        exchange.responseHeaders["X-Content-Type-Options"] = listOf("nosniff")
        exchange.responseHeaders["X-Frame-Options"] = listOf("SAMEORIGIN")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
