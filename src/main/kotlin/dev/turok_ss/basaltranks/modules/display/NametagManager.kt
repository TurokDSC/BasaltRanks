package dev.turok_ss.basaltranks.modules.display

import dev.turok_ss.basaltranks.BasaltRanks
import dev.turok_ss.basaltranks.modules.badge.Badge
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

/**
 * Manages Scoreboard teams for displaying badge prefixes on player nametags.
 *
 * Creates a team per badge (named "br_{badgeId}") with the badge's Unicode
 * character as the team prefix. Players are assigned to their badge's team
 * to display the badge above their head.
 *
 * The scoreboard is accessed lazily via null-safe access to avoid
 * NullPointerException if called before the server is fully initialized.
 */
class NametagManager(private val plugin: BasaltRanks, private val animationScheduler: AnimationScheduler) {

    companion object {
        private const val TEAM_PREFIX = "br_"
        private const val MAX_TEAM_NAME_LENGTH = 16
    }

    private fun getOrCreateTeamOn(scoreboard: Scoreboard, prefixBadge: Badge?, suffixBadge: Badge?): Team {
        val teamName = buildTeamName(prefixBadge?.id, suffixBadge?.id)
        val existing = scoreboard.getTeam(teamName)
        if (existing != null) {
            applyBadgesToTeam(existing, prefixBadge, suffixBadge)
            return existing
        }
        val team = scoreboard.registerNewTeam(teamName)
        applyBadgesToTeam(team, prefixBadge, suffixBadge)
        return team
    }

    private fun applyBadgesToTeam(team: Team, prefixBadge: Badge?, suffixBadge: Badge?) {
        if (prefixBadge != null) {
            val char = prefixBadge.getCurrentChar(animationScheduler.getCurrentFrame(prefixBadge))
            team.prefix(Component.text("$char "))
        } else {
            team.prefix(Component.empty())
        }
        if (suffixBadge != null) {
            val char = suffixBadge.getCurrentChar(animationScheduler.getCurrentFrame(suffixBadge))
            team.suffix(Component.text(" $char"))
        } else {
            team.suffix(Component.empty())
        }
    }

    private fun buildTeamName(prefixBadgeId: String?, suffixBadgeId: String?): String {
        val key = "${prefixBadgeId ?: ""}_${suffixBadgeId ?: ""}"
        val hash = key.hashCode().toUInt().toString(36)
        return "$TEAM_PREFIX$hash".take(MAX_TEAM_NAME_LENGTH)
    }

    private fun collectScoreboards(): Set<Scoreboard> {
        val scoreboards = mutableSetOf<Scoreboard>()
        Bukkit.getScoreboardManager()?.mainScoreboard?.let { scoreboards.add(it) }
        Bukkit.getOnlinePlayers().forEach { scoreboards.add(it.scoreboard) }
        return scoreboards
    }

    fun updatePlayer(player: Player) {
        val badgeModule = plugin.getBadgeModule() ?: return
        val prefixBadge = badgeModule.getPlayerBadge(player.uniqueId)
        val suffixBadge = badgeModule.getPlayerSuffixBadge(player.uniqueId)
        val targetTeamName = if (prefixBadge != null || suffixBadge != null)
            buildTeamName(prefixBadge?.id, suffixBadge?.id) else null

        val scoreboards = collectScoreboards()

        for (sb in scoreboards) {
            val oldTeam = sb.getEntryTeam(player.name)
            if (oldTeam != null && oldTeam.name.startsWith(TEAM_PREFIX) && oldTeam.name != targetTeamName) {
                oldTeam.removeEntry(player.name)
            }
            if (targetTeamName != null) {
                val team = sb.getTeam(targetTeamName) ?: getOrCreateTeamOn(sb, prefixBadge, suffixBadge)
                applyBadgesToTeam(team, prefixBadge, suffixBadge)
                if (!team.hasEntry(player.name)) {
                    team.addEntry(player.name)
                }
            }
        }
    }

    fun removePlayer(player: Player) {
        for (online in Bukkit.getOnlinePlayers()) {
            val sb = online.scoreboard
            val entryTeam = sb.getEntryTeam(player.name)
            if (entryTeam != null && entryTeam.name.startsWith(TEAM_PREFIX)) {
                entryTeam.removeEntry(player.name)
            }
        }
        val mainSb = Bukkit.getScoreboardManager()?.mainScoreboard
        if (mainSb != null) {
            val entryTeam = mainSb.getEntryTeam(player.name)
            if (entryTeam != null && entryTeam.name.startsWith(TEAM_PREFIX)) {
                entryTeam.removeEntry(player.name)
            }
        }
    }

    fun cleanup() {
        val scoreboards = collectScoreboards()
        var count = 0
        for (sb in scoreboards) {
            val teams = sb.teams.filter { it.name.startsWith(TEAM_PREFIX) }.toList()
            for (team in teams) {
                try { team.unregister(); count++ } catch (e: Exception) {
                    plugin.logger.fine("Failed to unregister team ${team.name}: ${e.message}")
                }
            }
        }
        if (count > 0) plugin.logger.info("Cleaned up $count nametag teams")
    }

    fun refreshTeams() {
        val badgeModule = plugin.getBadgeModule() ?: return
        for (sb in collectScoreboards()) {
            for (online in Bukkit.getOnlinePlayers()) {
                val prefixBadge = badgeModule.getPlayerBadge(online.uniqueId)
                val suffixBadge = badgeModule.getPlayerSuffixBadge(online.uniqueId)
                if (prefixBadge != null || suffixBadge != null) {
                    getOrCreateTeamOn(sb, prefixBadge, suffixBadge)
                }
            }
        }
    }

    fun onPlayerJoin(player: Player) {
        val badgeModule = plugin.getBadgeModule() ?: return
        val sb = player.scoreboard

        for (online in Bukkit.getOnlinePlayers()) {
            val onlinePrefixBadge = badgeModule.getPlayerBadge(online.uniqueId)
            val onlineSuffixBadge = badgeModule.getPlayerSuffixBadge(online.uniqueId)
            if (onlinePrefixBadge == null && onlineSuffixBadge == null) continue
            val teamName = buildTeamName(onlinePrefixBadge?.id, onlineSuffixBadge?.id)
            val team = sb.getTeam(teamName) ?: getOrCreateTeamOn(sb, onlinePrefixBadge, onlineSuffixBadge)
            if (!team.hasEntry(online.name)) {
                team.addEntry(online.name)
            }
        }

        val myPrefixBadge = badgeModule.getPlayerBadge(player.uniqueId)
        val mySuffixBadge = badgeModule.getPlayerSuffixBadge(player.uniqueId)
        if (myPrefixBadge == null && mySuffixBadge == null) return
        val myTeamName = buildTeamName(myPrefixBadge?.id, mySuffixBadge?.id)
        for (other in Bukkit.getOnlinePlayers()) {
            val otherSb = other.scoreboard
            val team = otherSb.getTeam(myTeamName) ?: getOrCreateTeamOn(otherSb, myPrefixBadge, mySuffixBadge)
            if (!team.hasEntry(player.name)) {
                team.addEntry(player.name)
            }
        }
    }
}
