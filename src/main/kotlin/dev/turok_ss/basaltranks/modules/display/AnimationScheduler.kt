package dev.turok_ss.basaltranks.modules.display

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import dev.turok_ss.basaltranks.utils.FoliaCompat
import java.util.concurrent.ConcurrentHashMap

class AnimationScheduler(private val plugin: BasaltRanks) {

    private val frameCounters = ConcurrentHashMap<String, Int>()
    private var task: FoliaCompat.CancellableTask? = null
    private var gameTickCounter = 0L
    private var baseTickRate = 5L
    @Volatile private var animatedCount = 0

    fun start(baseTickRate: Long = 5L) {
        this.baseTickRate = baseTickRate
        refreshAnimatedCount()
        task = FoliaCompat.runTaskTimer(plugin, Runnable { tick() }, baseTickRate, baseTickRate)
    }

    fun stop() {
        task?.cancel()
        task = null
        frameCounters.clear()
        gameTickCounter = 0L
        animatedCount = 0
    }

    private fun tick() {
        if (animatedCount == 0) return
        gameTickCounter += baseTickRate
        val badgeModule = plugin.getBadgeModule() ?: return
        for (badge in badgeModule.getAllBadges()) {
            if (!badge.animated || badge.frameChars.isEmpty()) continue
            if (gameTickCounter % badge.animationSpeed.coerceAtLeast(1) == 0L) {
                val current = frameCounters.getOrDefault(badge.id, 0)
                frameCounters[badge.id] = (current + 1) % badge.frameChars.size
            }
        }
    }

    fun getCurrentFrame(badge: Badge): Int {
        if (!badge.animated || badge.frameChars.isEmpty()) return 0
        return frameCounters.getOrDefault(badge.id, 0)
    }

    fun resetBadge(badgeId: String) {
        frameCounters.remove(badgeId)
        refreshAnimatedCount()
    }

    fun refreshAnimatedCount() {
        val badgeModule = plugin.getBadgeModule()
        animatedCount = badgeModule?.getAllBadges()?.count { it.animated && it.frameChars.isNotEmpty() } ?: 0
    }

    fun reset() {
        frameCounters.clear()
        gameTickCounter = 0L
        refreshAnimatedCount()
    }
}
