package dev.turok_ss.basaltranks.utils

import dev.turok_ss.basaltranks.BasaltRanks
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.net.HttpURLConnection
import java.net.URI

class UpdateChecker(private val plugin: BasaltRanks) : Listener {

    companion object {
        private const val VERSION_URL = "https://hangar.papermc.io/api/v1/projects/BasaltRanks/latestrelease"
        private const val DOWNLOAD_URL = "https://hangar.papermc.io/Turok_SS/BasaltRanks"
    }

    private var latestVersion: String? = null

    fun check() {
        if (!plugin.config.getBoolean("update-check", true)) return

        FoliaCompat.runAsyncLater(plugin, Runnable {
            try {
                val conn = URI(VERSION_URL).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "BasaltRanks/${plugin.pluginMeta.version}")

                if (conn.responseCode == 200) {
                    val latest = conn.inputStream.bufferedReader().readText().trim()
                    if (latest != plugin.pluginMeta.version && latest.isNotBlank()) {
                        latestVersion = latest
                        plugin.logger.info("New version available: $latest (current: ${plugin.pluginMeta.version})")
                        plugin.logger.info("Download: $DOWNLOAD_URL")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                plugin.logger.fine("Update check failed: ${e.message}")
            }
        }, 100L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val latest = latestVersion ?: return
        if (!event.player.hasPermission("basaltranks.admin")) return

        FoliaCompat.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                event.player.sendMessage(Messages.info(
                    "Update available: <white>$latest</white> <gray>(current: ${plugin.pluginMeta.version})"
                ))
            }
        }, 40L)
    }
}
