package net.sourcebot.module.moderation.data

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.sourcebot.api.asMessage
import net.sourcebot.api.formatted
import net.sourcebot.api.response.StandardErrorResponse
import org.bson.Document

class BanIncident @JvmOverloads constructor(
    override val id: Long,
    private val sender: Member,
    val banned: Member,
    private val delDays: Int,
    override val reason: String,
    private val points: Double = 100.0
) : OneshotIncident() {
    override val source = sender.id
    override val target = banned.id
    override val type = Incident.Type.BAN
    private val ban = StandardErrorResponse(
        "Ban - Case #$id",
        """
            **Banned By:** ${sender.formatted()} ($source)
            **Banned User:** ${banned.formatted()} ($target)
            **Reason:** $reason
        """.trimIndent()
    )

    override fun asDocument() = super.asDocument().also {
        it["points"] = Document().also {
            it["value"] = points
            it["decay"] = (86400L * points).toLong()
        }
    }

    override fun execute() {
        //Ignore DM failure
        kotlin.runCatching {
            val dm = banned.user.openPrivateChannel().complete()
            dm.sendMessage(ban.asMessage(banned)).complete()
        }
        banned.ban(delDays, reason).complete()
    }

    override fun sendLog(logChannel: TextChannel) = logChannel.sendMessage(
        ban.asMessage(sender)
    ).queue()
}