package net.sourcebot.module.moderation.data

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.sourcebot.api.DurationUtils
import net.sourcebot.api.asMessage
import net.sourcebot.api.formatLong
import net.sourcebot.api.response.StandardWarningResponse
import java.time.Duration

class BlacklistIncident(
    override val id: Long,
    private val blacklistRole: Role,
    private val sender: Member,
    val member: Member,
    val duration: Duration,
    override val reason: String
) : ExpiringPunishment(duration, Level.ONE) {
    override val source = sender.id
    override val target = member.id
    override val type = Incident.Type.BLACKLIST
    private val blacklist = StandardWarningResponse(
        "Blacklist - Case #$id",
        """
            **Blacklisted By:** ${sender.formatLong()} ($source)
            **Blacklisted User:** ${member.formatLong()} ($target)
            **Duration:** ${DurationUtils.formatDuration(duration)}
            **Reason:** $reason
        """.trimIndent()
    )

    override fun execute() {
        //Ignore DM failures
        kotlin.runCatching {
            val dm = member.user.openPrivateChannel().complete()
            dm.sendMessage(blacklist.asMessage(member)).complete()
        }
        sender.guild.addRoleToMember(member, blacklistRole).complete()
    }

    override fun sendLog(logChannel: TextChannel): Message =
        logChannel.sendMessage(blacklist.asMessage(sender)).complete()
}