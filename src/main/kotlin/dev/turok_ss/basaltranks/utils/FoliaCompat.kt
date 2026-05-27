package dev.turok_ss.basaltranks.utils

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

object FoliaCompat {

    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun runTask(plugin: JavaPlugin, task: Runnable) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun runTaskLater(plugin: JavaPlugin, task: Runnable, delayTicks: Long) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks.coerceAtLeast(1))
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
    }

    fun runTaskTimer(plugin: JavaPlugin, task: Runnable, delayTicks: Long, periodTicks: Long): CancellableTask {
        return if (isFolia) {
            val scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, { task.run() }, delayTicks.coerceAtLeast(1), periodTicks
            )
            CancellableTask { scheduled.cancel() }
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)
            CancellableTask { bukkitTask.cancel() }
        }
    }

    fun runAsync(plugin: JavaPlugin, task: Runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    fun runAsyncLater(plugin: JavaPlugin, task: Runnable, delayTicks: Long) {
        if (isFolia) {
            val delayMs = (delayTicks * 50).coerceAtLeast(50)
            Bukkit.getAsyncScheduler().runDelayed(
                plugin, { task.run() }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS
            )
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
        }
    }

    fun interface CancellableTask {
        fun cancel()
    }
}
