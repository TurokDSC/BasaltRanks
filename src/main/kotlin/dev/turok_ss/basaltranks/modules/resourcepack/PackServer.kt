package dev.turok_ss.basaltranks.modules.resourcepack

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.logging.Logger

/**
 * Embedded HTTP server that serves the resource pack ZIP file
 * and supports additional HTTP contexts (routes) for the web editor.
 *
 * Uses com.sun.net.httpserver.HttpServer which is part of the JDK.
 */
class PackServer(private val logger: Logger) {

    private var server: HttpServer? = null

    /** In-memory buffer of the pack file for fast serving */
    @Volatile
    private var packBytes: ByteArray? = null

    /** Whether the server is currently running */
    val isRunning: Boolean get() = server != null

    private val additionalContexts = java.util.concurrent.ConcurrentHashMap<String, (HttpExchange) -> Unit>()

    /**
     * Starts the HTTP server on the given port without a pack file.
     * Useful when the web editor needs to run before badges are created.
     *
     * @param port The port to listen on
     * @return true if the server started successfully
     */
    fun start(port: Int): Boolean {
        if (server != null) {
            return true // Already running
        }

        return try {
            val httpServer = HttpServer.create(InetSocketAddress(port), 0)
            httpServer.createContext("/pack") { exchange -> handlePackRequest(exchange) }
            httpServer.createContext("/") { exchange ->
                if (exchange.requestURI.path == "/" || exchange.requestURI.path.isBlank()) {
                    exchange.responseHeaders["Location"] = listOf("/web")
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                } else {
                    val body = "Not Found".toByteArray()
                    exchange.sendResponseHeaders(404, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }

            // Register any additional contexts that were added before server start
            for ((path, handler) in additionalContexts) {
                httpServer.createContext(path) { exchange ->
                    try {
                        handler(exchange)
                    } catch (e: Exception) {
                        logger.warning("Error handling request ${exchange.requestURI}: ${e.message}")
                        try {
                            val errBody = """{"error":"Internal server error"}""".toByteArray()
                            exchange.sendResponseHeaders(500, errBody.size.toLong())
                            exchange.responseBody.use { it.write(errBody) }
                        } catch (_: Exception) { }
                    }
                }
            }

            httpServer.executor = null // Use default executor
            httpServer.start()

            server = httpServer
            logger.info("Pack server started on port $port")
            true
        } catch (e: Exception) {
            logger.severe("Failed to start pack server on port $port: ${e.message}")
            false
        }
    }

    /**
     * Starts the HTTP server on the given port, serving the pack file.
     *
     * @param port The port to listen on
     * @param packFile The resource pack ZIP file to serve
     * @return true if the server started successfully
     */
    fun start(port: Int, packFile: File): Boolean {
        if (!packFile.exists()) {
            logger.warning("Cannot start pack server: ${packFile.name} does not exist")
            return false
        }

        // Read file into memory buffer
        packBytes = packFile.readBytes()

        // Start the server if not already running
        val started = start(port)
        if (started) {
            logger.info("Pack server serving ${packBytes?.size ?: 0} bytes at /pack")
        }
        return started
    }

    /**
     * Stops the HTTP server if running.
     */
    fun stop() {
        server?.let { srv ->
            try {
                srv.stop(0)
                logger.info("Pack server stopped")
            } catch (e: Exception) {
                logger.warning("Error stopping pack server: ${e.message}")
            }
        }
        server = null
        packBytes = null
    }

    /**
     * Reloads the pack file data, optionally restarting on a new port.
     *
     * @param packFile The new pack file to serve
     * @param port Optional new port (restarts server if different from current)
     */
    fun reload(packFile: File, port: Int? = null) {
        if (!packFile.exists()) {
            logger.warning("Cannot reload pack: ${packFile.name} does not exist")
            return
        }

        if (port != null && server != null) {
            val currentAddress = server?.address
            if (currentAddress != null && currentAddress.port != port) {
                // Port changed, need full restart
                stop()
                start(port, packFile)
                return
            }
        }

        // Just update the in-memory buffer
        packBytes = packFile.readBytes()
        logger.info("Pack server reloaded (${packBytes?.size ?: 0} bytes)")
    }

    /**
     * Registers an additional HTTP context on this server.
     * If the server is already running, the context is added immediately.
     * If not, it will be registered when the server starts.
     *
     * @param path The URL path prefix (e.g., "/api/" or "/")
     * @param handler The handler function for requests matching this path
     */
    fun registerContext(path: String, handler: (HttpExchange) -> Unit) {
        additionalContexts[path] = handler

        // If server is already running, add the context now
        server?.createContext(path) { exchange ->
            try {
                handler(exchange)
            } catch (e: Exception) {
                logger.warning("Error handling request ${exchange.requestURI}: ${e.message}")
                try {
                    val errBody = """{"error":"Internal server error"}""".toByteArray()
                    exchange.sendResponseHeaders(500, errBody.size.toLong())
                    exchange.responseBody.use { it.write(errBody) }
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * Removes a previously registered context.
     */
    fun removeContext(path: String) {
        additionalContexts.remove(path)
        try {
            server?.removeContext(path)
        } catch (_: Exception) {
            // Context may not exist on the server
        }
    }

    /**
     * Handles an incoming HTTP request for the pack file.
     */
    private fun handlePackRequest(exchange: HttpExchange) {
        try {
            val bytes = packBytes
            if (bytes == null) {
                val response = "Resource pack not available".toByteArray()
                exchange.sendResponseHeaders(503, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                return
            }

            // Only allow GET requests
            if (exchange.requestMethod != "GET") {
                val response = "Method not allowed".toByteArray()
                exchange.sendResponseHeaders(405, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                return
            }

            exchange.responseHeaders["Content-Type"] = listOf("application/zip")
            exchange.responseHeaders["Content-Disposition"] = listOf("attachment; filename=\"basaltranks.zip\"")
            exchange.responseHeaders["Cache-Control"] = listOf("no-cache")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            logger.warning("Error serving pack: ${e.message}")
            try {
                exchange.sendResponseHeaders(500, -1)
            } catch (_: Exception) {
                // Client may have disconnected
            }
        }
    }
}
